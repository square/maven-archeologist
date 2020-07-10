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
@file:JvmName("ResolutionTest")
package com.squareup.tools.maven.resolution

import com.squareup.tools.maven.resolution.gradle.AvailableAt.AvailableAtV1_1
import com.squareup.tools.maven.resolution.gradle.ModuleDependencyConstraint.ModuleDependencyConstraintV1_1
import com.squareup.tools.maven.resolution.gradle.ModuleFile.ModuleFileV1_1
import com.squareup.tools.maven.resolution.gradle.ModuleVariant.ModuleVariantV1_1
import com.squareup.tools.maven.resolution.gradle.ModuleVersionConstraint.ModuleVersionConstraintV1_1
import org.junit.Assert.assertThrows
import org.junit.Test

class GradleModelTest {

  @Test fun testModuleVariantValidity() {
    assertThrows(IllegalArgumentException::class.java) {
      ModuleVariantV1_1(
        name = "f",
        availableAt = AvailableAtV1_1("foo", "bar", "baz"),
        files = listOf(ModuleFileV1_1(url = "f", name = "f.txt", size = 5))
      )
    }
    assertThrows(IllegalArgumentException::class.java) {
      ModuleVariantV1_1(
        name = "f",
        availableAt = AvailableAtV1_1("foo", "bar", "baz"),
        dependencyConstraints = listOf(
          ModuleDependencyConstraintV1_1("foo", "bar", ModuleVersionConstraintV1_1("2.2"))
        )
      )
    }
    assertThrows(IllegalArgumentException::class.java) {
      ModuleVariantV1_1(
        name = "f",
        availableAt = AvailableAtV1_1("foo", "bar", "baz"),
        dependencyConstraints = listOf(
          ModuleDependencyConstraintV1_1("foo", "bar", ModuleVersionConstraintV1_1("2.2"))
        ),
        files = listOf(ModuleFileV1_1(url = "f", name = "f.txt", size = 5))
      )
    }
    // Should create fine
    ModuleVariantV1_1(name = "f")
    ModuleVariantV1_1(
      name = "f",
      files = listOf(ModuleFileV1_1(url = "f", name = "f.txt", size = 5))
    )
    ModuleVariantV1_1(
      name = "f",
      dependencyConstraints = listOf(
        ModuleDependencyConstraintV1_1("foo", "bar", ModuleVersionConstraintV1_1("2.2"))
      )
    )
    ModuleVariantV1_1(
      name = "f",
      dependencyConstraints = listOf(
        ModuleDependencyConstraintV1_1("foo", "bar", ModuleVersionConstraintV1_1("2.2"))
      ),
      files = listOf(ModuleFileV1_1(url = "f", name = "f.txt", size = 5))
    )
  }
}
