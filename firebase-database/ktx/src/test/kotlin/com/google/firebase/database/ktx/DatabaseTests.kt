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

package com.google.firebase.database.ktx

import com.google.common.truth.Truth.assertThat
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.database.Exclude
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.IgnoreExtraProperties
import com.google.firebase.database.createDataSnapshot
import com.google.firebase.database.createMutableData
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.app
import com.google.firebase.ktx.initialize
import com.google.firebase.platforminfo.UserAgentPublisher
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

const val APP_ID = "APP_ID"
const val API_KEY = "API_KEY"

const val EXISTING_APP = "existing"

@IgnoreExtraProperties
data class Player(
    var name: String? = "",
    var jersey: Int? = -1,
    var goalkeeper: Boolean? = false,
    var avg_goals_per_game: Double? = 0.0
) {
    @Exclude
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "name" to name,
            "jersey" to jersey,
            "goalkeeper" to goalkeeper,
            "avg_goals_per_game" to avg_goals_per_game
        )
    }
}

abstract class BaseTestCase {
    @Before
    fun setUp() {
        Firebase.initialize(
                RuntimeEnvironment.application,
                FirebaseOptions.Builder()
                        .setApplicationId(APP_ID)
                        .setApiKey(API_KEY)
                        .setProjectId("123")
                        .setDatabaseUrl("http://tests.fblocal.com:9000")
                        .build()
        )

        Firebase.initialize(
                RuntimeEnvironment.application,
                FirebaseOptions.Builder()
                        .setApplicationId(APP_ID)
                        .setApiKey(API_KEY)
                        .setProjectId("123")
                        .setDatabaseUrl("http://tests.fblocal.com:9000")
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
class DatabaseTests : BaseTestCase() {
    @Test
    fun `database should delegate to FirebaseDatabase#getInstance()`() {
        assertThat(Firebase.database).isSameInstanceAs(FirebaseDatabase.getInstance())
    }

    @Test
    fun `FirebaseApp#database should delegate to FirebaseDatabase#getInstance(FirebaseApp)`() {
        val app = Firebase.app(EXISTING_APP)
        assertThat(Firebase.database(app)).isSameInstanceAs(FirebaseDatabase.getInstance(app))
    }

    @Test
    fun `Firebase#database should delegate to FirebaseDatabase#getInstance(url)`() {
        val url = "http://tests.fblocal.com:9000"
        assertThat(Firebase.database(url)).isSameInstanceAs(FirebaseDatabase.getInstance(url))
    }

    @Test
    fun `Firebase#database should delegate to FirebaseDatabase#getInstance(FirebaseApp, url)`() {
        val app = Firebase.app(EXISTING_APP)
        val url = "http://tests.fblocal.com:9000"
        assertThat(Firebase.database(app, url)).isSameInstanceAs(FirebaseDatabase.getInstance(app, url))
    }
}

@RunWith(RobolectricTestRunner::class)
class DataSnapshotTests : BaseTestCase() {
    @Test
    fun `reified getValue works with basic types`() {
        val data = mapOf(
            "name" to "John Doe",
            "jersey" to 35L,
            "goalkeeper" to false,
            "avg_goals_per_game" to 0.35
        )
        val dataSnapshot = createDataSnapshot(data, Firebase.database)
        assertThat(dataSnapshot.child("name").getValue<String>()).isEqualTo("John Doe")
        assertThat(dataSnapshot.child("jersey").getValue<Long>()).isEqualTo(35L)
        assertThat(dataSnapshot.child("goalkeeper").getValue<Boolean>()).isEqualTo(false)
        assertThat(dataSnapshot.child("avg_goals_per_game").getValue<Double>()).isEqualTo(0.35)
    }

    @Test
    fun `reified getValue works with maps`() {
        val data = mapOf(
            "name" to "John Doe",
            "jersey" to 35L,
            "goalkeeper" to false,
            "avg_goals_per_game" to 0.35
        )
        val dataSnapshot = createDataSnapshot(data, Firebase.database)
        assertThat(dataSnapshot.getValue<Map<String, Any>>()).isEqualTo(data)
    }

    @Test
    fun `reified getValue works with lists types`() {
        val data = listOf(
            "George",
            "John",
            "Paul",
            "Ringo"
        )
        val dataSnapshot = createDataSnapshot(data, Firebase.database)
        assertThat(dataSnapshot.getValue<List<String>>()).isEqualTo(data)
    }

    @Test
    fun `reified getValue works with custom types`() {
        val data = Player(
            name = "John Doe",
            jersey = 35,
            goalkeeper = false,
            avg_goals_per_game = 0.35
        )
        val dataSnapshot = createDataSnapshot(data.toMap(), Firebase.database)
        assertThat(dataSnapshot.getValue<Player>()).isEqualTo(data)
    }
}

@RunWith(RobolectricTestRunner::class)
class MutableDataTests : BaseTestCase() {
    @Test
    fun `reified getValue works with basic types`() {
        val data = mapOf(
                "name" to "John Doe",
                "jersey" to 35L,
                "goalkeeper" to false,
                "avg_goals_per_game" to 0.35
        )
        val mutableData = createMutableData(data)

        assertThat(mutableData.child("name").getValue<String>()).isEqualTo("John Doe")
        assertThat(mutableData.child("jersey").getValue<Long>()).isEqualTo(35L)
        assertThat(mutableData.child("goalkeeper").getValue<Boolean>()).isEqualTo(false)
        assertThat(mutableData.child("avg_goals_per_game").getValue<Double>()).isEqualTo(0.35)
    }

    @Test
    fun `reified getValue works with maps`() {
        val data = mapOf(
                "name" to "John Doe",
                "jersey" to 35L,
                "goalkeeper" to false,
                "avg_goals_per_game" to 0.35
        )
        val mutableData = createMutableData(data)
        assertThat(mutableData.getValue<Map<String, Any>>()).isEqualTo(data)
    }

    @Test
    fun `reified getValue works with lists types`() {
        val data = listOf(
                "George",
                "John",
                "Paul",
                "Ringo"
        )
        val mutableData = createMutableData(data)
        assertThat(mutableData.getValue<List<String>>()).isEqualTo(data)
    }

    @Test
    fun `reified getValue works with custom types`() {
        val data = Player(
                name = "John Doe",
                jersey = 35,
                goalkeeper = false,
                avg_goals_per_game = 0.35
        )
        val mutableData = createMutableData(data.toMap())
        assertThat(mutableData.getValue<Player>()).isEqualTo(data)
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
