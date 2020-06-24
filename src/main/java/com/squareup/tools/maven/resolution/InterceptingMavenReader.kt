package com.squareup.tools.maven.resolution

import java.io.File
import java.io.InputStream
import java.io.Reader
import org.apache.maven.model.Model
import org.apache.maven.model.io.DefaultModelReader
import org.apache.maven.model.io.ModelReader

class InterceptingMavenReader(
  private val delegate: ModelReader = DefaultModelReader(),
  private val interceptor: (Model) -> Model
) : ModelReader by delegate {
  // DefaultModelReader delegates to read(InputStream, Map)
  override fun read(input: File, options: MutableMap<String, *>?) = delegate.read(input, options)

  override fun read(input: InputStream, options: MutableMap<String, *>): Model {
    val model = delegate.read(input, options)!!
    return interceptor.invoke(model)
  }

  override fun read(input: Reader, options: MutableMap<String, *>?): Model {
    val model = delegate.read(input, options)!!
    return interceptor.invoke(model)
  }
}
