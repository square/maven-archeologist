package com.squareup.tools.maven.resolution

import org.apache.maven.model.Repository

sealed class FetchStatus {
  sealed class RepositoryFetchStatus: FetchStatus() {
    /** Artifact file successfully fetched and validated */
    object SUCCESSFUL: RepositoryFetchStatus()

    /** Artifact file could not be found in any repositories given to the fetcher. */
    object NOT_FOUND: RepositoryFetchStatus()

    /** An individual error from a given repository (id) */
    data class FETCH_ERROR(
      val repository: String? = null,
      val message: Any? = null,
      val responseCode: Int? = null,
      val error: Throwable? = null
    ): RepositoryFetchStatus()
  }

  /** Artifact file found and fetched, but failed hash validation */
  object INVALID_HASH: FetchStatus()

  /**
   * A compound error containing the full map of errors from repositories which had errors.
   *
   * This should be returned where there were non-404 failures from various given repositories,
   * when none of them were successful.
   */
  data class ERROR(val errors: Map<String, RepositoryFetchStatus>): FetchStatus()
}

interface ArtifactFetcher {
  /**
   * Performs a fetch against any repositories offered (in order), downloads the pom file (and any
   * hash files), and if it finds it, performs validation. Returns false if the file wasn't fetched,
   * or if the file's validation failed.
   */
  fun fetchPom(pom: PomFile, repositories: List<Repository>): FetchStatus

  /**
   * Performs a fetch against any repositories offered (in order), downloads the artifact file (and any
   * hash files), and if it finds it, performs validation. Returns false if the file wasn't fetched,
   * or if the file's validation failed.
   */
  fun fetchArtifact(artifactFile: ArtifactFile, repositories: List<Repository>): FetchStatus
}

