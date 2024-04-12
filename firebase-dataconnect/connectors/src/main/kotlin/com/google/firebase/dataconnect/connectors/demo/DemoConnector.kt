@file:Suppress("SpellCheckingInspection")

package com.google.firebase.dataconnect.connectors.demo

import com.google.firebase.FirebaseApp
import com.google.firebase.dataconnect.ConnectorConfig
import com.google.firebase.dataconnect.DataConnectSettings
import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.generated.GeneratedConnector
import com.google.firebase.dataconnect.getInstance
import java.util.WeakHashMap

public interface DemoConnector : GeneratedConnector {
  override val dataConnect: FirebaseDataConnect

  public val deleteFoo: DeleteFooMutation

  public val deleteFoosByBar: DeleteFoosByBarMutation

  public val getDateVariantsById: GetDateVariantsByIdQuery

  public val getFooById: GetFooByIdQuery

  public val getFoosByBar: GetFoosByBarQuery

  public val getHardcodedFoo: GetHardcodedFooQuery

  public val getInt64variantsById: GetInt64variantsByIdQuery

  public val getStringVariantsById: GetStringVariantsByIdQuery

  public val getUuidvariantsById: GetUuidvariantsByIdQuery

  public val insertDateVariants: InsertDateVariantsMutation

  public val insertFoo: InsertFooMutation

  public val insertInt64variants: InsertInt64variantsMutation

  public val insertStringVariants: InsertStringVariantsMutation

  public val insertUuidvariants: InsertUuidvariantsMutation

  public val updateFoo: UpdateFooMutation

  public val updateFoosByBar: UpdateFoosByBarMutation

  public val upsertFoo: UpsertFooMutation

  public val upsertHardcodedFoo: UpsertHardcodedFooMutation

  public companion object {
    @Suppress("MemberVisibilityCanBePrivate")
    public val config: ConnectorConfig =
      ConnectorConfig(
        connector = "demo",
        location = "us-central1",
        serviceId = "local",
      )

    public fun getInstance(dataConnect: FirebaseDataConnect): DemoConnector =
      synchronized(instances) { instances.getOrPut(dataConnect) { DemoConnectorImpl(dataConnect) } }

    private val instances = WeakHashMap<FirebaseDataConnect, DemoConnectorImpl>()
  }
}

public val DemoConnector.Companion.instance: DemoConnector
  get() = getInstance(FirebaseDataConnect.getInstance(config))

public fun DemoConnector.Companion.getInstance(
  settings: DataConnectSettings = DataConnectSettings()
): DemoConnector = getInstance(FirebaseDataConnect.getInstance(config, settings))

public fun DemoConnector.Companion.getInstance(
  app: FirebaseApp,
  settings: DataConnectSettings = DataConnectSettings()
): DemoConnector = getInstance(FirebaseDataConnect.getInstance(app, config, settings))

private class DemoConnectorImpl(override val dataConnect: FirebaseDataConnect) : DemoConnector {

  override val deleteFoo by lazy(LazyThreadSafetyMode.PUBLICATION) { DeleteFooMutationImpl(this) }

  override val deleteFoosByBar by
    lazy(LazyThreadSafetyMode.PUBLICATION) { DeleteFoosByBarMutationImpl(this) }

  override val getDateVariantsById by
    lazy(LazyThreadSafetyMode.PUBLICATION) { GetDateVariantsByIdQueryImpl(this) }

  override val getFooById by lazy(LazyThreadSafetyMode.PUBLICATION) { GetFooByIdQueryImpl(this) }

  override val getFoosByBar by
    lazy(LazyThreadSafetyMode.PUBLICATION) { GetFoosByBarQueryImpl(this) }

  override val getHardcodedFoo by
    lazy(LazyThreadSafetyMode.PUBLICATION) { GetHardcodedFooQueryImpl(this) }

  override val getInt64variantsById by
    lazy(LazyThreadSafetyMode.PUBLICATION) { GetInt64variantsByIdQueryImpl(this) }

  override val getStringVariantsById by
    lazy(LazyThreadSafetyMode.PUBLICATION) { GetStringVariantsByIdQueryImpl(this) }

  override val getUuidvariantsById by
    lazy(LazyThreadSafetyMode.PUBLICATION) { GetUuidvariantsByIdQueryImpl(this) }

