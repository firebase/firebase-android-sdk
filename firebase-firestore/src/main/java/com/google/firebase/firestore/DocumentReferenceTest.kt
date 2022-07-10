package com.google.firebase.firestore

import com.google.firebase.firestore.model.DocumentKey
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.serialization.Serializable

class DocumentReferenceTest internal constructor(key: DocumentKey?, firestore: FirebaseFirestore?) :
    DocumentReference(key, firestore)