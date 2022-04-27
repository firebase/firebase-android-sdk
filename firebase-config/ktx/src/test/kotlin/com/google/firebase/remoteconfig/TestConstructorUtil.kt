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

package com.google.firebase.remoteconfig

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.abt.FirebaseABTesting
import com.google.firebase.installations.FirebaseInstallationsApi
import com.google.firebase.remoteconfig.internal.ConfigCacheClient
import com.google.firebase.remoteconfig.internal.ConfigFetchHandler
import com.google.firebase.remoteconfig.internal.ConfigGetParameterHandler
import com.google.firebase.remoteconfig.internal.ConfigMetadataClient
import java.util.concurrent.Executor

// This method is a workaround for testing. It enable us to create a FirebaseRemoteConfig object
// with mocks using the package-private constructor.
fun createRemoteConfig(
    context: Context?,
    firebaseApp: FirebaseApp,
    firebaseInstallations: FirebaseInstallationsApi,
    firebaseAbt: FirebaseABTesting?,
    executor: Executor,
    fetchedConfigsCache: ConfigCacheClient,
    activatedConfigsCache: ConfigCacheClient,
    defaultConfigsCache: ConfigCacheClient,
    fetchHandler: ConfigFetchHandler,
    getHandler: ConfigGetParameterHandler,
    frcMetadata: ConfigMetadataClient
): FirebaseRemoteConfig {
        return FirebaseRemoteConfig(
            context,
            firebaseApp,
            firebaseInstallations,
            firebaseAbt,
            executor,
            fetchedConfigsCache,
            activatedConfigsCache,
            defaultConfigsCache,
            fetchHandler,
            getHandler,
            frcMetadata
    )
}
