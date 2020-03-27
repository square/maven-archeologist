package com.squareup.tools.maven.resolution

import com.squareup.tools.maven.resolution.FetchStatus.RepositoryFetchStatus
import org.apache.maven.model.Repository
import java.nio.file.Path

/**
 * A convenience partial implementation which does all the organization around fetch results and trying
 * various repositories.
 */
abstract class AbstractArtifactFetcher(protected val cacheDir: Path): ArtifactFetcher {
  override fun fetchPom(pom: PomFile, repositories: List<Repository>): FetchStatus =
      fetchFiles(pom, repositories)

  override fun fetchArtifact(artifactFile: ArtifactFile, repositories: List<Repository>): FetchStatus =
      fetchFiles(artifactFile, repositories)

  /**
   * The workhorse method which actually fetches a file and writes it to the local cacheDir in the
   * appropriate location. Implementations are expected to write fetch from
   * `"${repository.url}/$path"` to `cacheDir.resolve(path)` for any URLs they support, and return
   * the appropriate RepositoryFetchStatus based on the success of that operation.
   */
  abstract fun fetchFile(fileSpec: FileSpec, repository: Repository, path: Path): RepositoryFetchStatus

  fun fetchFiles(fileSpec: FileSpec, repositories: List<Repository>): FetchStatus {
    if (fileSpec.artifact.snapshot)
      return RepositoryFetchStatus.FETCH_ERROR(
          message = "Snapshot versions not supported (${fileSpec.artifact.coordinate})"
      )

    if (!fileSpec.localFile.exists) {
      val errors = mutableMapOf<String, RepositoryFetchStatus>()
      repositories@ for (repository in repositories) {
        if (repository.releases.isEnabled) {
          val result = when (repository.layout) {
            "default" -> fetchFileAndHashes(fileSpec, repository)
            else -> throw UnsupportedOperationException(
                "${repository.layout} layout not supported for ${repository.id}"
            )
          }
          when (result) {
            is RepositoryFetchStatus.FETCH_ERROR -> errors[repository.id] = result
            is RepositoryFetchStatus.NOT_FOUND -> {
              /* Ignore "not founds" - we'll return that if we never get a file by the end. */
            }
            is RepositoryFetchStatus.SUCCESSFUL -> break@repositories
          }
        }
      }
      return when {
        !fileSpec.localFile.exists -> if (errors.isEmpty()) RepositoryFetchStatus.NOT_FOUND else FetchStatus.ERROR(
            errors)
        !fileSpec.validateHashes() -> FetchStatus.INVALID_HASH
        else -> RepositoryFetchStatus.SUCCESSFUL
      }
    } else {
      info { "Found cached file ${fileSpec.localFile}" }
      return RepositoryFetchStatus.SUCCESSFUL
    }
  }

  private fun fetchFileAndHashes(fileSpec: FileSpec, repository: Repository): RepositoryFetchStatus {
    // TODO profile and make this a parallel fetch
    val mainFetchResult = fetchFile(fileSpec, repository, fileSpec.path)

    // Best effort fetching of md5 and sha1 hash files.
    // TODO make this do something more secure with hashes
    fetchFile(fileSpec, repository, fileSpec.path.md5File)
    fetchFile(fileSpec, repository, fileSpec.path.sha1File)
    return mainFetchResult
  }

}
