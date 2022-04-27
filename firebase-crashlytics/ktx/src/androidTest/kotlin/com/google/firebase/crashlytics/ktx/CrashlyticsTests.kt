// Copyright 2020 Google LLC
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

package com.google.firebase.crashlytics.ktx

import androidx.test.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.app
import com.google.firebase.ktx.initialize
import com.google.firebase.platforminfo.UserAgentPublisher
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CrashlyticsTests {
    companion object {
        lateinit var app: FirebaseApp

        @BeforeClass @JvmStatic fun setup() {
            app = Firebase.initialize(InstrumentationRegistry.getContext())!!
        }

        @AfterClass @JvmStatic fun cleanup() {
            app.delete()
        }
    }

    @Test
    fun firebaseCrashlyticsDelegates() {
        assertThat(Firebase.crashlytics).isSameInstanceAs(FirebaseCrashlytics.getInstance())
    }

    @Test
    fun testDataCall() {
        assertThat("hola").isEqualTo("hola")
    }
}

@RunWith(AndroidJUnit4::class)
class LibraryVersionTest {
    companion object {
        lateinit var app: FirebaseApp

        @BeforeClass @JvmStatic fun setup() {
            app = Firebase.initialize(InstrumentationRegistry.getContext())!!
        }

        @AfterClass @JvmStatic fun cleanup() {
            app.delete()
        }
    }

    @Test
    fun libraryRegistrationAtRuntime() {
        val publisher = Firebase.app.get(UserAgentPublisher::class.java)
        assertThat(publisher.userAgent).contains(LIBRARY_NAME)
    }
}
