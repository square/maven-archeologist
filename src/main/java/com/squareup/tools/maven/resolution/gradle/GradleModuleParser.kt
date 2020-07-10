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

import com.squareup.moshi.JsonReader
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.squareup.tools.maven.resolution.gradle.Module.ModuleV1_1
import java.io.IOException
import java.nio.file.Path
import java.nio.file.StandardOpenOption.READ
import okio.Source
import okio.buffer
import okio.source

class GradleModuleParser(
  val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
) {

  @Throws(IOException::class)
  fun parse(json: Path): Module? = parse(json.source(READ))

  @Throws(IOException::class)
  fun parse(json: Source): Module? {
    json.use { source ->
      val reader = JsonReader.of(source.buffer())
      // TODO(cgruber) handle module format versions by peeking the format.
      // For now, just pipe them all to 1.1, since 1.0 is backward compatible.
      // val peeker = reader.peekJson()
      val adapter = moshi.adapter(ModuleV1_1::class.java).failOnUnknown()
      return adapter.fromJson(reader)
    }
  }
}
