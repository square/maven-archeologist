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

import com.squareup.tools.maven.resolution.gradle.ArtifactSelector.ArtifactSelectorV1_1
import com.squareup.tools.maven.resolution.gradle.ModuleCapabilities.ModuleCapabilitiesV1_1
import com.squareup.tools.maven.resolution.gradle.ModuleDependencyExclude.ModuleDependencyExcludeV1_1
import com.squareup.tools.maven.resolution.gradle.ModuleDependencyThirdPartCompatibility.ModuleDependencyThirdPartCompatibilityV1_1
import com.squareup.tools.maven.resolution.gradle.ModuleVersionConstraint.ModuleVersionConstraintV1_1

/**
 * Represents a dependency reference, including its coordinates and special characteristics,
 * such as exclusions.
 */
sealed class ModuleDependency {
  /** The group of the artifact referenced by this dependency, equivalent to a maven group_id */
  abstract val group: String

  /**
   * The module name of the artifact referenced by this dependency, equivalent to a maven
   * artifact_id
   */
  abstract val module: String

  /**
   * The version requirements of this dependency, similar to a maven version specification.
   * It may specify constraints such as ranges, excluded versions, etc.
   */
  abstract val version: ModuleVersionConstraint

  /**
   * Dependency exclusions, intended to trim the transitive dependency graph at this edge.
   */
  abstract val excludes: List<ModuleDependencyExclude>

  /** A text description of the reason why this dependency exists. */
  abstract val reason: String?

  /** Gradle attributes, which may inform this dependency. */
  abstract val attributes: Map<String, Any>

  /** A text description of the reason why this dependency exists. */
  abstract val requestedCapabilities: List<ModuleCapabilities>

  /**
   * all strict versions of the target module will be treated as if they were defined on the
   * variant defining this dependency.
   *
   * TODO(cgruber) Clarify what the heck this means.
   */
  abstract val endorseStrictVersions: Boolean

  /**
   * additional information to be used if the dependency points at a module that did not
   * publish Gradle module metadata. Typically this references a maven classifier, etc.
   */
  abstract val thirdPartyCompatibility: ModuleDependencyThirdPartCompatibility?

  data class ModuleDependencyV1_1(
    override val group: String,
    override val module: String,
    override val version: ModuleVersionConstraintV1_1,
    override val excludes: List<ModuleDependencyExcludeV1_1> = listOf(),
    override val reason: String? = null,
    override val attributes: Map<String, Any> = mapOf(),
    override val requestedCapabilities: List<ModuleCapabilitiesV1_1> = listOf(),
    override val endorseStrictVersions: Boolean = false,
    override val thirdPartyCompatibility: ModuleDependencyThirdPartCompatibilityV1_1? = null
  ) : ModuleDependency()
}

/** An exclusion consisting of a group and a module name */
sealed class ModuleDependencyExclude {
  abstract val group: String
  abstract val module: String

  data class ModuleDependencyExcludeV1_1(
    override val group: String,
    override val module: String
  ) : ModuleDependencyExclude()
}

sealed class ModuleDependencyThirdPartCompatibility {
  abstract val artifactSelector: ArtifactSelector

  data class ModuleDependencyThirdPartCompatibilityV1_1(
    override val artifactSelector: ArtifactSelectorV1_1
  ) : ModuleDependencyThirdPartCompatibility()
}

sealed class ArtifactSelector {
  abstract val name: String
  abstract val type: String
  abstract val extension: String?
  abstract val classifier: String?

  data class ArtifactSelectorV1_1(
    override val name: String,
    override val type: String,
    override val extension: String?,
    override val classifier: String?
  ) : ArtifactSelector()
}
