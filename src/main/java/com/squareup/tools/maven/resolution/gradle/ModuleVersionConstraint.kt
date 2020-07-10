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

/**
 * A constraint to indicate a version, a range of versions, a preferred version, and any excluded
 * versions consumable by a [ModuleDependency]
 */
sealed class ModuleVersionConstraint {
  /** A version string, which may be a range if maven-style semantic versioning is used. */
  abstract val requires: String?

  /**
   * An optional version [String], indicating a version which, if available, should be selected
   * if multiple versions satisfy the [requires] constraint.  If [requires] is a single verions,
   * then this preference is superfluous.
   */
  // TODO(cgruber) Determine what semantic there is for a conflict between prefers and excludes.
  abstract val prefers: String?

  /** A list of [String] versions which should be considered ineligible. */
  abstract val rejects: List<String>

  data class ModuleVersionConstraintV1_1(
    override val requires: String? = null,
    override val prefers: String? = null,
    override val rejects: List<String> = listOf()
  ) : ModuleVersionConstraint()
}
