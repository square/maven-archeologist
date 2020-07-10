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

import com.squareup.tools.maven.resolution.gradle.ModuleComponent.ModuleComponentV1_1
import com.squareup.tools.maven.resolution.gradle.ModuleVariant.ModuleVariantV1_1

sealed class Module {
  /** Which version of the gradle module specification does this json conform to? */
  abstract val formatVersion: String

  /**
   * Details what system created this file. (typically gradle, with version and buildId). There
   * should only be one entry.  This could be a stronger type, but there's no reason to fail
   * if there's variance, so
   */
  abstract val createdBy: Map<String, Map<String, String>>

  abstract val component: ModuleComponent

  abstract val variants: List<ModuleVariant>

  data class ModuleV1_1(
    override val formatVersion: String,
    override val createdBy: Map<String, Map<String, String>>,
    override val component: ModuleComponentV1_1,
    override val variants: List<ModuleVariantV1_1>
  ) : Module() {
    init {
      assert(formatVersion in setOf("1.0", "1.1")) {
        "Parsed Gradle Module declaring format version $formatVersion with 1.1 model objects."
      }
    }
  }
}
