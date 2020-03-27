package com.squareup.tools.maven.resolution

import com.squareup.tools.maven.resolution.FetchStatus.RepositoryFetchStatus
import com.squareup.tools.maven.resolution.FetchStatus.RepositoryFetchStatus.FETCH_ERROR
import com.squareup.tools.maven.resolution.FetchStatus.RepositoryFetchStatus.NOT_FOUND
import com.squareup.tools.maven.resolution.FetchStatus.RepositoryFetchStatus.SUCCESSFUL
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Request.Builder
import org.apache.maven.model.Repository
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * An engine for performing fetches against maven repositories.  Generally this class' methods will
 * operate with side-effects, attempting to download the various files associated with an artifact
 * (e.g. pom file, main artifact file, etc.), and will signal if it was successful. It is left to
 * the callers to do something with the files in question.
 *
 * This class makes the broad assumption that the data will be in UTF-8 (or compatible, such as
 * ASCII) format, as that is what the maven pom specification recommends.
 *
 * This class currently does not support SNAPSHOT artifacts.
 * TODO: Handle snapshots
 */
class HttpArtifactFetcher(
  cacheDir: Path,
  private val client: OkHttpClient = OkHttpClient()
): AbstractArtifactFetcher(cacheDir) {

  override fun fetchFile(
    fileSpec: FileSpec,
    repository: Repository,
    path: Path): RepositoryFetchStatus {
    val url = "${repository.url}/$path"
    val request: Request = Builder().url(url)
        .build()
    return client.newCall(request)
        .also { info { "About to fetch $url" } }
        .execute()
        .use { response ->
          info { "Fetched $url with response code ${response.code}" }
          when (response.code) {
            200 -> {
              response.body?.bytes()?.let { body ->
                try {
                  var localFile = cacheDir.resolve(path)
                  Files.createDirectories(localFile.parent)
                  Files.write(localFile, body)
                  if (fileSpec.localFile.exists) SUCCESSFUL
                  else FETCH_ERROR(
                      repository = repository.id,
                      message = "File downloaded but did not write successfully."
                  )
                } catch (e: IOException) {
                  FETCH_ERROR(
                      repository = repository.id,
                      message = "Failed to write file",
                      error = e
                  )
                }
              } ?: FETCH_ERROR(
                  repository = repository.id,
                  message = "$path was resolved from ${repository.url} with no body"
              )
            }
            404 -> NOT_FOUND
            else -> {
              warn { "Error fetching ${fileSpec.artifact.coordinate} (${response.code}): " }
              debug { "Error content: ${response.body}" }
              FETCH_ERROR(
                  repository = repository.id,
                  message = "Unknown error fetching ${fileSpec.artifact.coordinate}",
                  responseCode = response.code
              )
            }
          }
        }
  }
}
