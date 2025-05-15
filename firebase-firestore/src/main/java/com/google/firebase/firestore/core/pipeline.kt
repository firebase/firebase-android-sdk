package com.google.firebase.firestore.core

import com.google.firebase.firestore.AbstractPipeline
import com.google.firebase.firestore.model.MutableDocument
import com.google.firebase.firestore.pipeline.EvaluationContext
import kotlinx.coroutines.flow.Flow

internal fun runPipeline(
  pipeline: AbstractPipeline,
  input: Flow<MutableDocument>
): Flow<MutableDocument> {
  val context = EvaluationContext(pipeline.userDataReader)
  return pipeline.stages.fold(input) { documentFlow, stage ->
    stage.evaluate(context, documentFlow)
  }
}
