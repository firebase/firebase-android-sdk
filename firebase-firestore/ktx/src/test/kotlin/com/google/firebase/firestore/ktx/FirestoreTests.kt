// Copyright 2019 Google LLC
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

package com.google.firebase.firestore.ktx

import com.google.common.truth.Truth.assertThat
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.TestUtil
import com.google.firebase.firestore.model.ObjectValue
import com.google.firebase.firestore.testutil.TestUtil.wrap
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.app
import com.google.firebase.ktx.initialize
import com.google.firebase.platforminfo.UserAgentPublisher
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

const val APP_ID = "APP_ID"
const val API_KEY = "API_KEY"

const val EXISTING_APP = "existing"

abstract class BaseTestCase {
    @Before
    fun setUp() {
        Firebase.initialize(
                RuntimeEnvironment.application,
                FirebaseOptions.Builder()
                        .setApplicationId(APP_ID)
                        .setApiKey(API_KEY)
                        .setProjectId("123")
                        .build()
        )

        Firebase.initialize(
                RuntimeEnvironment.application,
                FirebaseOptions.Builder()
                        .setApplicationId(APP_ID)
                        .setApiKey(API_KEY)
                        .setProjectId("123")
                        .build(),
                EXISTING_APP
        )
    }

    @After
    fun cleanUp() {
        FirebaseApp.clearInstancesForTest()
    }
}

@RunWith(RobolectricTestRunner::class)
class FirestoreTests : BaseTestCase() {

    @Test
    fun `firestore should delegate to FirebaseFirestore#getInstance()`() {
        assertThat(Firebase.firestore).isSameInstanceAs(FirebaseFirestore.getInstance())
    }

    @Test
    fun `FirebaseApp#firestore should delegate to FirebaseFirestore#getInstance(FirebaseApp)`() {
        val app = Firebase.app(EXISTING_APP)
        assertThat(Firebase.firestore(app)).isSameInstanceAs(FirebaseFirestore.getInstance(app))
    }

    @Test
    fun `FirebaseFirestoreSettings builder works`() {
        val host = "http://10.0.2.2:8080"
        val isSslEnabled = false
        val isPersistenceEnabled = false

        val settings = firestoreSettings {
            this.host = host
            this.isSslEnabled = isSslEnabled
            this.isPersistenceEnabled = isPersistenceEnabled
        }

        assertThat(host).isEqualTo(settings.host)
        assertThat(isSslEnabled).isEqualTo(settings.isSslEnabled)
        assertThat(isPersistenceEnabled).isEqualTo(settings.isPersistenceEnabled)
    }
}

@RunWith(RobolectricTestRunner::class)
class LibraryVersionTest : BaseTestCase() {
    @Test
    fun `library version should be registered with runtime`() {
        val publisher = Firebase.app.get(UserAgentPublisher::class.java)
        assertThat(publisher.userAgent).contains(LIBRARY_NAME)
    }
}

data class Room(var a: Int = 0, var b: Int = 0)

@RunWith(RobolectricTestRunner::class)
class DocumentSnapshotTests {
    @Before
    fun setup() {
        Mockito.`when`(TestUtil.firestore().firestoreSettings).thenReturn(FirebaseFirestoreSettings.Builder().build())
    }

    @After
    fun cleanup() {
        Mockito.reset(TestUtil.firestore())
    }

    @Test
    fun `reified getField delegates to get()`() {
        val ds = TestUtil.documentSnapshot("rooms/foo", mapOf("a" to 1, "b" to 2), false)

        assertThat(ds.getField<Int>("a")).isEqualTo(ds.get("a", Int::class.java))
        assertThat(ds.getField<Int>(FieldPath.of("a")))
                .isEqualTo(ds.get(FieldPath.of("a"), Int::class.java))

        assertThat(ds.getField<Int>("a", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE))
                .isEqualTo(ds.get("a", Int::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE))
        assertThat(ds.getField<Int>(FieldPath.of("a"), DocumentSnapshot.ServerTimestampBehavior.ESTIMATE))
                .isEqualTo(ds.get(FieldPath.of("a"), Int::class.java))
    }

    @Test
    fun `reified toObject delegates to toObject(Class)`() {
        val ds = TestUtil.documentSnapshot("rooms/foo", mapOf("a" to 1, "b" to 2), false)

        var room = ds.toObject<Room>()
        assertThat(room).isEqualTo(Room(1, 2))
        assertThat(room).isEqualTo(ds.toObject(Room::class.java))

        room = ds.toObject<Room>(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        assertThat(room).isEqualTo(Room(1, 2))
        assertThat(room)
                .isEqualTo(ds.toObject(Room::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE))
    }
}

@RunWith(RobolectricTestRunner::class)
class QuerySnapshotTests {
    @Before
    fun setup() {
        Mockito.`when`(TestUtil.firestore().firestoreSettings).thenReturn(FirebaseFirestoreSettings.Builder().build())
    }

    @After
    fun cleanup() {
        Mockito.reset(TestUtil.firestore())
    }

    @Test
    fun `reified toObjects delegates to toObjects(Class)`() {
        val qs = TestUtil.querySnapshot(
                "rooms",
                mapOf(),
                mapOf("id" to ObjectValue.fromMap(mapOf("a" to wrap(1), "b" to wrap(2)))),
                false,
                false)

        assertThat(qs.toObjects<Room>()).containsExactly(Room(1, 2))
        assertThat(qs.toObjects<Room>(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)).containsExactly(Room(1, 2))
    }
}
