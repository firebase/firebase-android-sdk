@file:Suppress("SpellCheckingInspection")

package com.google.firebase.dataconnect.connectors.demo

import com.google.firebase.FirebaseApp
import com.google.firebase.dataconnect.ConnectorConfig
import com.google.firebase.dataconnect.DataConnectSettings
import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.getInstance
import java.util.WeakHashMap

public interface DemoConnector {
  public val dataConnect: FirebaseDataConnect

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
  override fun toString() = "DeleteFooMutationImpl(connector=$connector)"
}

private class DeleteFoosByBarMutationImpl(override val connector: DemoConnectorImpl) :
  DeleteFoosByBarMutation {
  override fun toString() = "DeleteFoosByBarMutationImpl(connector=$connector)"
}

private class GetDateVariantsByIdQueryImpl(override val connector: DemoConnectorImpl) :
  GetDateVariantsByIdQuery {
  override fun toString() = "GetDateVariantsByIdQueryImpl(connector=$connector)"
}

private class GetFooByIdQueryImpl(override val connector: DemoConnectorImpl) : GetFooByIdQuery {
  override fun toString() = "GetFooByIdQueryImpl(connector=$connector)"
}

private class GetFoosByBarQueryImpl(override val connector: DemoConnectorImpl) : GetFoosByBarQuery {
  override fun toString() = "GetFoosByBarQueryImpl(connector=$connector)"
}

private class GetHardcodedFooQueryImpl(override val connector: DemoConnectorImpl) :
  GetHardcodedFooQuery {
  override fun toString() = "GetHardcodedFooQueryImpl(connector=$connector)"
}

private class GetInt64variantsByIdQueryImpl(override val connector: DemoConnectorImpl) :
  GetInt64variantsByIdQuery {
  override fun toString() = "GetInt64variantsByIdQueryImpl(connector=$connector)"
}

private class GetStringVariantsByIdQueryImpl(override val connector: DemoConnectorImpl) :
  GetStringVariantsByIdQuery {
  override fun toString() = "GetStringVariantsByIdQueryImpl(connector=$connector)"
}

private class GetUuidvariantsByIdQueryImpl(override val connector: DemoConnectorImpl) :
  GetUuidvariantsByIdQuery {
  override fun toString() = "GetUuidvariantsByIdQueryImpl(connector=$connector)"
}

private class InsertDateVariantsMutationImpl(override val connector: DemoConnectorImpl) :
  InsertDateVariantsMutation {
  override fun toString() = "InsertDateVariantsMutationImpl(connector=$connector)"
}

private class InsertFooMutationImpl(override val connector: DemoConnectorImpl) : InsertFooMutation {
  override fun toString() = "InsertFooMutationImpl(connector=$connector)"
}

private class InsertInt64variantsMutationImpl(override val connector: DemoConnectorImpl) :
  InsertInt64variantsMutation {
  override fun toString() = "InsertInt64variantsMutationImpl(connector=$connector)"
}

private class InsertStringVariantsMutationImpl(override val connector: DemoConnectorImpl) :
  InsertStringVariantsMutation {
  override fun toString() = "InsertStringVariantsMutationImpl(connector=$connector)"
}

private class InsertUuidvariantsMutationImpl(override val connector: DemoConnectorImpl) :
  InsertUuidvariantsMutation {
  override fun toString() = "InsertUuidvariantsMutationImpl(connector=$connector)"
}

private class UpdateFooMutationImpl(override val connector: DemoConnectorImpl) : UpdateFooMutation {
  override fun toString() = "UpdateFooMutationImpl(connector=$connector)"
}

private class UpdateFoosByBarMutationImpl(override val connector: DemoConnectorImpl) :
  UpdateFoosByBarMutation {
  override fun toString() = "UpdateFoosByBarMutationImpl(connector=$connector)"
}

private class UpsertFooMutationImpl(override val connector: DemoConnectorImpl) : UpsertFooMutation {
  override fun toString() = "UpsertFooMutationImpl(connector=$connector)"
}

private class UpsertHardcodedFooMutationImpl(override val connector: DemoConnectorImpl) :
  UpsertHardcodedFooMutation {
  override fun toString() = "UpsertHardcodedFooMutationImpl(connector=$connector)"
}

// The lines below are used by the code generator to ensure that this file is deleted if it is no
// longer needed. Any files in this directory that contain the lines below will be deleted by the
// code generator if the file is no longer needed. If, for some reason, you do _not_ want the code
// generator to delete this file, then remove the line below (and this comment too, if you want).

// FIREBASE_DATA_CONNECT_GENERATED_FILE MARKER 42da5e14-69b3-401b-a9f1-e407bee89a78
// FIREBASE_DATA_CONNECT_GENERATED_FILE CONNECTOR demo
