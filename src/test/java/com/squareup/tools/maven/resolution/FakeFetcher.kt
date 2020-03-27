package com.squareup.tools.maven.resolution

import com.google.common.truth.Truth.assertThat

import com.squareup.tools.maven.resolution.FetchStatus.RepositoryFetchStatus
import com.squareup.tools.maven.resolution.FetchStatus.RepositoryFetchStatus.NOT_FOUND
import com.squareup.tools.maven.resolution.FetchStatus.RepositoryFetchStatus.SUCCESSFUL
import org.apache.maven.model.Repository
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

internal class FakeFetcher(
    cacheDir: Path,
    val repositoriesContent: Map<String, Map<String, String>> = mapOf()
): AbstractArtifactFetcher(cacheDir) {
  private val callCounter = AtomicInteger()
  public val count get() = callCounter.get()
  override fun fetchFile(
    fileSpec: FileSpec,
    repository: Repository,
    path: Path
  ): RepositoryFetchStatus {
    callCounter.incrementAndGet()
    val content = repositoriesContent.getOrElse(repository.id) {
      throw IllegalStateException("Repository ${repository.id} is not registered in this fetcher.")
    }
    val url = "${repository.url}/$path"
    val fileContent = content.getOrElse(url) { return NOT_FOUND }
    val localFile = cacheDir.resolve(path)
    Files.createDirectories(localFile.parent)
    Files.write(localFile, fileContent.toByteArray())
    assertThat(localFile.readText() == fileContent)
    return SUCCESSFUL
  }
}

internal fun MutableMap<String, String>.fakeArtifact(
  repoUrl: String,
  coordinate: String,
  suffix: String,
  pomContent: String,
  fileContent: String
): MutableMap<String, String> {
  val (groupId, artifactId, version) = coordinate.split(':')
  val groupPath = groupId.replace('.', '/')
  val dir = "$repoUrl/$groupPath/$artifactId/$version"
  val filePrefix = "$artifactId-$version"
  put("$dir/$filePrefix.pom", pomContent)
  put("$dir/$filePrefix.pom.sha1", pomContent.sha1())
  put("$dir/$filePrefix.pom.md5", pomContent.md5())
  put("$dir/$filePrefix.$suffix", fileContent)
  put("$dir/$filePrefix.$suffix.sha1", fileContent.sha1())
  put("$dir/$filePrefix.$suffix.md5", fileContent.md5())
  return this
}

private fun String.hash(algorithm: String) =
    MessageDigest.getInstance(algorithm).digest(this.toByteArray()).toHex()

private fun String.md5() = hash("MD5")

private fun String.sha1() = hash("SHA1")
