package com.google.firebase.firestore

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FirebaseFirestore.InstanceRegistry
import com.google.firebase.firestore.auth.CredentialsProvider
import com.google.firebase.firestore.auth.User
import com.google.firebase.firestore.model.DatabaseId
import com.google.firebase.firestore.util.AsyncQueue

object AccessHelper {
    /** Makes the FirebaseFirestore constructor accessible.  */
    fun newFirebaseFirestore(
        context: Context,
        databaseId: DatabaseId,
        persistenceKey: String,
        authProvider: CredentialsProvider<User>,
        appCheckProvider: CredentialsProvider<String>,
        asyncQueue: AsyncQueue,
        instanceRegistry: InstanceRegistry
    ): FirebaseFirestore {
        return FirebaseFirestore(
                context,
                databaseId,
                persistenceKey,
                authProvider,
                appCheckProvider,
                asyncQueue,
                null,
                instanceRegistry,
                null)
    }

    fun setIndexConfiguration(db: FirebaseFirestore, json: String) {
        db.setIndexConfiguration(json)
    }

    fun forceBackfill(db: FirebaseFirestore): Task<Void> {
        return db.forceBackfill()
    }
}
