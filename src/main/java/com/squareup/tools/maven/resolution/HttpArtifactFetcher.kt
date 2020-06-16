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

import com.squareup.tools.maven.resolution.FetchStatus.RepositoryFetchStatus
import com.squareup.tools.maven.resolution.FetchStatus.RepositoryFetchStatus.FETCH_ERROR
import com.squareup.tools.maven.resolution.FetchStatus.RepositoryFetchStatus.NOT_FOUND
import com.squareup.tools.maven.resolution.FetchStatus.RepositoryFetchStatus.SUCCESSFUL
import java.io.IOException
import java.nio.file.Path
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Request.Builder
import org.apache.maven.model.Repository

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
  /** The path to the local artifact cache. */
  cacheDir: Path,
  /** A function to return an [OkHttpClient], possibly configured for proxies, filtered by url */
  private val client: (url: String) -> OkHttpClient = ProxyHelper::createProxyingClientFromEnv
) : AbstractArtifactFetcher(cacheDir) {

  override fun fetchFile(
    fileSpec: FileSpec,
    repository: Repository,
    path: Path
  ): RepositoryFetchStatus {
    val url = "${repository.url}/$path"
    val request: Request = Builder().url(url).build()
    return client(url).newCall(request)
        .also { info { "About to fetch $url" } }
        .execute()
        .use { response ->
          info { "Fetched $url with response code ${response.code}" }
          when (response.code) {
            200 -> {
              response.body?.bytes()?.let { body ->
                try {
                  val localFile = cacheDir.resolve(path)
                  safeWrite(localFile, body)
                  if (fileSpec.localFile.exists) SUCCESSFUL.SUCCESSFULLY_FETCHED
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