  override val insertDateVariants by
    lazy(LazyThreadSafetyMode.PUBLICATION) { InsertDateVariantsMutationImpl(this) }

  override val insertFoo by lazy(LazyThreadSafetyMode.PUBLICATION) { InsertFooMutationImpl(this) }

  override val insertInt64variants by
    lazy(LazyThreadSafetyMode.PUBLICATION) { InsertInt64variantsMutationImpl(this) }

  override val insertStringVariants by
    lazy(LazyThreadSafetyMode.PUBLICATION) { InsertStringVariantsMutationImpl(this) }

  override val insertUuidvariants by
    lazy(LazyThreadSafetyMode.PUBLICATION) { InsertUuidvariantsMutationImpl(this) }

  override val updateFoo by lazy(LazyThreadSafetyMode.PUBLICATION) { UpdateFooMutationImpl(this) }

  override val updateFoosByBar by
    lazy(LazyThreadSafetyMode.PUBLICATION) { UpdateFoosByBarMutationImpl(this) }

  override val upsertFoo by lazy(LazyThreadSafetyMode.PUBLICATION) { UpsertFooMutationImpl(this) }

  override val upsertHardcodedFoo by
    lazy(LazyThreadSafetyMode.PUBLICATION) { UpsertHardcodedFooMutationImpl(this) }

  override fun toString() = "DemoConnectorImpl(dataConnect=$dataConnect)"
}

