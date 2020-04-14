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

import java.nio.file.Path
import org.apache.maven.model.Model

private val packagingToSuffix = mapOf(
    "bundle" to "jar"
)

/**
 * Represents the logical artifact coordinates and (unresolved) metadata needed to start the
 * process.  It serves as an easy way to get from a versioned maven spec to a set of file locations
 * and other metadata used during fetching.
 */
open class Artifact
  internal constructor(
    val groupId: String,
    val artifactId: String,
    val version: String,
    cacheDir: Path
  ) {

  val coordinate = "$groupId:$artifactId:$version"

  val snapshot get() = version.endsWith("-SNAPSHOT")

  val pom = PomFile(this, cacheDir)
}

/** Represents an artifact whose metadata has been fully resolved by maven */
class ResolvedArtifact(
  /** The underlying maven model object. */
  val model: Model,
  /** The cache directory into which this file was fetched (or from which it was read) */
  cacheDir: Path,
  /** Whether this artifact metadata was remotely fetched or satisfied from the local cache. */
  val cached: Boolean = false
) :
    Artifact(model.groupId, model.artifactId, model.version, cacheDir) {
  val main = ArtifactFile(this, cacheDir)

  val suffix = packagingToSuffix.getOrDefault(model.packaging, model.packaging)
}

/**
 * An abstract file definition, including a relative path, local expected path, maven
 * coordinate, etc. Generally users won't deal with this abstraction. Examples would be
 * [PomFile] or [ArtifactFile]
 */
interface FileSpec {
  /** The relative path (in default maven layout style) of a file (pom file, artifact, etc) */
  val path: Path

  /**
   * The expected local path to the file (typically something like:
   * `/home/username/.m2/repository/group/path/artifactId/version/artifactId-version.suffix`)
   */
  val localFile: Path

  /**
   * Supplies the maven coordinates (simple, 3-part, colon separated address) for the artifact
   * associated with this file.
   */
  val coordinate: String

  /** The artifact (resolved or otherwise) to which this file is associated */
  val artifact: Artifact

  fun validateHashes(): Boolean {
    assert(localFile.exists) {
      "Attempted to validate hashes on an un-fetched pom file $localFile."
    }
    return validateHash(localFile, "sha1", localFile.sha1File, Path::sha1) &&
        validateHash(localFile, "md5", localFile.md5File, Path::md5)
  }
}

class PomFile
  internal constructor(override val artifact: Artifact, val cacheDir: Path) : FileSpec {
  override val coordinate: String get() = artifact.coordinate

  override val path: Path by lazy {
    // e.g. com/google/guava/guava/16.0.1/guava-16.0.1.pom
    with(artifact) {
      cacheDir.fileSystem.getPath(groupId.groupPath)
          .resolve(artifactId)
          .resolve(version)
          .resolve("$artifactId-$version.pom")
    }
  }

  override val localFile: Path by lazy { cacheDir.resolve(path) }
}

class ArtifactFile(
  override val artifact: ResolvedArtifact,
  private val cacheDir: Path
) : FileSpec {
  override val coordinate: String get() = artifact.coordinate

  override val path: Path by lazy {
    // e.g. com/google/guava/guava/16.0.1/guava-16.0.1.jar
    with(artifact) {
      cacheDir.fileSystem
          .getPath(groupId.groupPath)
          .resolve(artifactId)
          .resolve(version)
          .resolve("$artifactId-$version.$suffix")
    }
  }

  override val localFile: Path by lazy { cacheDir.resolve(path) }
}

val Model.snapshot get() = version.endsWith("-SNAPSHOT")

internal val String.groupPath get() = replace(".", "/")
