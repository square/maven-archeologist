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
 * An identifier for a capability, typically attributed by some plugin, affecting the output
 * artifact, and used as a part of a selector for a specific artifact.
 */
sealed class ModuleCapabilities {
  /** corresponds to a maven group_id */
  abstract val group: String

  /** corresponds to a maven artifact_id */
  abstract val module: String

  /** corresponds to a maven version */
  abstract val version: String

  /** The core identity definition of a gradle component */
  data class ModuleCapabilitiesV1_1(
    override val group: String,
    override val module: String,
    override val version: String
  ) : ModuleCapabilities()
}
