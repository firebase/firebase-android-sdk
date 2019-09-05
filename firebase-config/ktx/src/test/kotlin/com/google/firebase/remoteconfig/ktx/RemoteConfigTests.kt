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

package com.google.firebase.remoteconfig.ktx

import com.google.common.truth.Truth.assertThat
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue
import com.google.firebase.remoteconfig.createRemoteConfig
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.internal.ConfigCacheClient
import com.google.firebase.remoteconfig.internal.ConfigFetchHandler
import com.google.firebase.remoteconfig.internal.ConfigGetParameterHandler
import com.google.firebase.remoteconfig.internal.ConfigMetadataClient
import com.google.common.util.concurrent.MoreExecutors
import com.google.firebase.ktx.app
import com.google.firebase.ktx.initialize
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

const val APP_ID = "APP_ID"
const val API_KEY = "API_KEY"

const val EXISTING_APP = "existing"

open class DefaultFirebaseRemoteConfigValue : FirebaseRemoteConfigValue {
    override fun asLong(): Long = TODO("Unimplementend")
    override fun asDouble(): Double = TODO("Unimplementend")
    override fun asString(): String = TODO("Unimplementend")
    override fun asByteArray(): ByteArray = TODO("Unimplementend")
    override fun asBoolean(): Boolean = TODO("Unimplementend")
    override fun getSource(): Int = TODO("Unimplementend")
}

class StringRemoteConfigValue(val value: String) : DefaultFirebaseRemoteConfigValue() {
    override fun asString() = value
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
class ConfigTests : BaseTestCase() {

    @Test
    fun `Firebase#remoteConfig should delegate to FirebaseRemoteConfig#getInstance()`() {
        assertThat(Firebase.remoteConfig).isSameInstanceAs(FirebaseRemoteConfig.getInstance())
    }

    @Test
    fun `Firebase#remoteConfig should delegate to FirebaseRemoteConfig#getInstance(FirebaseApp, region)`() {
        val app = Firebase.app(EXISTING_APP)
        assertThat(Firebase.remoteConfig(app)).isSameInstanceAs(FirebaseRemoteConfig.getInstance(app))
    }

    @Test
    fun `Overloaded get() operator returns default value when key doesn't exist`() {
        val remoteConfig = Firebase.remoteConfig
        assertThat(remoteConfig["non_existing_key"].asString())
                .isEqualTo(FirebaseRemoteConfig.DEFAULT_VALUE_FOR_STRING)
        assertThat(remoteConfig["another_non_exisiting_key"].asDouble())
                .isEqualTo(FirebaseRemoteConfig.DEFAULT_VALUE_FOR_DOUBLE)
    }

    @Test
    fun `Overloaded get() operator returns value when key exists`() {
        val mockGetHandler = mock(ConfigGetParameterHandler::class.java)
        val directExecutor = MoreExecutors.directExecutor()

        val remoteConfig = createRemoteConfig(
            context = null,
            firebaseApp = Firebase.app(EXISTING_APP),
            firebaseAbt = null,
            executor = directExecutor,
            fetchedConfigsCache = mock(ConfigCacheClient::class.java),
            activatedConfigsCache = mock(ConfigCacheClient::class.java),
            defaultConfigsCache = mock(ConfigCacheClient::class.java),
            fetchHandler = mock(ConfigFetchHandler::class.java),
            getHandler = mockGetHandler,
            frcMetadata = mock(ConfigMetadataClient::class.java))

        `when`(mockGetHandler.getValue("KEY")).thenReturn(StringRemoteConfigValue("non default value"))
        assertThat(remoteConfig["KEY"].asString()).isEqualTo("non default value")
    }
}
