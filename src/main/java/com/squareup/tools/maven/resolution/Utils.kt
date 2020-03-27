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
