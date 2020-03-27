@file:JvmName("ResolutionTest")
package com.squareup.tools.maven.resolution

import com.google.common.truth.Truth.assertThat
import org.apache.maven.model.Repository
import org.apache.maven.model.RepositoryPolicy
import org.junit.After
import org.junit.Test
import java.nio.file.Files

class ResolutionTest {
  private val cacheDir = Files.createTempDirectory("resolution-test-")
  private val repoId = "fake-repo"
  private val repoUrl = "fake://repo"
  private val repositories = listOf(Repository().apply {
    id = repoId
    releases = RepositoryPolicy().apply { enabled = "true" }
    url = repoUrl
  })
  private val fetcher = FakeFetcher(
      cacheDir = cacheDir,
      repositoriesContent = mapOf(repoId to mutableMapOf<String, String>()
          .fakeArtifact(
              repoUrl = "fake://repo",
              coordinate = "foo.bar:bar:1",
              suffix = "txt",
              pomContent = """<?xml version="1.0" encoding="UTF-8"?>
                  <project><modelVersion>4.0.0</modelVersion>
                    <groupId>foo.bar</groupId>
                    <artifactId>bar</artifactId>
                    <version>1</version>
                    <packaging>txt</packaging> 
                  </project>
                  """.trimIndent(),
              fileContent = "bar\n"
          )
      )
  )

  @After fun tearDown() {
    cacheDir.toFile().deleteRecursively()
    check(!cacheDir.exists) { "Failed to tear down and delete temp directory."}
  }

  @Test fun testBasicDownload() {
    val resolver = ArtifactResolver(
        cacheDir = cacheDir,
        fetcher = fetcher,
        repositories = repositories
    )
    val (pom, file) = resolver.download("foo.bar:bar:1")
    assertThat(pom.toString()).isEqualTo("$cacheDir/foo/bar/bar/1/bar-1.pom")
    assertThat(pom.exists).isTrue()
    assertThat(file.toString()).isEqualTo("$cacheDir/foo/bar/bar/1/bar-1.txt")
    assertThat(file.exists).isTrue()
  }

  @Test fun testFetchAvoidance() {
    val resolver = ArtifactResolver(
        cacheDir = cacheDir,
        fetcher = fetcher,
        repositories = repositories
    )
    assertThat(fetcher.count).isEqualTo(0)
    resolver.download("foo.bar:bar:1")
    assertThat(fetcher.count).isEqualTo(6)
    resolver.download("foo.bar:bar:1")
    assertThat(fetcher.count).isEqualTo(6)

  }

}
