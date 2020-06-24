package com.squareup.tools.maven.resolution

import org.apache.maven.model.Model
import org.apache.maven.model.building.DefaultModelProblem
import org.apache.maven.model.building.ModelBuildingException
import org.apache.maven.model.building.ModelBuildingRequest
import org.apache.maven.model.building.ModelBuildingResult
import org.apache.maven.model.building.ModelProblem
import org.apache.maven.model.building.ModelProblemCollector
import org.apache.maven.model.building.ModelProblemCollectorRequest
import org.apache.maven.model.io.ModelParseException
import org.apache.maven.model.validation.ModelValidator

object Validator {
  @Throws(ModelBuildingException::class)
  internal fun validateResult(
    result: ModelBuildingResult,
    validator: ModelValidator,
    req: ModelBuildingRequest
  ): ModelBuildingResult {
    val model = result.effectiveModel
    val problems: MutableList<ModelProblem> = mutableListOf()
    val collector = ModelProblemCollector { request -> problems.add(request.toProblem(model)) }
    validator.validateEffectiveModel(model, req, collector)
    return result
  }
}

fun ModelProblemCollectorRequest.toProblem(model: Model): ModelProblem {
  var line = -1
  var column = -1
  var source: String? = null
  var modelId: String? = null

  location?.let { it ->
    line = it.lineNumber
    column = it.columnNumber
    it.source?.let { inputSource ->
      modelId = inputSource.modelId
      source = inputSource.location
    }
  }

  if (modelId == null) {
    modelId = model.coordinate
    source = model.pomFile?.absolutePath ?: ""
  }

  with(exception) {
    if (line <= 0 && column <= 0 && this is ModelParseException) {
      line = this.lineNumber
      column = this.columnNumber
    }
  }

  return DefaultModelProblem(message, severity, version, source, line, column, modelId, exception)
}

class NoopEffectiveModelValidator(delegate: ModelValidator) : ModelValidator by delegate {
  override fun validateEffectiveModel(a: Model, b: ModelBuildingRequest, c: ModelProblemCollector) {
    // noop
  }
}
