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

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.ParameterException
import com.beust.jcommander.Parameters
import com.beust.jcommander.UnixStyleUsageFormatter
import com.squareup.tools.maven.resolution.ArtifactResolver
import com.squareup.tools.maven.resolution.FetchStatus.RepositoryFetchStatus.SUCCESSFUL
import com.squareup.tools.maven.resolution.GlobalConfig
import com.squareup.tools.maven.resolution.exists
import com.squareup.tools.maven.resolution.isDirectory
import com.squareup.tools.maven.resolution.report
import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayList
import kotlin.system.exitProcess

@Parameters(separators = "=")
object ResolveArgs {
  @Parameter(names = ["--help"], help = true, description = "This help text.", order = 9999)
  var help = false

  @Parameter(names = ["--debug", "-d"], description = "Print debug-level output.")
  var debug = false

  @Parameter(names = ["--verbose", "-v"], description = "Show more output.")
  var verbose = false

  @Parameter(description = "<artifact> [<artifact2>...]")
  internal var artifacts: List<String> = ArrayList()

  @Parameter(
      names = ["--local_maven_cache"],
      description = "The prefix into which maven artifacts will be cached (e.g. @maven//foo/bar). " +
          "The tool will create the local cache directory if it does not exist."
  )
  var localRepository: Path =
      FileSystems.getDefault().getPath("${System.getProperty("user.home")}/.m2/repository")

  fun validate(context: JCommander) {
    if (context.objects.size != 1) throw AssertionError("Processed wrong number of Args classes.")

    if (help) {
      context.usage()
      exitProcess(0) // valid run, but should terminate early.
    }

    if (!localRepository.isAbsolute) localRepository = localRepository.toAbsolutePath().normalize()
    if (!localRepository.exists) {
      try {
        Files.createDirectories(
            localRepository)
      } catch (e: IOException) {
        com.squareup.tools.maven.resolution.error { "Could not create local maven cache $localRepository" }
        e.printStackTrace()
        context.usage()
        exitProcess(1)
      }
    } else if (!localRepository.isDirectory) {
      com.squareup.tools.maven.resolution.error {
        "Local maven cache path ($localRepository) must point to a directory (or be uncreated)"
      }
      context.usage()
      exitProcess(1)
    }

    GlobalConfig.debug =
        debug
    GlobalConfig.verbose =
        verbose
  }
}

fun main(vararg argv: String) {
  val jcommander = JCommander.newBuilder()
      .addObject(ResolveArgs)
      .programName("resolve")
      .build()
      .also { it?.usageFormatter = UnixStyleUsageFormatter(it) }
      .parse(*argv) {
        com.squareup.tools.maven.resolution.error { it.message }
        it.jCommander.usage()
        exitProcess(22) // Invalid argument
      }
  ResolveArgs.validate(
      jcommander)
  val resolver = ArtifactResolver(
      suppressAddRepositoryWarnings = true,
      cacheDir = ResolveArgs.localRepository
  )
  ResolveArgs.artifacts
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

fun JCommander.parse(vararg args: String, failure: (e: ParameterException) -> Unit): JCommander {
  try {
    this.parse(*args)
  } catch (e: ParameterException) {
    failure(e)
  }
  return this
}
