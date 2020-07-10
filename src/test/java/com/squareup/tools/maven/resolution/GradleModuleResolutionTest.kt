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
@file:JvmName("ResolutionTest")
package com.squareup.tools.maven.resolution

import com.google.common.truth.Truth.assertThat
import com.squareup.tools.maven.resolution.FetchStatus.RepositoryFetchStatus.SUCCESSFUL
import java.nio.file.Files
import org.apache.maven.model.Repository
import org.apache.maven.model.RepositoryPolicy
import org.junit.After
import org.junit.Test

class GradleModuleResolutionTest {
  private val cacheDir = Files.createTempDirectory("resolution-test-")
  private val repoId = "fake-repo"
  private val repoUrl = "fake://repo"
  private val repositories = listOf(Repository().apply {
    id = repoId
    releases = RepositoryPolicy().apply { enabled = "true" }
    url = repoUrl
  })
  private val fakeFetcher = FakeFetcher(
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
              gradleModuleContent = """
                {
                    "formatVersion": "1.1",
                    "component": {
                        "group": "foo.bar",
                        "module": "bar",
                        "version": "1"
                    },
                    "createdBy": {
                        "fake": {
                            "version": "1"
                        }
                    },
                    "variants": [
                        {
                            "name": "api",
                            "attributes": {
                                "org.gradle.usage": "java-api",
                                "org.gradle.category": "library",
                                "org.gradle.libraryelements": "jar"
                            },
                            "files": [
                                { 
                                    "name": "mylib-api.jar", 
                                    "url": "mylib-api-1.2.jar",
                                    "size": "1453",
                                    "sha1": "abc12345",
                                    "md5": "abc12345"
                                }
                            ],
                            "dependencies": [
                                { 
                                    "group": "some.group", 
                                    "module": "other-lib", 
                                    "version": { "requires": "3.4" },
                                    "excludes": [
                                        { "group": "*", "module": "excluded-lib" }
                                    ],
                                    "attributes": {
                                       "buildType": "debug"
                                    }
                                }
                            ]
                        },
                        {
                            "name": "runtime",
                            "attributes": {
                                "org.gradle.usage": "java-runtime",
                                "org.gradle.category": "library",
                                "org.gradle.libraryelements": "jar"
                            },
                            "files": [
                                { 
                                    "name": "mylib.jar", 
                                    "url": "mylib-1.2.jar",
                                    "size": "4561",
                                    "sha1": "abc12345",
                                    "md5": "abc12345"
                                }
                            ],
                            "dependencies": [
                                { 
                                    "group": "some.group", 
                                    "module": "other-lib", 
                                    "version": { "requires": "[3.0, 4.0)", "prefers": "3.4", "rejects": ["3.4.1"] } 
                                }
                            ],
                            "dependencyConstraints": [
                                { 
                                    "group": "some.group", 
                                    "module": "other-lib-2", 
                                    "version": { "requires": "1.0" } 
                                }
                            ]
                        }
                    ]
                }
              """.trimIndent(),
              fileContent = "bar\n",
              sourceContent = "sources",
              classifiedFiles = mapOf("extra" to ("extrastuff" to "bargle"))
          )
        .fakeArtifact(
          repoUrl = "fake://repo",
          coordinate = "foo.bar:baz:2",
          suffix = "txt",
          pomContent = """<?xml version="1.0" encoding="UTF-8"?>
                  <project><modelVersion>4.0.0</modelVersion>
                    <groupId>foo.bar</groupId>
                    <artifactId>baz</artifactId>
                    <version>2</version>
                    <packaging>txt</packaging> 
                  </project>
                  """.trimIndent(),
          fileContent = "baz\n"
        )
      )
  )

  private val resolver = ArtifactResolver(
    cacheDir = cacheDir,
    fetcher = fakeFetcher,
    repositories = repositories
  )

  @After fun tearDown() {
    cacheDir.toFile().deleteRecursively()
    check(!cacheDir.exists) { "Failed to tear down and delete temp directory." }
  }

  @Test fun testResolution() {
    val artifact = resolver.artifactFor("foo.bar:bar:1")
    assertThat(!artifact.pom.localFile.exists).isTrue()
    assertThat(!artifact.gradleModule.localFile.exists).isTrue()

    val result = resolver.resolve(artifact)
    assertThat(result.status).isInstanceOf(SUCCESSFUL::class.java)
    val resolved = result.artifact!!
    assertThat(resolved).isNotNull()
    assertThat(resolved!!.pom.localFile.exists).isTrue()
    assertThat(resolved.pom.localFile.readText()).contains("<groupId>foo.bar</groupId>")
    assertThat(resolved.pom.localFile.readText()).contains("<artifactId>bar</artifactId>")
    assertThat(resolved.pom.localFile.readText()).contains("<version>1</version>")
    assertThat(resolved.gradleModule.localFile.readText()).contains("\"formatVersion\": \"1.1\"")

    assertThat(resolved.model)
  }
}
