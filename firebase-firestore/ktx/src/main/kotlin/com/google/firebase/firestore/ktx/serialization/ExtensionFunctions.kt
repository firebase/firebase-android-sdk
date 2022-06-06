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
