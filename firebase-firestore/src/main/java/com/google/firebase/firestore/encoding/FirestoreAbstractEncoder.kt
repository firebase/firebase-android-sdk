package com.google.firebase.firestore.encoding

import com.google.firebase.firestore.JavaDocumentReference
import com.google.firebase.firestore.JavaGeoPoint
import kotlinx.serialization.encoding.Encoder

interface FirestoreAbstractEncoder : Encoder {

    fun encodeGeoPoint(value: JavaGeoPoint)

    fun encodeDocumentReference(value: JavaDocumentReference)
}
