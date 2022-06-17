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
 * Overwrites the document referred to by this {@code DocumentReference}. If the document does not
 * yet exist, it will be created. If a document already exists, it will be overwritten.
 *
 * @param data The data to write to the document (the data must be a @Serializable Kotlin object).
 * @return A Task that will be resolved when the write finishes.
 */
inline fun <reified T> DocumentReference.setData(data: T): Task<Void> {
    val encodedMap = encodeToMap<T>(data)
    return this.set(encodedMap)
}
