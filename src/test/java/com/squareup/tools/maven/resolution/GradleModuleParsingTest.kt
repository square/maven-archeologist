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

import com.google.common.truth.Truth.assertThat
import com.squareup.tools.maven.resolution.gradle.GradleModuleParser
import java.nio.file.Paths
import org.junit.Test

class GradleModuleParsingTest {
  private val runfiles = Paths.get(System.getenv("JAVA_RUNFILES")!!)
  private val workspace = runfiles.resolve(Paths.get(System.getenv("TEST_WORKSPACE")!!))
  private val here = workspace.resolve(Paths.get(System.getenv("TEST_BINARY")!!).parent)

  @Test fun parseOkio260() {
    val parser = GradleModuleParser()
    val model = parser.parse(here.resolve("data/okio-2.6.0.module"))
    requireNotNull(model)
    assertThat(model.formatVersion).isEqualTo("1.1")
  }

  @Test fun parseV1_0() {
    val parser = GradleModuleParser()
    val model = parser.parse(here.resolve("data/gradle-module-spec-1.0-example.module"))
    requireNotNull(model)
    assertThat(model.formatVersion).isEqualTo("1.0")
  }

  @Test fun parseV1_1() {
    val parser = GradleModuleParser()
    val model = parser.parse(here.resolve("data/gradle-module-spec-1.1-example.module"))
    requireNotNull(model)
    assertThat(model.formatVersion).isEqualTo("1.1")
  }

  @Test fun parseExtraAttributes() {
    val parser = GradleModuleParser()
    val model = parser.parse(here.resolve("data/extra-attributes.module"))
    requireNotNull(model)
    assertThat(model.formatVersion).isEqualTo("1.1")
    assertThat(model.variants[0].attributes["org.gradle.usage"]).isEqualTo("java-api")
    assertThat(model.variants[0].attributes["foo.bar.baz"]).isEqualTo("blah")
  }

  @Test fun parseExampleFromGradleCodebase() {
    val parser = GradleModuleParser()
    val model = parser.parse(here.resolve("data/gradle-example-java-library.module"))
    requireNotNull(model)
    assertThat(model.formatVersion).isEqualTo("1.0")
    assertThat(model.variants[0].attributes["org.gradle.usage"]).isEqualTo("java-runtime")
  }
}
