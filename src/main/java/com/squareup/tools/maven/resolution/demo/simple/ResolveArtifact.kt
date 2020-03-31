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
@file:JvmName("ResolveArtifact")
package com.squareup.tools.maven.resolution.demo.simple

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import com.squareup.tools.maven.resolution.ArtifactResolver
import com.squareup.tools.maven.resolution.FetchStatus.RepositoryFetchStatus.SUCCESSFUL
import com.squareup.tools.maven.resolution.GlobalConfig
import com.squareup.tools.maven.resolution.error
import com.squareup.tools.maven.resolution.exists
import com.squareup.tools.maven.resolution.report
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

class Resolve: CliktCommand() {
  private val debug by option("--debug", "-d").flag(default = false)

  private val verbose by option("--verbose", "-v").flag(default = false)

  val localRepository: Path by option(
      "--local_maven_cache",
      help= "The prefix into which maven artifacts will be cached (e.g. @maven//foo/bar). " +
          "The tool will create the local cache directory if it does not exist."
  )
      .path(canBeFile = false, canBeSymlink = false, canBeDir = true)
      .default(Paths.get("${System.getProperties()["user.home"]}",".m2/repository"))

  val artifacts by argument().multiple()

  private fun validate() {
    GlobalConfig.debug = debug
    GlobalConfig.verbose = verbose
    if (!localRepository.exists) {
      try {
        Files.createDirectories(localRepository)
      } catch (e: IOException) {
        error { "Could not create local maven cache $localRepository" }
        e.printStackTrace()
        exitProcess(1)
      }
    }
  }
  override fun run() {
    validate()
    val resolver = ArtifactResolver(
        suppressAddRepositoryWarnings = true,
        cacheDir = localRepository
    )
    artifacts
        .map { resolver.artifactFor(it) }
        .forEach { artifact ->
          val resolvedArtifact = resolver.resolveArtifact(artifact)
          resolvedArtifact?.apply {
            report(
                "Artifact model for ${resolvedArtifact.coordinate} successfully resolved.")
            report(
                "Pom file available at ${resolvedArtifact.pom.localFile}.")
            val result = resolver.downloadArtifact(resolvedArtifact)
            report(
                "Attempt to fetch ${resolvedArtifact.coordinate} " +
                    "was ${result.javaClass.simpleName}")
            if (result is SUCCESSFUL)
              report(
                  "Main artifact file available at ${resolvedArtifact.main.localFile}")
          }
        }
  }
}

fun main(vararg argv: String) {
  Resolve().main(argv.toList())
}
