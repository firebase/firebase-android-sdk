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


package com.google.firebase.firestore.ktx.testutil

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.model.DocumentKey
import kotlin.reflect.jvm.isAccessible

/**
 * Returns a [DocumentReference] identified by document name for ktx unit test.
 * @param pathString A slash separated path for navigating resources (documents and collections) within Firestore.
 */
fun documentReference(pathString: String): DocumentReference {
    val reflectedDocumentRefClass = DocumentReference::class
    val documentKey = DocumentKey.fromPathString(pathString)
    return reflectedDocumentRefClass.constructors.first().run {
        isAccessible = true
        call(documentKey, null)
    }
}
