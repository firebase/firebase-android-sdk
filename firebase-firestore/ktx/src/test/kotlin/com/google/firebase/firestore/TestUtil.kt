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

package com.google.firebase.firestore

import com.google.common.truth.ThrowableSubject
import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.model.DocumentKey
import org.junit.Assert.assertThrows

/**
 * Returns a [DocumentReference] identified by document name for ktx unit test purpose. The
 * firestore in this DocumentReference is always null.
 *
 * @param pathString A slash separated path represents the location of a document in the Firestore
 * database.
 * @return The [DocumentReference] identified by the slash separated path.
 */
fun documentReference(pathString: String): DocumentReference {
    val documentKey = DocumentKey.fromPathString(pathString)
    return DocumentReference(documentKey, null)
}

/**
 * Asserts that [runnable] throws an exception of type [T] when executed. If it does not throw an
 * exception, or if it throws the wrong type of exception, an [AssertionError] is thrown describing
 * the mismatch; If it throws the correct type of exception, a [ThrowableSubject] of
 * assertThat(exception) is returned, this return value can be used for the following Truth
 * assertion tests.
 *
 * @param T The expected type of the exception.
 * @param runnable A function that is expected to throw an exception when executed.
 * @return A throwableSubject that can be used for the following Truth assertion checks.
 */
inline fun <reified T : Exception> assertThrows(
    crossinline runnable: () -> Any?
): ThrowableSubject {
    val exception: T = assertThrows(T::class.java) { runnable() }
    return assertThat(exception)
}
