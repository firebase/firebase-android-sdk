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

package com.google.firebase.firestore.pipeline.evaluation.logical

import com.google.firebase.firestore.pipeline.BooleanExpression
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.field
import com.google.firebase.firestore.pipeline.Expression.Companion.nullValue

/**
 * Returns a [BooleanExpression] that always evaluates to `null`.
 *
 * This is intended for testing purposes.
 */
fun nullBoolean(): BooleanExpression = nullValue().asBoolean()

/**
 * Returns a [BooleanExpression] that always evaluates to a string.
 *
 * This is intended for testing purposes.
 */
fun stringBoolean(): BooleanExpression = constant("foo").asBoolean()

/**
 * Returns a [BooleanExpression] that always evaluates to "unset".
 *
 * This is intended for testing purposes.
 */
fun unsetBoolean(): BooleanExpression = field("not-existent").asBoolean()
