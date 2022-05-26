package com.example.firestore_kotlin_serialization.testutil

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class IntegrationTestUtil {

    companion object {
        // constants
        private const val OPERATION_WAIT_TIMEOUT_MS: Long = 30000

        private const val AUTO_ID_LENGTH = 20

        private const val AUTO_ID_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

        private val rand: Random = SecureRandom()

        // Emulator must have local persistence storage enabled
        var settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()

        val testFirestore: FirebaseFirestore by lazy {
            FirebaseFirestore.getInstance().apply {
                this.useEmulator("10.0.2.2", 8080)
                this.firestoreSettings = settings
            }
        }

        // static methods
        fun <T> waitFor(task: Task<T>, timeoutMS: Long): T {
            return try {
                Tasks.await(task, timeoutMS, TimeUnit.MILLISECONDS)
            } catch (e: TimeoutException) {
                throw RuntimeException(e)
            } catch (e: ExecutionException) {
                throw RuntimeException(e)
            } catch (e: InterruptedException) {
                throw RuntimeException(e)
            }
        }

        fun <T> waitFor(task: Task<T>): T {
            return waitFor(task, OPERATION_WAIT_TIMEOUT_MS)
        }

        private fun autoId(): String {
            val builder = StringBuilder()
            val maxRandom = AUTO_ID_ALPHABET.length
            for (i in 0 until AUTO_ID_LENGTH) {
                builder.append(AUTO_ID_ALPHABET[rand.nextInt(maxRandom)])
            }
            return builder.toString()
        }

        fun testCollection(name: String): CollectionReference {
            return testFirestore.collection(name + "_" + autoId())
        }

        fun testDocument(name: String): DocumentReference {
            return testCollection("test-collection").document(name)
        }

        fun testDocumentSnapshot(): DocumentSnapshot {
            return waitFor(testCollection("test-document").document("dummy").get())
        }
    }
}
