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

import com.squareup.moshi.Json
import com.squareup.tools.maven.resolution.gradle.AvailableAt.AvailableAtV1_1
import com.squareup.tools.maven.resolution.gradle.ModuleDependency.ModuleDependencyV1_1
import com.squareup.tools.maven.resolution.gradle.ModuleDependencyConstraint.ModuleDependencyConstraintV1_1
import com.squareup.tools.maven.resolution.gradle.ModuleFile.ModuleFileV1_1

/**
 * A unique variant, unique in [name], and unique in Pair([attributes], [capabilities]), so these
 * can be used as selectors.  Systems which support this variant system may be supplied with
 * attributes and constraints, such that a variant can be selected. No combination of attributes
 * and capabilities may be shared by more than one named variant.
 */
sealed class ModuleVariant {
  abstract val name: String

  /** Attributes that distinguish this variant from others (and supply configuration information) */
  abstract val attributes: Map<String, Any>
  /** Capabilities that distinguish this variant from others. */
  abstract val capabilities: Map<String, String>

  // Define the variant - mutually exclusive with available-at
  abstract val dependencies: List<ModuleDependency>
  abstract val files: List<ModuleFile>
  abstract val dependencyConstraints: List<ModuleDependencyConstraint>

  // Redirects to another artifact for variant definition - mutually incompatible with
  // dependencies, files, dependencyConstraints, and capabilities.
  abstract val availableAt: AvailableAt?

  protected fun validate() {
    require((availableAt == null) ||
        (files.isEmpty() && dependencyConstraints.isEmpty() && dependencies.isEmpty())
    ) {
      "If avaliable-at is set then " +
        "neither files nor dependencyConstraints nor dependencies may be set on a variant"
    }
  }

  class ModuleVariantV1_1(
    override val name: String,
    override val attributes: Map<String, Any> = mapOf(),
    override val capabilities: Map<String, String> = mapOf(),
    override val dependencies: List<ModuleDependencyV1_1> = listOf(),
    override val files: List<ModuleFileV1_1> = listOf(),
    override val dependencyConstraints: List<ModuleDependencyConstraintV1_1> = listOf(),
    @Json(name = "available-at")
    override val availableAt: AvailableAtV1_1? = null
  ) : ModuleVariant() {
    init { this.validate() }
  }
}

/**
 * A variant redirection signal. Mutually incompatible with [ModuleVariant.files],
 * [ModuleVariant.dependencies], and [ModuleVariant.dependencyConstraints].
 */
sealed class AvailableAt {
  /** corresponds to a maven group_id */
  abstract val group: String

  /** corresponds to a maven artifact_id */
  abstract val module: String

  /** corresponds to a maven version */
  abstract val version: String

  /** A location which may be a proper UI, or a relative path to this file's location */
  abstract val url: String?

  data class AvailableAtV1_1(
    override val group: String,
    override val module: String,
    override val version: String,
    override val url: String? = null
  ) : AvailableAt()
}
