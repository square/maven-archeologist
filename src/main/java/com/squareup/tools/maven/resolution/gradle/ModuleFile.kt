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
package com.squareup.tools.maven.resolution.gradle

/** Represents a file reference, including name, location, and content characteristics. */
sealed class ModuleFile {
  abstract val name: String
  abstract val url: String
  abstract val size: Long
  abstract val md5: String?
  abstract val sha1: String?
  abstract val sha256: String?
  abstract val sha512: String?

  data class ModuleFileV1_1(
    override val name: String,
    override val url: String,
    override val size: Long,
    override val md5: String? = null,
    override val sha1: String? = null,
    override val sha256: String? = null,
    override val sha512: String? = null
  ) : ModuleFile()
}
