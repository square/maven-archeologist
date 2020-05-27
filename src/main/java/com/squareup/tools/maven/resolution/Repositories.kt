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

import org.apache.maven.model.Repository
import org.apache.maven.model.RepositoryPolicy

/**
 * Well known repositories here, for convenience, as strongly-typed constants (sealed classes)
 *
 * Usage:
 * ```
 * val resolver = ArtifactResolver(
 * ```
 */
sealed class Repositories {
  companion object {
    @JvmField val DEFAULT = listOf(MAVEN_CENTRAL)
  }
  object MAVEN_CENTRAL: Repository() {
    init {
      id = "central"
      releases = RepositoryPolicy().apply {
        enabled = "true"
      }
      url = "https://repo.maven.apache.org/maven2"
    }
  }
  object JCENTER_BINTRAY: Repository() {
    init {
      id = "jcenter-bintray"
      releases = RepositoryPolicy().apply {
        enabled = "true"
      }
      url = "https://jcenter.bintray.com/"
    }
  }
  object GOOGLE_ANDROID: Repository() {
    init {
      id = "google-android"
      releases = RepositoryPolicy().apply {
        enabled = "true"
      }
      url = "https://dl.google.com/dl/android/maven2"
    }
  }
  object GOOGLE_MAVEN_CENTRAL_AMERICAS: Repository() {
    init {
      id = "google-maven-central"
      releases = RepositoryPolicy().apply {
        enabled = "true"
      }
      url = "https://maven-central.storage-download.googleapis.com/repos/central/data"
    }
  }
  object GOOGLE_MAVEN_CENTRAL_EUROPE: Repository() {
    init {
      id = "google-maven-central"
      releases = RepositoryPolicy().apply {
        enabled = "true"
      }
      url = "https://maven-central-eu.storage-download.googleapis.com/repos/central/data"
    }
  }
  object GOOGLE_MAVEN_CENTRAL_ASIA: Repository() {
    init {
      id = "google-maven-central"
      releases = RepositoryPolicy().apply {
        enabled = "true"
      }
      url = "https://maven-central-asia.storage-download.googleapis.com/repos/central/data"
    }
  }
}
