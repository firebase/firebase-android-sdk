/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:Suppress(
  "KotlinRedundantDiagnosticSuppress",
  "LocalVariableName",
  "RedundantVisibilityModifier",
  "RemoveEmptyClassBody",
  "SpellCheckingInspection",
  "LocalVariableName",
  "unused",
)

package com.google.firebase.dataconnect.connectors.`typealias`

import com.google.firebase.FirebaseApp
import com.google.firebase.dataconnect.ConnectorConfig
import com.google.firebase.dataconnect.DataConnectSettings
import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.generated.GeneratedConnector
import com.google.firebase.dataconnect.getInstance
import java.util.WeakHashMap

public interface KeywordsConnector : GeneratedConnector {
  override val dataConnect: FirebaseDataConnect

  public val deleteFoo: DeleteFooMutation

  public val `do`: DoMutation

  public val getFoosByBar: GetFoosByBarQuery

  public val getTwoFoosById: GetTwoFoosByIdQuery

  public val insertTwoFoos: InsertTwoFoosMutation

  public val `return`: ReturnQuery

  public companion object {
    @Suppress("MemberVisibilityCanBePrivate")
    public val config: ConnectorConfig =
      ConnectorConfig(
        connector = "keywords",
        location = "us-central1",
        serviceId = "sid2ehn9ct8te",
      )

    public fun getInstance(dataConnect: FirebaseDataConnect): KeywordsConnector =
      synchronized(instances) {
        instances.getOrPut(dataConnect) { KeywordsConnectorImpl(dataConnect) }
      }

    private val instances = WeakHashMap<FirebaseDataConnect, KeywordsConnectorImpl>()
  }
}

public val KeywordsConnector.Companion.instance: KeywordsConnector
  get() = getInstance(FirebaseDataConnect.getInstance(config))

public fun KeywordsConnector.Companion.getInstance(
  settings: DataConnectSettings = DataConnectSettings()
): KeywordsConnector = getInstance(FirebaseDataConnect.getInstance(config, settings))

public fun KeywordsConnector.Companion.getInstance(
  app: FirebaseApp,
  settings: DataConnectSettings = DataConnectSettings()
): KeywordsConnector = getInstance(FirebaseDataConnect.getInstance(app, config, settings))

private class KeywordsConnectorImpl(override val dataConnect: FirebaseDataConnect) :
  KeywordsConnector {

  override val deleteFoo by lazy(LazyThreadSafetyMode.PUBLICATION) { DeleteFooMutationImpl(this) }

  override val `do` by lazy(LazyThreadSafetyMode.PUBLICATION) { DoMutationImpl(this) }

  override val getFoosByBar by
    lazy(LazyThreadSafetyMode.PUBLICATION) { GetFoosByBarQueryImpl(this) }

  override val getTwoFoosById by
    lazy(LazyThreadSafetyMode.PUBLICATION) { GetTwoFoosByIdQueryImpl(this) }

  override val insertTwoFoos by
    lazy(LazyThreadSafetyMode.PUBLICATION) { InsertTwoFoosMutationImpl(this) }

  override val `return` by lazy(LazyThreadSafetyMode.PUBLICATION) { ReturnQueryImpl(this) }

  override fun toString() = "KeywordsConnectorImpl(dataConnect=$dataConnect)"
}

private class DeleteFooMutationImpl(override val connector: KeywordsConnectorImpl) :
  DeleteFooMutation {
  override val operationName by DeleteFooMutation.Companion::operationName
  override val dataDeserializer by DeleteFooMutation.Companion::dataDeserializer
  override val variablesSerializer by DeleteFooMutation.Companion::variablesSerializer

  override fun toString() =
    "DeleteFooMutationImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class DoMutationImpl(override val connector: KeywordsConnectorImpl) : DoMutation {
  override val operationName by DoMutation.Companion::operationName
  override val dataDeserializer by DoMutation.Companion::dataDeserializer
  override val variablesSerializer by DoMutation.Companion::variablesSerializer

  override fun toString() =
    "DoMutationImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class GetFoosByBarQueryImpl(override val connector: KeywordsConnectorImpl) :
  GetFoosByBarQuery {
  override val operationName by GetFoosByBarQuery.Companion::operationName
  override val dataDeserializer by GetFoosByBarQuery.Companion::dataDeserializer
  override val variablesSerializer by GetFoosByBarQuery.Companion::variablesSerializer

  override fun toString() =
    "GetFoosByBarQueryImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class GetTwoFoosByIdQueryImpl(override val connector: KeywordsConnectorImpl) :
  GetTwoFoosByIdQuery {
  override val operationName by GetTwoFoosByIdQuery.Companion::operationName
  override val dataDeserializer by GetTwoFoosByIdQuery.Companion::dataDeserializer
  override val variablesSerializer by GetTwoFoosByIdQuery.Companion::variablesSerializer

  override fun toString() =
    "GetTwoFoosByIdQueryImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class InsertTwoFoosMutationImpl(override val connector: KeywordsConnectorImpl) :
  InsertTwoFoosMutation {
  override val operationName by InsertTwoFoosMutation.Companion::operationName
  override val dataDeserializer by InsertTwoFoosMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertTwoFoosMutation.Companion::variablesSerializer

  override fun toString() =
    "InsertTwoFoosMutationImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class ReturnQueryImpl(override val connector: KeywordsConnectorImpl) : ReturnQuery {
  override val operationName by ReturnQuery.Companion::operationName
  override val dataDeserializer by ReturnQuery.Companion::dataDeserializer
  override val variablesSerializer by ReturnQuery.Companion::variablesSerializer

  override fun toString() =
    "ReturnQueryImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}


// The lines below are used by the code generator to ensure that this file is deleted if it is no
// longer needed. Any files in this directory that contain the lines below will be deleted by the
// code generator if the file is no longer needed. If, for some reason, you do _not_ want the code
// generator to delete this file, then remove the line below (and this comment too, if you want).


// FIREBASE_DATA_CONNECT_GENERATED_FILE MARKER 42da5e14-69b3-401b-a9f1-e407bee89a78
// FIREBASE_DATA_CONNECT_GENERATED_FILE CONNECTOR keywords
