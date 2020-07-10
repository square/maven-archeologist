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

import com.squareup.tools.maven.resolution.gradle.ModuleVersionConstraint.ModuleVersionConstraintV1_1

sealed class ModuleDependencyConstraint {
  abstract val group: String
  abstract val module: String
  abstract val version: ModuleVersionConstraint

  data class ModuleDependencyConstraintV1_1(
    override val group: String,
    override val module: String,
    override val version: ModuleVersionConstraintV1_1
  ) : ModuleDependencyConstraint()
}
