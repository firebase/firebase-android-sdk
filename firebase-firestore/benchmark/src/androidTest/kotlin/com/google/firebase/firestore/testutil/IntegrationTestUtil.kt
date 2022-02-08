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

package com.google.firebase.firestore.testutil

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.AccessHelper
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestore.InstanceRegistry
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.R
import com.google.firebase.firestore.auth.CredentialsProvider
import com.google.firebase.firestore.auth.User
import com.google.firebase.firestore.benchmark.BuildConfig
import com.google.firebase.firestore.model.DatabaseId
import com.google.firebase.firestore.util.AsyncQueue
import com.google.firebase.firestore.util.Listener
import java.lang.RuntimeException
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** A set of helper methods for tests  */
object IntegrationTestUtil {
    // Whether the integration tests should run against a local Firestore emulator instead of the
    // Production environment. Note that the Android Emulator treats "10.0.2.2" as its host machine.
    private const val isRunningAgainstEmulator: Boolean = BuildConfig.USE_EMULATOR_FOR_TESTS
    private const val EMULATOR_HOST: String = "10.0.2.2"
    private const val EMULATOR_PORT: Int = 8080

    /** Default amount of time to wait for a given operation to complete, used by waitFor() helper.  */
    private const val OPERATION_WAIT_TIMEOUT_MS: Long = 30000

    private fun newTestSettings(): FirebaseFirestoreSettings {
        val settings: FirebaseFirestoreSettings.Builder = FirebaseFirestoreSettings.Builder()
        if (isRunningAgainstEmulator) {
            settings.host = String.format("%s:%d", EMULATOR_HOST, EMULATOR_PORT)
            settings.isSslEnabled = false
        }
        return settings.build()
    }

    /**
     * Initializes a new Firestore instance that uses the default project, customized with the
     * provided settings.
     */
    fun testFirestore(settings: FirebaseFirestoreSettings? = newTestSettings()): FirebaseFirestore {
        val persistenceKey: String = "db" + UUID.randomUUID()
        val context: Context = ApplicationProvider.getApplicationContext()
        val databaseId = DatabaseId.forDatabase(getProjectId(), DatabaseId.DEFAULT_DATABASE_ID)
        val firestore = AccessHelper.newFirebaseFirestore(
                context,
                databaseId,
                persistenceKey,
                EmptyAuthCredentialsProvider(),
                EmptyAppCheckTokenProvider(),
                AsyncQueue(),
                InstanceRegistry { }
        )
        waitFor<Void>(firestore.clearPersistence())
        firestore.firestoreSettings = settings!!
        return firestore
    }

    private fun getProjectId(): String {
        val context: Context = ApplicationProvider.getApplicationContext()
        return context.getString(R.string.project_id)
    }

    fun <T> waitFor(task: Task<T>): T {
        return waitFor(task, OPERATION_WAIT_TIMEOUT_MS)
    }

    fun <T> waitFor(task: Task<T>, timeoutMS: Long): T {
        return try {
            Tasks.await<T>(task, timeoutMS, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            throw RuntimeException(e)
        } catch (e: ExecutionException) {
            throw RuntimeException(e)
        } catch (e: InterruptedException) {
            throw RuntimeException(e)
        }
    }
}

/** A Credentials Provider that always returns an empty token  */
class EmptyAppCheckTokenProvider : CredentialsProvider<String>() {
    override fun getToken(): Task<String> {
        return Tasks.forResult("")
    }
    override fun removeChangeListener() {}
    override fun setChangeListener(changeListener: Listener<String>) {}
    override fun invalidateToken() {}
}

open class EmptyAuthCredentialsProvider : CredentialsProvider<User>() {
    override fun getToken(): Task<String> {
        return Tasks.forResult(null)
    }

    override fun invalidateToken() {}
    override fun setChangeListener(changeListener: Listener<User>) {
        changeListener.onValue(User.UNAUTHENTICATED)
    }

    override fun removeChangeListener() {}
}
