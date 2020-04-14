/*
 * Copyright 2020 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.tools.maven.resolution

import com.squareup.tools.maven.resolution.FetchStatus.ERROR
import com.squareup.tools.maven.resolution.FetchStatus.INVALID_HASH
import com.squareup.tools.maven.resolution.FetchStatus.RepositoryFetchStatus.FETCH_ERROR
import com.squareup.tools.maven.resolution.FetchStatus.RepositoryFetchStatus.NOT_FOUND
import com.squareup.tools.maven.resolution.FetchStatus.RepositoryFetchStatus.SUCCESSFUL
import java.io.IOException
import java.lang.IllegalArgumentException
import java.nio.file.FileSystems
import java.nio.file.Path
import org.apache.maven.model.Repository
import org.apache.maven.model.building.DefaultModelBuilderFactory
import org.apache.maven.model.building.DefaultModelBuildingRequest
import org.apache.maven.model.building.ModelBuildingRequest
import org.apache.maven.model.building.ModelBuildingResult
import org.apache.maven.model.resolution.ModelResolver

/**
 * The main entry point to the library, which wraps (and mostly hides) the maven resolution
 * infrastructure.
 *
 * [ArtifactResolver] holds two key workhorse functions: [ArtifactResolver.resolveArtifact] and
 * [ArtifactResolver.downloadArtifact], plus a convenience function [ArtifactResolver.download].
 *
 * Intended usage is simple, assuming no need to specify a lot of configuration:
 *
 * ```
 * val resolver = ArtifactResolver() // creates a resolver with repo list defaulting to maven central
 * val artifact = resolver.artifactFor("com.google.guava:guava:27.1-jre") // returns Artifact
 * val resolvedArtifact = resolver.resolveArtifact(artifact) // returns ResolvedArtifact
 * val result = resolver.download(resolvedArtifact) // returns FetchStatus
 * if (result is SUCCESSFUL) { /* win! */ }
 * ```
 *
 * A simpler option (if you don't need programmatic access to the resolved model information) is
 * to use [ArtifactResolver.download] which can be invoked like so:
 *
 * ```
 * val resolver = ArtifactResolver() // creates a resolver with repo list defaulting to maven central
 * val (pom, artifact) = resolver.download("com.google.guava:guava:27.1-jre")
 * ```
 *
 * Configuring repositories is easy:
 * ```
 * val resolver = ArtifactResolver(repositories = listOf(rep1, rep2))
 * ```
 *
 * Reasonably popular repositories have been pre-defined in [Repositories] (e.g.
 * [Repositories.MAVEN_CENTRAL]). These repositories are all instances of
 * [org.apache.maven.model.Repository] (available via org.apache.maven:maven-model) and can be
 * constructed pretty simply via:
 *
 * ```
 * Repository().apply {
 *   id = "some-identifier"
 *   releases = RepositoryPolicy().apply { enabled = "true" }
 *   url = "https://some.server/path/to/repo" // At present, only http/https are supported.
 * }
 * ```
 *
 * Artifacts are cached in a file location, that defaults to ${HOME}/.m2/repository (maven standard
 * local repository location). This can be configured for each [ArtifactResolver] instance via the
 * [cacheDir] property.
 */
class ArtifactResolver(
  suppressAddRepositoryWarnings: Boolean = false,
  private val cacheDir: Path =
      FileSystems.getDefault().getPath("${System.getProperty("user.home")}/.m2/repository"),
  private val fetcher: ArtifactFetcher = HttpArtifactFetcher(cacheDir = cacheDir),
  private val modelBuilderFactory: DefaultModelBuilderFactory = DefaultModelBuilderFactory(),
  private val repositories: List<Repository> = Repositories.DEFAULT,
  private val resolver: ModelResolver = SimpleHttpResolver(
      cacheDir = cacheDir,
      fetcher = fetcher,
      repositories = repositories,
      suppressAddRepositoryWarnings = suppressAddRepositoryWarnings
  )
) {

  fun artifactFor(spec: String): Artifact {
    with(spec.split(":")) {
      when (this.size) {
        3 -> {
          val (groupId, artifactId, versionId) = this
          return Artifact(groupId, artifactId, versionId, cacheDir)
        }
        4 -> {
          val (groupId, artifactId, _, versionId) = this
          // we throw away packaging, as we will decide from the pom resolution, what the
          // packaging type is.
          return Artifact(groupId, artifactId, versionId, cacheDir)
        }
        else -> {
          throw IllegalArgumentException("Invalid artifact format: $spec")
        }
      }
    }
  }

  fun resolveArtifact(artifact: Artifact): ResolvedArtifact? {
    if (artifact is ResolvedArtifact) return artifact

    info { "Fetching ${artifact.coordinate}" }
    val fetched = fetcher.fetchPom(artifact.pom, repositories = repositories)
    when (fetched) {
      is SUCCESSFUL -> { /* carry on */ }
      is INVALID_HASH -> {
        // WARN spam, but continue
        warn { "Hashes did not match for artifact ${artifact.coordinate}" }
      }
      is FETCH_ERROR -> {
        val repositoryIds = repositories.map { repo -> repo.id }
        error { "Could not resolve artifact pom for ${artifact.coordinate}: $repositoryIds" }
        return null
      }
      is ERROR -> {
        error { "Errors from repositories while resolving ${artifact.coordinate}." }
        fetched.errors.entries.forEach { (id, error) -> error { "    $id: $error" } }
        return null
      }
      is NOT_FOUND -> {
        val repositoryIds = repositories.map { repo -> repo.id }
        error { "No artifact found ${artifact.coordinate} in repositories $repositoryIds" }
        return null
      }
    }
    val modelBuilder = modelBuilderFactory.newInstance()
    val req = DefaultModelBuildingRequest()
        .apply {
          modelResolver = resolver
          pomFile = artifact.pom.localFile.toFile()
          setProcessPlugins(false) // Auto-property inference is broken for this property.
          validationLevel = ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL
          systemProperties = System.getProperties()
        }
    val builder: ModelBuildingResult = modelBuilder.build(req)
    return ResolvedArtifact(builder.effectiveModel, cacheDir, fetched is SUCCESSFUL.FOUND_IN_CACHE)
  }

  /**
   * Downloads the main artifact file, and returns a fetch status to indicate its success.
   *
   * The resulting downloaded (or cached) file is available via [ResolvedArtifact.main.path]
   * (typically pointing at the file within the user's local maven repository, or another local
   * repository if one is configured.
   */
  fun downloadArtifact(artifact: ResolvedArtifact): FetchStatus {
    info { "Fetching ${artifact.coordinate}" }
    return fetcher.fetchArtifact(artifact.main, repositories = repositories)
  }

  /**
   * Resolves and downloads the given artifact's pom file and main artifact file.
   *
   * The result is a [Pair<Path, Path] with the first item being the local path to the pom, and
   * the second being the local path to the artifact file. This version throws an IOException if
   * the download fails (either for the pom or the artifact), or if the downloaded files fail hash
   * validation.
   */
  @Throws(IOException::class)
  fun download(coordinate: String): Pair<Path, Path> {
    val artifact = artifactFor(coordinate)
    val resolvedArtifact = resolveArtifact(artifact)
        ?: throw IOException("Could not resolve pom file for $coordinate")
    val status = downloadArtifact(resolvedArtifact)
    if (status is SUCCESSFUL)
      return resolvedArtifact.pom.localFile to resolvedArtifact.main.localFile
    else throw IOException("Could not download artifact for $coordinate: $status")
  }
}
