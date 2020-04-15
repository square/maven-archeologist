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

import kotlin.math.min

const val LESS_THAN = -1
const val GREATER_THAN = 1
const val EQUAL = 0

/**
 * Encapsulates the logic of maven version comparisons, notably enshrining the logic described
 * [in this article](https://blog.soebes.de/blog/2017/02/04/apache-maven-how-version-comparison-works).
 */
class MavenVersion private constructor(
  private val raw: String,
  private val elements: List<String>,
  val snapshot: Boolean = false
) : Comparable<MavenVersion> {
  override fun toString() = raw
  override fun equals(other: Any?) = other is MavenVersion && compareTo(other) == EQUAL
  override fun hashCode() = 31 + raw.hashCode()
  override fun compareTo(other: MavenVersion): Int {
    if (raw == other.raw) return EQUAL // simple obvious case.
    // loop through the next-to-last of the shortest.
    val minLength = min(elements.size, other.elements.size)
    for (i in 0..minLength - 2) {
      elements[i].numberOrStringComparison(other.elements[i]).let {
        cmp -> if (cmp != EQUAL) return cmp
      }
    }
    // so far, all but the last element (of the shortest version) are equal.
    // 1.3.5.2 vs. 1.3.6-RC1 or 1.3.5 vs. 1.3.05, should all make it here.
    val a = VersionElement.from(
        elements[minLength - 1],
        elements.size == minLength)
    val b = VersionElement.from(
        other.elements[minLength - 1],
        other.elements.size == minLength)
    // test the last element
    a.compareTo(b).let { comparison -> if (comparison != EQUAL) return comparison }

    // so far, the equivalent elements all match. Now check to see if one has more elements
    // (1.3.5 > 1.3)
    return when {
      elements.size > other.elements.size -> return GREATER_THAN // 1.3.5 > 1.3
      elements.size < other.elements.size -> return LESS_THAN // 1.3 < 1.3.5
      else -> EQUAL
    }
  }

  companion object {
    @JvmStatic fun from(raw: String) = with(raw.split(".")) {
      MavenVersion(raw, this, this.last().endsWith("-SNAPSHOT"))
    }
  }
}

internal data class VersionElement(
  val core: String,
  val qualifier: String? = null,
  val snapshot: Boolean = false
) : Comparable<VersionElement> {

  override fun compareTo(other: VersionElement): Int {
    var compare = core.numberOrStringComparison(other.core)
    if (compare != 0) return compare
    compare = when {
      qualifier == null && other.qualifier != null -> GREATER_THAN // qualified version is lesser
      qualifier != null && other.qualifier == null -> LESS_THAN // unqualified version is greater
      else -> {
        qualifier?.numberOrStringComparison(other.qualifier!!) ?: EQUAL
      }
    }
    if (compare != 0) return compare
    // qualifiers are equal or don't exist - versions are equal at this point. Checking snapshot
    return when {
      this.snapshot && !other.snapshot -> LESS_THAN // snapshot less than release
      !this.snapshot && other.snapshot -> GREATER_THAN // release greater than snapshot
      else -> EQUAL
    }
  }

  companion object {
    fun from(raw: String, terminal: Boolean): VersionElement {
      require(!raw.contains(".")) { "Version elements may not contains '.' characters." }
      return if (terminal) {
        val noSnapshot = raw.removeSuffix("-SNAPSHOT")
        val parts = noSnapshot.split("-", limit = 2)
        VersionElement(
            core = parts[0],
            qualifier = if (parts.size == 2) parts[1] else null,
            snapshot = raw.endsWith("-SNAPSHOT")
        )
      } else VersionElement(raw)
    }
  }
}

internal fun String.numberComparison(other: String): Int? {
  val a = this.toIntOrNull()
  val b = other.toIntOrNull()
  return if (a != null && b != null) a.compareTo(b)
  else null
}

internal fun String.numberOrStringComparison(other: String) =
    numberComparison(other) ?: this.compareTo(other)
