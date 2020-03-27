package com.squareup.tools.maven.resolution

import org.apache.maven.model.Repository
import org.apache.maven.model.RepositoryPolicy

/**
 * Well known repositories here, for convenience, as constants (sealed classes)
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
