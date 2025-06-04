package com.google.firebase.firestore

import com.google.firebase.firestore.model.MutableDocument
import com.google.firebase.firestore.pipeline.EvaluationContext
import kotlinx.coroutines.flow.Flow

internal fun runPipeline(
  db: FirebaseFirestore,
  pipeline: AbstractPipeline,
  input: Flow<MutableDocument>
): Flow<MutableDocument> {
  val context = EvaluationContext(db, db.userDataReader)
  return pipeline.stages.fold(input) { documentFlow, stage ->
    stage.evaluate(context, documentFlow)
  }
}
