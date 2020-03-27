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

import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path

/* Internal utilities and extensions to make java APIs more convenient. */

// Path Utilities

internal val Path.isDirectory: Boolean get() = Files.isDirectory(this)

internal val Path.exists: Boolean get() = Files.exists(this)

internal fun Path.readText(charset: Charset = Charsets.UTF_8) =
    String(Files.readAllBytes(this), charset)

// Trivial logging utilities (These rely on configuring GlobalConfig

fun report(message: Any?, inline: Boolean = false) {
  if (inline) print(message) else println(message)
}

fun debug(message: () -> Any?) {
  if (GlobalConfig.debug) println("DEBUG: ${message.invoke()}")
}

fun info(message: () -> Any?) {
  if (GlobalConfig.verbose || GlobalConfig.debug) println("INFO: ${message.invoke()}")
}

fun warn(message: () -> Any?) {
  System.err.println("WARNING: ${message.invoke()}")
}

fun error(message: () -> Any?) {
  error(null, message)
}

fun error(throwable: Throwable?, message: () -> Any?) {
  System.err.println("ERROR: ${message.invoke()}")
  throwable?.printStackTrace(System.err)
}
