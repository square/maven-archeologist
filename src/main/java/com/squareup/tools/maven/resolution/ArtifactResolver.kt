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
import com.squareup.tools.maven.resolution.FetchStatus.RepositoryFetchStatus.SUCCESSFUL.FOUND_IN_CACHE
import com.squareup.tools.maven.resolution.FetchStatus.RepositoryFetchStatus.SUCCESSFUL.SUCCESSFULLY_FETCHED
import com.squareup.tools.maven.resolution.gradle.GradleModuleParser
import com.squareup.tools.maven.resolution.gradle.Module
import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.DeprecationLevel.WARNING
import org.apache.maven.model.Model
import org.apache.maven.model.Repository
import org.apache.maven.model.building.DefaultModelBuilderFactory
import org.apache.maven.model.building.DefaultModelBuildingRequest
import org.apache.maven.model.building.ModelBuildingException
import org.apache.maven.model.building.ModelBuildingRequest
import org.apache.maven.model.building.ModelBuildingResult
import org.apache.maven.model.resolution.ModelResolver
import org.apache.maven.model.validation.DefaultModelValidator

/**
 * The main entry point to the library, which wraps (and mostly hides) the maven resolution
 * infrastructure.
 *
 * [ArtifactResolver] holds two key workhorse functions: [ArtifactResolver.resolve] and
 * [ArtifactResolver.downloadArtifact] (and some related sub-artifact downloading functions),
 * plus a convenience function [ArtifactResolver.download] which just downloads the common
 * files and returns a structure with paths to their location..
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
 * This is what you would use, also, if you needed access to the maven model or gradle module
 * metadata, which are located on [ResolvedArtifact.model] and [ResolvedArtifact.gradleModule].
 *
 * A simpler option (if you don't need programmatic access to the resolved model information) is
 * to use [ArtifactResolver.download] which can be invoked like so:
 *
 * ```
 * val resolver = ArtifactResolver() // creates a resolver with repo list defaulting to maven central
 * val (pom, artifact, sources, module) = resolver.download("com.google.guava:guava:27.1-jre")
 * // module will be null, as there is no gradle .module file for this artifact
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
class ArtifactResolver @JvmOverloads constructor(
  suppressAddRepositoryWarnings: Boolean = false,
  private val strictHashValidation: Boolean = false,
  /** Should the resolver fetch the gradle module when fetching the pom file? */
  private val resolveGradleModule: Boolean = true,
  private val cacheDir: Path =
      FileSystems.getDefault().getPath("${System.getProperty("user.home")}/.m2/repository"),
  private val fetcher: ArtifactFetcher = HttpArtifactFetcher(cacheDir = cacheDir),
  private val modelBuilderFactory: DefaultModelBuilderFactory = DefaultModelBuilderFactory(),
  private val repositories: List<Repository> = Repositories.DEFAULT,
  private val gradleModuleParser: GradleModuleParser = GradleModuleParser(),
  private val resolver: ModelResolver = SimpleHttpResolver(
      cacheDir = cacheDir,
      fetcher = fetcher,
      repositories = repositories,
      suppressAddRepositoryWarnings = suppressAddRepositoryWarnings
  ),
  private val modelInterceptor: (Model) -> Unit = { }
) {

  /** Returns an [Artifact] parsed from a given maven style specification (group:id:version) */
  fun artifactFor(spec: String): Artifact {
    return with(spec.split(":")) {
      when (this.size) {
        3 -> {
          val (groupId, artifactId, versionId) = this
          Artifact(groupId, artifactId, versionId, cacheDir)
        }
        4 -> {
          val (groupId, artifactId, _, versionId) = this
          // we throw away packaging, as we will decide from the pom resolution, what the
          // packaging type is.
          Artifact(groupId, artifactId, versionId, cacheDir)
        }
        else -> throw IllegalArgumentException("Invalid artifact format: $spec")
      }
    }
  }

  /**
   * Resolves the supplied artifact, downloading any necessary POM files needed to resolve
   * the model, returning a [ResolutionResult] that contains the fetch status (success from cache
   * or download or relevant errors) and a [ResolvedArtifact] if the model was successfully
   * resolved.
   *
   * If the returned result includes a non-null resolved artifact, the fetch status will be
   * either [FOUND_IN_CACHE] or [SUCCESSFUL.SUCCESSFULLY_FETCHED] or possibly an [INVALID_HASH]
   * (if strict hash validation is disabled, as is the default)
   */
  fun resolve(artifact: Artifact): ResolutionResult {
    if (artifact is ResolvedArtifact) return ResolutionResult(FOUND_IN_CACHE, artifact)

    info { "Fetching ${artifact.coordinate}" }
    val pomResult = fetcher.fetchFile(artifact.pom, repositories = repositories)

    when (pomResult) {
      is ERROR -> return ResolutionResult(pomResult, null)
      is FETCH_ERROR -> return ResolutionResult(pomResult, null)
      is NOT_FOUND -> return ResolutionResult(pomResult, null)
      is INVALID_HASH -> if (strictHashValidation) return ResolutionResult(pomResult, null)
      is SUCCESSFUL -> { /* continue */ }
    }
    // Only a limited number of cases to handle here. Split in two from the above when, because this
    // logic relates to gradle module fetching, not the fetch status of the pom.
    val gradleResult = when (pomResult) {
      is SUCCESSFULLY_FETCHED, INVALID_HASH -> {
        // only try to fetch the module if we just fetched the pom, thereby caching the non-existance
        // of modules which were not fetched when the pom was fetched. This replcates gradle's
        // semantic, which doesn't try to get the module file if it finds a cached .pom file but no
        // locally cached .module file.
        //
        // Also, fetch as a precaution on (lenient) INVALID_HASH of the pom, since we can't tell
        // the difference between fetched vs. found in the invalid hash case.
        // TODO(cgruber) move hash validity state into a property of the FetchStatus success states
        fetcher.fetchFile(artifact.gradleModule, repositories = repositories).let { status ->
          when (status) {
            is ERROR -> return ResolutionResult(status, null)
            is FETCH_ERROR -> return ResolutionResult(status, null)
            is INVALID_HASH -> {
              if (!strictHashValidation) status
              else return ResolutionResult(status, null)
            }
            else -> status // successful or not found
          }
        }
      }
      is FOUND_IN_CACHE -> if (artifact.gradleModule.localFile.exists) FOUND_IN_CACHE else NOT_FOUND
      else -> NOT_FOUND
    }

    val modelResult = try {
      resolveMavenModel(artifact.pom)
    } catch (e: ModelBuildingException) {
      return ResolutionResult(
        FETCH_ERROR(message = "Error processing maven pom.", error = e),
        null
      )
    }

    val gradleModel = try {
      if (gradleResult is SUCCESSFUL || (!strictHashValidation && gradleResult is INVALID_HASH)) {
        resolveGradleModel(artifact.gradleModule)
      } else null
    } catch (e: IOException) {
      return ResolutionResult(
        FETCH_ERROR(message = "Error parsing gradle module.", error = e),
        null
      )
    }

    return ResolutionResult(
      status = pomResult,
      artifact = ResolvedArtifact(
        modelResult.effectiveModel,
        gradleModel,
        cacheDir,
        pomResult is FOUND_IN_CACHE
      )
    )
  }

  private fun resolveGradleModel(moduleFile: GradleModuleFile): Module? {
    return if (moduleFile.localFile.exists) {
      gradleModuleParser.parse(moduleFile.localFile)
    } else null
  }

  private fun resolveMavenModel(pom: PomFile): ModelBuildingResult {
    val validator = DefaultModelValidator()
    val modelBuilder = modelBuilderFactory.newInstance()
      .setModelValidator(NoopEffectiveModelValidator(validator)) // we will validate it later.

    val req = DefaultModelBuildingRequest()
      .apply {
        modelResolver = resolver
        pomFile = pom.localFile.toFile()
        validationLevel = ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL
        systemProperties = System.getProperties()

        // Auto-property inference is broken for boolean java functions in the pattern they use.
        setProcessPlugins(false)
      }
    val result = modelBuilder.build(req)
    modelInterceptor.invoke(result.effectiveModel) // models are mutable
    return Validator.validateResult(result, validator, req)
  }

  /**
   * Downloads the main artifact file, and returns a fetch status to indicate its success.
   *
   * The resulting downloaded (or cached) file is available via [ResolvedArtifact.main.localFile]
   * (typically pointing at the file within the user's local maven repository, or another local
   * repository if one is configured.)
   */
  fun downloadArtifact(artifact: ResolvedArtifact): FetchStatus {
    info { "Fetching ${artifact.coordinate}" }
    return fetcher.fetchFile(artifact.main, repositories = repositories)
  }

  /**
   * Downloads the artifact sources file, and returns a fetch status to indicate its success.
   *
   * The resulting downloaded (or cached) file is available via [ResolvedArtifact.sources.localFile]
   * (typically pointing at the file within the user's local maven repository, or another local
   * repository if one is configured.)
   */
  fun downloadSources(artifact: ResolvedArtifact): FetchStatus {
    info { "Fetching ${artifact.coordinate} sources" }
    return fetcher.fetchFile(artifact.sources, repositories = repositories)
  }

  /**
   * Downloads the artifact sources file, and returns a fetch status to indicate its success.
   *
   * The resulting downloaded (or cached) file is available via [ClassifiedFile.localFile]
   * (typically pointing at the file within the user's local maven repository, or another local
   * repository if one is configured.)
   */
  fun downloadSubArtifact(subArtifact: ClassifiedFile): FetchStatus {
    return fetcher.fetchFile(subArtifact, repositories = repositories)
  }

  /**
   * Resolves and downloads the given artifact's pom file and main artifact file.
   *
   * > NOTE: This version is deprecated and will ultimately be deleted. Please use
   * > [download(String,Boolean)].
   */
  @Deprecated(
    "New method has a more robust download result type",
    replaceWith = ReplaceWith("download(coordinate, downloadSources = false)"),
    level = WARNING
  )
  @Throws(IOException::class)
  fun download(coordinate: String): Pair<Path, Path> =
    download(coordinate, false).let { it.pom to it.main }

  /**
   * Resolves and downloads the given artifact's pom file and main artifact file.
   *
   * The result is a [Triple<Path, Path, Path?>] with the first item being the local path to the
   * pom, the second being the local path to the artifact file, and the third being the path to
   * the source jar (if it exists, otherwise null)). This version throws an IOException if
   * the download fails (either for the pom or the artifact), or if the downloaded files fail hash
   * validation.  This will NOT fail if the -sources.jar is not successfully downloaded, but will
   * rather return SUCCESSFUL with a null entry
   */
  @Throws(IOException::class)
  fun download(coordinate: String, downloadSources: Boolean = false): SimpleDownloadResult {
    val artifact = artifactFor(coordinate)
    val resolution = resolve(artifact)
    val resolvedArtifact = resolution.artifact
        ?: throw IOException("Could not resolve pom file for $coordinate: ${resolution.status}")
    val status = downloadArtifact(resolvedArtifact)
    val sourcesStatus = if (downloadSources) downloadSources(resolvedArtifact) else null
    if (status is SUCCESSFUL)
      return SimpleDownloadResult(
        pom = resolvedArtifact.pom.localFile,
        main = resolvedArtifact.main.localFile,
        sources = if (sourcesStatus != null && sourcesStatus is SUCCESSFUL)
          resolvedArtifact.sources.localFile else null,
        gradleModule = with(resolvedArtifact.gradleModule.localFile) {
          this.takeIf { it.exists }
        }
      )
    else throw IOException("Could not download artifact for $coordinate: $status")
  }
}

/**
 * Represents the result of a resolution request, containing a fetch status for the model
 * object files (poms) and (if successfully resolved) the resolved model artifact.
 */
data class ResolutionResult(
  val status: FetchStatus,
  val artifact: ResolvedArtifact?
)
