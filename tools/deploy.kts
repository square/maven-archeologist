#!/usr/bin/env kscript

/*
 * Copyright 2020 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:MavenRepository("maven-central", "https://repo1.maven.org/maven2")
@file:DependsOn("com.github.ajalt:clikt:2.6.0")

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import java.io.File
import java.io.IOException
import java.lang.ProcessBuilder.Redirect.INHERIT
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.system.exitProcess

class Main : CliktCommand() {
  private val key by option("-k", "--key", help = "GPG Key for deployment to sonatype")
  private val travis by option(envvar = "TRAVIS", help = "Operate with CI behavior").flag()
  private val branch by option(envvar = "TRAVIS_BRANCH", help = "Branch name override")
      .default("git branch --show-current".execute().stdout)
  private val verbose by option("-v", "-verbose").flag()

  private val username by option(hidden = true, envvar = "CI_DEPLOY_USERNAME")
  private val password by option(hidden = true, envvar = "CI_DEPLOY_PASSWORD")

  private val bazel = "bazel".which()

  private val version = File("versions.bzl").extractPythonicStringVariable("LIBRARY_VERSION").also {
    if (it.isEmpty()) {
      System.err.println("Could not extract version from version file.")
      exitProcess(1)
    }
  }
  private val snapshotVersion = version.endsWith("-SNAPSHOT")

  val pom_file = "$bazel build //tools/release:pom".execAndFilterSuffix(".pom")
  val artifact_file = "$bazel build //tools/release:deployment_jar".execAndFilterSuffix(".jar")
  val sources_file = "$bazel build //tools/release:sources_jar".execAndFilterSuffix("-sources.jar")
  val javadoc_file = "tools/release/placeholder-javadoc.jar"

  override fun run() {
    if (!File("WORKSPACE").exists()) {
      System.err.println("Must run deployment script from the workspace root.")
      exitProcess(1)
    }
    val repo = if (travis) {
      if (branch == "main") Repo.SonatypeSnapshots
      else throw PrintMessage("Aborting deployment on a non-main branch.")
    } else if (branch.startsWith("release-")) {
      if (key == null) {
        throw UsageError("Must supply --key <gpgkey> for release deployments.")
      } else if (snapshotVersion) {
        throw UsageError("Don't use a snapshot version ($version) on a release branch ($branch)")
      } else Repo.SonatypeStaging
    } else Repo.FakeLocalRepo

    val mvn_goal = key?.let { "gpg:sign-and-deploy-file" } ?: "deploy:deploy-file"
    val key_flag = key?.let { " -Dgpg.keyname=$it" } ?: ""
    val settings_file = if (repo != Repo.FakeLocalRepo) " -gs tools/release/settings.xml" else ""
    val debug_flag = if (verbose) " --debug" else ""
    val javadoc_flag = if (!snapshotVersion) " -Djavadoc=$javadoc_file" else ""
    val mvn_cmd = "mvn $mvn_goal" +
        settings_file +
        debug_flag +
        " -Dfile=$artifact_file" +
        " -DpomFile=$pom_file" +
        " -Dsources=$sources_file" +
        " -DrepositoryId=${repo.id}" +
        " -Durl=${repo.url}" +
        javadoc_flag
        key_flag

    echo("Deploying version $version to $repo")
    if (verbose) echo("Executing command: $mvn_cmd")
    mvn_cmd.cmd(outputRedirect = INHERIT, errorRedirect = INHERIT)
        .apply {
          with(environment()) {
            if (repo != Repo.FakeLocalRepo) {
              if (username != null && password != null) {
                putIfAbsent("CI_DEPLOY_USERNAME", username)
                putIfAbsent("CI_DEPLOY_PASSWORD", password)
              } else {
                throw UsageError("Must supply either CI_DEPLOY_USERNAME/CI_DEPLOY_PASSWORD " +
                    "environment vairables, or override --username/--password to deploy to a " +
                    "non-fake repo.")
              }
            }
          }
        }
        .execute(timeout = 300)
  }
}

sealed class Repo(val id: String, val url: String) {
  object SonatypeSnapshots : Repo(
      "sonatype-nexus-snapshots",
      "https://oss.sonatype.org/content/repositories/snapshots"
  )
  object SonatypeStaging : Repo(
      "sonatype-nexus-staging",
      "https://oss.sonatype.org/service/local/staging/deploy/maven2"
  )
  object FakeLocalRepo : Repo(
      "local-fake",
      "file:///tmp/fakerepo"
  )
}

fun String.execAndFilterSuffix(suffix: String) =
    cmd()
        .execute()
        .let { proc ->
          if (proc.isAlive) throw TimeoutException("Should not still be running.")
          if (proc.exitValue() != 0) {
            val error = """Error executing command.
                Stdout: ${proc.stdout}
                Stderr: ${proc.stderr}
                """.trimIndent()
            throw IllegalStateException(error)
          }
          proc.stdout + proc.stderr
        }
        .lines()
        .first { it.endsWith(suffix) }
        .trim()

/** A utility method to extact a known version */
fun File.extractPythonicStringVariable(variable: String) =
    readText()
        .lines()
        .first { it.startsWith(variable) }
        .substringBefore("#") // ditch comments
        .substringAfter("=")
        .trim('"', ' ')

fun Process.wait(timeout: Long = 120, unit: TimeUnit = TimeUnit.SECONDS): Process =
    this.also { it.waitFor(timeout, unit) }

val Process.stderr: String get() = errorStream.bufferedReader().readText()

val Process.stdout: String get() = inputStream.bufferedReader().readText()

/** Short-cut which creates the command and executes it directly */
fun String.execute(
  timeout: Long = 120,
  unit: TimeUnit = TimeUnit.SECONDS,
  onTimeout: (Process) -> Unit = {},
  onError: (Process) -> Unit = {}
) = cmd().execute(timeout, unit, onTimeout, onError)

fun String.cmd(
  workingDir: File = File("."),
  outputRedirect: ProcessBuilder.Redirect = ProcessBuilder.Redirect.PIPE,
  errorRedirect: ProcessBuilder.Redirect = ProcessBuilder.Redirect.PIPE
): ProcessBuilder {
  val parts = this.split("\\s".toRegex())
  return ProcessBuilder(*parts.toTypedArray())
      .directory(workingDir)
      .redirectOutput(outputRedirect)
      .redirectError(errorRedirect)
}

fun ProcessBuilder.execute(
  timeout: Long = 120,
  unit: TimeUnit = TimeUnit.SECONDS,
  onTimeout: (Process) -> Unit = {},
  onError: (Process) -> Unit = {}
): Process = start().wait(timeout, unit).apply {
  if (isAlive) onTimeout(this)
  else when (exitValue()) {
    0 -> {}
    else -> onError(this)
  }
}

/**
 * Wraps the unix `which` comamnd, returning the first path entry for the given command, or null.
 */
fun String.which() = "which $this"
    .execute()
    .stdout
    .trim()
    .also {
      if (it.isEmpty()) throw IOException("Could not locate baze binary")
    }

Main().main(args)
