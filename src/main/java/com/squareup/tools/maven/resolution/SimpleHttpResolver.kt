package com.squareup.tools.maven.resolution

import com.squareup.tools.maven.resolution.FetchStatus.RepositoryFetchStatus.SUCCESSFUL
import org.apache.maven.model.Dependency
import org.apache.maven.model.Parent
import org.apache.maven.model.Repository
import org.apache.maven.model.building.ModelSource2
import org.apache.maven.model.resolution.ModelResolver
import java.io.InputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

/**
 * Performs [ModelResolver] responsibilities using a simple http based fetcher. This is a stateful
 * class containing the sets of repositories initially given, plus any repositories added.
 *
 * Contra the contract of ModelResolver, new repositories discovered during resolution will NOT be
 * added to the list, in order to ensure security. If more repositories are needed, the
 * [ArtifactResolver] (library entry-point) should be configured with the needed repositories.
 */
internal data class SimpleHttpResolver(
  private val cacheDir: Path,
  private val fetcher: ArtifactFetcher, // Usually a HttpArtifactFetcher, but could be fake or file
  private val repositories: List<Repository> = mutableListOf(),
  private val suppressAddRepositoryWarnings: Boolean = false
) : ModelResolver {
  private val modelCache: MutableMap<Artifact, FileModelSource?> = mutableMapOf()
  private val newRepos = mutableSetOf<Repository>()

  override fun addRepository(newRepo: Repository) {
    addRepository(newRepo, false)
  }

  override fun addRepository(repo: Repository, replace: Boolean) {
    if (suppressAddRepositoryWarnings) return

    // Swallow this method, as we cannot permit arbitrary repositories, but warn
    val existing = repositories.find { it.id == repo.id }
    if (existing != null) {
      if (existing.url != repo.url) {
        // if it's the same id and url, don't even warn. Otherwise, complain.
        warn { "Ignoring attempt to replace a repository ${repo.id} (url: ${repo.url})." }
      }
    } else if (!newRepos.contains(repo)) {
      newRepos.add(repo)
      warn { "Ignoring attempt to add a repository ${repo.id} (url: ${repo.url}). " +
          "If this repository is needed, add to the fixed list of repositories" }
    }
  }

  override fun newCopy() = copy(
      repositories = mutableListOf<Repository>().apply { addAll(repositories) }
  )

  private fun resolve(artifact: Artifact): FileModelSource? {
    return modelCache.getOrPut(artifact) {
      val fetched = fetcher.fetchPom(artifact.pom, repositories)
      if (fetched is SUCCESSFUL) FileModelSource(artifact.pom.localFile) else null
    }
  }

  override fun resolveModel(groupId: String, artifactId: String, version: String): ModelSource2? =
    resolve(Artifact(groupId, artifactId, version, cacheDir))

  override fun resolveModel(parent: Parent): ModelSource2? =
    resolve(Artifact(parent.groupId, parent.artifactId, parent.version, cacheDir))

  override fun resolveModel(dependency: Dependency): ModelSource2? =
    resolve(Artifact(dependency.groupId, dependency.artifactId, dependency.version, cacheDir))
}

/**
 * A local implementation of an internal part of the resolving infrastructure. This simply wraps
 * the locally downloaded file and gives its information to the maven model infrastructure.
 */
private class FileModelSource(val file: Path) : ModelSource2 {
  override fun getLocationURI(): URI = file.toUri()

  override fun getLocation(): String = file.toString()

  override fun getRelatedSource(relativePath: String) = null // We don't handle local project layouts

  override fun getInputStream(): InputStream = Files.newInputStream(file)
}