private class DeleteFooMutationImpl(override val connector: DemoConnectorImpl) : DeleteFooMutation {
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

private class DeleteFoosByBarMutationImpl(override val connector: DemoConnectorImpl) :
  DeleteFoosByBarMutation {
  override val operationName by DeleteFoosByBarMutation.Companion::operationName
  override val dataDeserializer by DeleteFoosByBarMutation.Companion::dataDeserializer
  override val variablesSerializer by DeleteFoosByBarMutation.Companion::variablesSerializer

  override fun toString() =
    "DeleteFoosByBarMutationImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class GetDateVariantsByIdQueryImpl(override val connector: DemoConnectorImpl) :
  GetDateVariantsByIdQuery {
  override val operationName by GetDateVariantsByIdQuery.Companion::operationName
  override val dataDeserializer by GetDateVariantsByIdQuery.Companion::dataDeserializer
  override val variablesSerializer by GetDateVariantsByIdQuery.Companion::variablesSerializer

  override fun toString() =
    "GetDateVariantsByIdQueryImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class GetFooByIdQueryImpl(override val connector: DemoConnectorImpl) : GetFooByIdQuery {
  override val operationName by GetFooByIdQuery.Companion::operationName
  override val dataDeserializer by GetFooByIdQuery.Companion::dataDeserializer
  override val variablesSerializer by GetFooByIdQuery.Companion::variablesSerializer

  override fun toString() =
    "GetFooByIdQueryImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class GetFoosByBarQueryImpl(override val connector: DemoConnectorImpl) : GetFoosByBarQuery {
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

private class GetHardcodedFooQueryImpl(override val connector: DemoConnectorImpl) :
  GetHardcodedFooQuery {
  override val operationName by GetHardcodedFooQuery.Companion::operationName
  override val dataDeserializer by GetHardcodedFooQuery.Companion::dataDeserializer
  override val variablesSerializer by GetHardcodedFooQuery.Companion::variablesSerializer

  override fun toString() =
    "GetHardcodedFooQueryImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class GetInt64variantsByIdQueryImpl(override val connector: DemoConnectorImpl) :
  GetInt64variantsByIdQuery {
  override val operationName by GetInt64variantsByIdQuery.Companion::operationName
  override val dataDeserializer by GetInt64variantsByIdQuery.Companion::dataDeserializer
  override val variablesSerializer by GetInt64variantsByIdQuery.Companion::variablesSerializer

  override fun toString() =
    "GetInt64variantsByIdQueryImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class GetStringVariantsByIdQueryImpl(override val connector: DemoConnectorImpl) :
  GetStringVariantsByIdQuery {
  override val operationName by GetStringVariantsByIdQuery.Companion::operationName
  override val dataDeserializer by GetStringVariantsByIdQuery.Companion::dataDeserializer
  override val variablesSerializer by GetStringVariantsByIdQuery.Companion::variablesSerializer

  override fun toString() =
    "GetStringVariantsByIdQueryImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class GetUuidvariantsByIdQueryImpl(override val connector: DemoConnectorImpl) :
  GetUuidvariantsByIdQuery {
  override val operationName by GetUuidvariantsByIdQuery.Companion::operationName
  override val dataDeserializer by GetUuidvariantsByIdQuery.Companion::dataDeserializer
  override val variablesSerializer by GetUuidvariantsByIdQuery.Companion::variablesSerializer

  override fun toString() =
    "GetUuidvariantsByIdQueryImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class InsertDateVariantsMutationImpl(override val connector: DemoConnectorImpl) :
  InsertDateVariantsMutation {
  override val operationName by InsertDateVariantsMutation.Companion::operationName
  override val dataDeserializer by InsertDateVariantsMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertDateVariantsMutation.Companion::variablesSerializer

  override fun toString() =
    "InsertDateVariantsMutationImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class InsertFooMutationImpl(override val connector: DemoConnectorImpl) : InsertFooMutation {
  override val operationName by InsertFooMutation.Companion::operationName
  override val dataDeserializer by InsertFooMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertFooMutation.Companion::variablesSerializer

  override fun toString() =
    "InsertFooMutationImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class InsertInt64variantsMutationImpl(override val connector: DemoConnectorImpl) :
  InsertInt64variantsMutation {
  override val operationName by InsertInt64variantsMutation.Companion::operationName
  override val dataDeserializer by InsertInt64variantsMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertInt64variantsMutation.Companion::variablesSerializer

  override fun toString() =
    "InsertInt64variantsMutationImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class InsertStringVariantsMutationImpl(override val connector: DemoConnectorImpl) :
  InsertStringVariantsMutation {
  override val operationName by InsertStringVariantsMutation.Companion::operationName
  override val dataDeserializer by InsertStringVariantsMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertStringVariantsMutation.Companion::variablesSerializer

  override fun toString() =
    "InsertStringVariantsMutationImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class InsertUuidvariantsMutationImpl(override val connector: DemoConnectorImpl) :
  InsertUuidvariantsMutation {
  override val operationName by InsertUuidvariantsMutation.Companion::operationName
  override val dataDeserializer by InsertUuidvariantsMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertUuidvariantsMutation.Companion::variablesSerializer

  override fun toString() =
    "InsertUuidvariantsMutationImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class UpdateFooMutationImpl(override val connector: DemoConnectorImpl) : UpdateFooMutation {
  override val operationName by UpdateFooMutation.Companion::operationName
  override val dataDeserializer by UpdateFooMutation.Companion::dataDeserializer
  override val variablesSerializer by UpdateFooMutation.Companion::variablesSerializer

  override fun toString() =
    "UpdateFooMutationImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class UpdateFoosByBarMutationImpl(override val connector: DemoConnectorImpl) :
  UpdateFoosByBarMutation {
  override val operationName by UpdateFoosByBarMutation.Companion::operationName
  override val dataDeserializer by UpdateFoosByBarMutation.Companion::dataDeserializer
  override val variablesSerializer by UpdateFoosByBarMutation.Companion::variablesSerializer

  override fun toString() =
    "UpdateFoosByBarMutationImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class UpsertFooMutationImpl(override val connector: DemoConnectorImpl) : UpsertFooMutation {
  override val operationName by UpsertFooMutation.Companion::operationName
  override val dataDeserializer by UpsertFooMutation.Companion::dataDeserializer
  override val variablesSerializer by UpsertFooMutation.Companion::variablesSerializer

  override fun toString() =
    "UpsertFooMutationImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class UpsertHardcodedFooMutationImpl(override val connector: DemoConnectorImpl) :
  UpsertHardcodedFooMutation {
  override val operationName by UpsertHardcodedFooMutation.Companion::operationName
  override val dataDeserializer by UpsertHardcodedFooMutation.Companion::dataDeserializer
  override val variablesSerializer by UpsertHardcodedFooMutation.Companion::variablesSerializer

  override fun toString() =
    "UpsertHardcodedFooMutationImpl(" +
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
// FIREBASE_DATA_CONNECT_GENERATED_FILE CONNECTOR demo
