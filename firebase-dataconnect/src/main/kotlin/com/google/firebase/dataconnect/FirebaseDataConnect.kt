// Copyright 2023 Google LLC
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
package com.google.firebase.dataconnect

import android.annotation.SuppressLint
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.app
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy

public interface FirebaseDataConnect : AutoCloseable {
  public val app: FirebaseApp
  public val config: ConnectorConfig
  public val settings: DataConnectSettings

  public fun useEmulator(host: String = "10.0.2.2", port: Int = 9510)

  public fun <Data, Variables> query(
    operationName: String,
    variables: Variables,
    dataDeserializer: DeserializationStrategy<Data>,
    variablesSerializer: SerializationStrategy<Variables>,
  ): QueryRef<Data, Variables>

  public fun <Data, Variables> mutation(
    operationName: String,
    variables: Variables,
    dataDeserializer: DeserializationStrategy<Data>,
    variablesSerializer: SerializationStrategy<Variables>,
  ): MutationRef<Data, Variables>

  public companion object
}

@SuppressLint("FirebaseUseExplicitDependencies")
public fun FirebaseDataConnect.Companion.getInstance(
  app: FirebaseApp,
  config: ConnectorConfig,
  settings: DataConnectSettings? = null,
): FirebaseDataConnect =
  app.get(FirebaseDataConnectFactory::class.java).run { get(config = config, settings = settings) }

public fun FirebaseDataConnect.Companion.getInstance(
  config: ConnectorConfig,
  settings: DataConnectSettings? = null
): FirebaseDataConnect = getInstance(app = Firebase.app, config = config, settings = settings)
