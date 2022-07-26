// Copyright 2022 Google LLC
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

package com.google.firebase.firestore.ktx.serialization

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentReference

/**
 * Converts a @[Serializable] object to a map of Firestore supported types, and sets the map to a
 * [DocumentReference] for integration tests.
 *
 * @param data The @[Serializable] object.
 * @return A Task that will be resolved when the write finishes.
 */
// TODO: This extension function need to be removed after merge the component registrar in.
inline fun <reified T : Any> DocumentReference.setData(data: T): Task<Void> {
    val encodedMap = encodeToMap(data)
    return this.set(encodedMap)
}
