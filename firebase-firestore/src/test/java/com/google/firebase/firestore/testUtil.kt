// Copyright 2025 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.firestore

import com.google.firebase.firestore.model.MutableDocument
import com.google.firebase.firestore.pipeline.EvaluationContext
import kotlinx.coroutines.flow.Flow

internal fun runPipeline(
  pipeline: RealtimePipeline,
  input: Flow<MutableDocument>
): Flow<MutableDocument> {
  val rewrittenPipeline = pipeline.rewriteStages()
  val context = EvaluationContext(rewrittenPipeline)
  return rewrittenPipeline.stages.fold(input) { documentFlow, stage ->
    stage.evaluate(context, documentFlow)
  }
}
