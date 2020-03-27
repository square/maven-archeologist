package com.squareup.tools.maven.resolution

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/*
 * Convenience extensions and functions related to hash computation, to make consuming code more
 * readable.  These are maven-specific, focusing on the md5 and sha1 algorithms, which maven artifacts
 * are published with.
 */

internal val Path.sha1File get() = parent.resolve("$fileName.sha1")

internal val Path.md5File get() = parent.resolve("$fileName.md5")

private fun Path.hash(algorithm: String) =
  MessageDigest.getInstance(algorithm).digest(Files.readAllBytes(this)).toHex()

internal fun Path?.md5() = this?.hash("MD5") ?: "d41d8cd98f00b204e9800998ecf8427e"

internal fun Path?.sha1() = this?.hash("SHA1") ?: "da39a3ee5e6b4b0d3255bfef95601890afd80709"

internal fun validateHash(localFile: Path, algorithm: String, file: Path, hashFunction: (Path) -> String): Boolean {
  return if (!file.exists) {
    // We don't fail if there's no file as some artifacts didn't ship with all hash files.
    true.also { info { "Validation: No $algorithm file found: $file." } }
  } else {
    val hash = file.readText().trim().split(' ', '\n')[0]
    val calculatedHash = hashFunction(localFile)
    (calculatedHash == hash).also { matched ->
      if (!matched)
        warn { "$algorithm failed on $localFile: found $calculatedHash but $file contained $hash" }
    }
  }
}

internal fun ByteArray.toHex() =
    joinToString("") { (0xFF and it.toInt()).toString(16).padStart(2, '0') }
