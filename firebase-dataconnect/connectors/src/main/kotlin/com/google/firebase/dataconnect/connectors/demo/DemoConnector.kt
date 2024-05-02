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

  public val getManyToManyChildAbyKey: GetManyToManyChildAbyKeyQuery

  public val getManyToManySelfChildByKey: GetManyToManySelfChildByKeyQuery

  public val getManyToOneChildByKey: GetManyToOneChildByKeyQuery

  public val getManyToOneSelfCustomNameByKey: GetManyToOneSelfCustomNameByKeyQuery

  public val getManyToOneSelfMatchingNameByKey: GetManyToOneSelfMatchingNameByKeyQuery

  public val getNested1byKey: GetNested1byKeyQuery

  public val getOptionalStringsByKey: GetOptionalStringsByKeyQuery

  public val getPrimaryKeyIsCompositeByKey: GetPrimaryKeyIsCompositeByKeyQuery

  public val getPrimaryKeyIsDateByKey: GetPrimaryKeyIsDateByKeyQuery

  public val getPrimaryKeyIsFloatByKey: GetPrimaryKeyIsFloatByKeyQuery

  public val getPrimaryKeyIsInt64byKey: GetPrimaryKeyIsInt64byKeyQuery

  public val getPrimaryKeyIsIntByKey: GetPrimaryKeyIsIntByKeyQuery

  public val getPrimaryKeyIsStringByKey: GetPrimaryKeyIsStringByKeyQuery

  public val getPrimaryKeyIsTimestampByKey: GetPrimaryKeyIsTimestampByKeyQuery

  public val getPrimaryKeyIsUuidbyKey: GetPrimaryKeyIsUuidbyKeyQuery

  public val getPrimaryKeyNested7byKey: GetPrimaryKeyNested7byKeyQuery

  public val getStringVariantsById: GetStringVariantsByIdQuery

  public val getSyntheticIdById: GetSyntheticIdByIdQuery

  public val getTimestampVariantsById: GetTimestampVariantsByIdQuery

  public val getUuidvariantsById: GetUuidvariantsByIdQuery

  public val insertDateVariants: InsertDateVariantsMutation

  public val insertFoo: InsertFooMutation

  public val insertInt64variants: InsertInt64variantsMutation

  public val insertManyToManyChildA: InsertManyToManyChildAMutation

  public val insertManyToManyChildB: InsertManyToManyChildBMutation

  public val insertManyToManyParent: InsertManyToManyParentMutation

  public val insertManyToManySelfChild: InsertManyToManySelfChildMutation

  public val insertManyToManySelfParent: InsertManyToManySelfParentMutation

  public val insertManyToOneChild: InsertManyToOneChildMutation

  public val insertManyToOneParent: InsertManyToOneParentMutation

  public val insertManyToOneSelfCustomName: InsertManyToOneSelfCustomNameMutation

  public val insertManyToOneSelfMatchingName: InsertManyToOneSelfMatchingNameMutation

  public val insertNested1: InsertNested1Mutation

  public val insertNested2: InsertNested2Mutation

  public val insertNested3: InsertNested3Mutation

  public val insertOptionalStrings: InsertOptionalStringsMutation

  public val insertPrimaryKeyIsComposite: InsertPrimaryKeyIsCompositeMutation

  public val insertPrimaryKeyIsDate: InsertPrimaryKeyIsDateMutation

  public val insertPrimaryKeyIsFloat: InsertPrimaryKeyIsFloatMutation

  public val insertPrimaryKeyIsInt: InsertPrimaryKeyIsIntMutation

  public val insertPrimaryKeyIsInt64: InsertPrimaryKeyIsInt64Mutation

  public val insertPrimaryKeyIsString: InsertPrimaryKeyIsStringMutation

  public val insertPrimaryKeyIsTimestamp: InsertPrimaryKeyIsTimestampMutation

  public val insertPrimaryKeyIsUuid: InsertPrimaryKeyIsUuidMutation

  public val insertPrimaryKeyNested1: InsertPrimaryKeyNested1Mutation

  public val insertPrimaryKeyNested2: InsertPrimaryKeyNested2Mutation

  public val insertPrimaryKeyNested3: InsertPrimaryKeyNested3Mutation

  public val insertPrimaryKeyNested4: InsertPrimaryKeyNested4Mutation

  public val insertPrimaryKeyNested5: InsertPrimaryKeyNested5Mutation

  public val insertPrimaryKeyNested6: InsertPrimaryKeyNested6Mutation

  public val insertPrimaryKeyNested7: InsertPrimaryKeyNested7Mutation

  public val insertStringVariants: InsertStringVariantsMutation

  public val insertSyntheticId: InsertSyntheticIdMutation

  public val insertTimestampVariants: InsertTimestampVariantsMutation

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

  override val getManyToManyChildAbyKey by
    lazy(LazyThreadSafetyMode.PUBLICATION) { GetManyToManyChildAbyKeyQueryImpl(this) }

  override val getManyToManySelfChildByKey by
    lazy(LazyThreadSafetyMode.PUBLICATION) { GetManyToManySelfChildByKeyQueryImpl(this) }

  override val getManyToOneChildByKey by
    lazy(LazyThreadSafetyMode.PUBLICATION) { GetManyToOneChildByKeyQueryImpl(this) }

  override val getManyToOneSelfCustomNameByKey by
    lazy(LazyThreadSafetyMode.PUBLICATION) { GetManyToOneSelfCustomNameByKeyQueryImpl(this) }

  override val getManyToOneSelfMatchingNameByKey by
    lazy(LazyThreadSafetyMode.PUBLICATION) { GetManyToOneSelfMatchingNameByKeyQueryImpl(this) }

  override val getNested1byKey by
    lazy(LazyThreadSafetyMode.PUBLICATION) { GetNested1byKeyQueryImpl(this) }

  override val getOptionalStringsByKey by
    lazy(LazyThreadSafetyMode.PUBLICATION) { GetOptionalStringsByKeyQueryImpl(this) }

  override val getPrimaryKeyIsCompositeByKey by
    lazy(LazyThreadSafetyMode.PUBLICATION) { GetPrimaryKeyIsCompositeByKeyQueryImpl(this) }

  override val getPrimaryKeyIsDateByKey by
    lazy(LazyThreadSafetyMode.PUBLICATION) { GetPrimaryKeyIsDateByKeyQueryImpl(this) }

  override val getPrimaryKeyIsFloatByKey by
    lazy(LazyThreadSafetyMode.PUBLICATION) { GetPrimaryKeyIsFloatByKeyQueryImpl(this) }

  override val getPrimaryKeyIsInt64byKey by
    lazy(LazyThreadSafetyMode.PUBLICATION) { GetPrimaryKeyIsInt64byKeyQueryImpl(this) }

  override val getPrimaryKeyIsIntByKey by
    lazy(LazyThreadSafetyMode.PUBLICATION) { GetPrimaryKeyIsIntByKeyQueryImpl(this) }

  override val getPrimaryKeyIsStringByKey by
    lazy(LazyThreadSafetyMode.PUBLICATION) { GetPrimaryKeyIsStringByKeyQueryImpl(this) }

  override val getPrimaryKeyIsTimestampByKey by
    lazy(LazyThreadSafetyMode.PUBLICATION) { GetPrimaryKeyIsTimestampByKeyQueryImpl(this) }

  override val getPrimaryKeyIsUuidbyKey by
    lazy(LazyThreadSafetyMode.PUBLICATION) { GetPrimaryKeyIsUuidbyKeyQueryImpl(this) }

  override val getPrimaryKeyNested7byKey by
    lazy(LazyThreadSafetyMode.PUBLICATION) { GetPrimaryKeyNested7byKeyQueryImpl(this) }

  override val getStringVariantsById by
    lazy(LazyThreadSafetyMode.PUBLICATION) { GetStringVariantsByIdQueryImpl(this) }

  override val getSyntheticIdById by
    lazy(LazyThreadSafetyMode.PUBLICATION) { GetSyntheticIdByIdQueryImpl(this) }

  override val getTimestampVariantsById by
    lazy(LazyThreadSafetyMode.PUBLICATION) { GetTimestampVariantsByIdQueryImpl(this) }

  override val getUuidvariantsById by
    lazy(LazyThreadSafetyMode.PUBLICATION) { GetUuidvariantsByIdQueryImpl(this) }

  override val insertDateVariants by
    lazy(LazyThreadSafetyMode.PUBLICATION) { InsertDateVariantsMutationImpl(this) }

  override val insertFoo by lazy(LazyThreadSafetyMode.PUBLICATION) { InsertFooMutationImpl(this) }

  override val insertInt64variants by
    lazy(LazyThreadSafetyMode.PUBLICATION) { InsertInt64variantsMutationImpl(this) }

  override val insertManyToManyChildA by
    lazy(LazyThreadSafetyMode.PUBLICATION) { InsertManyToManyChildAMutationImpl(this) }

  override val insertManyToManyChildB by
    lazy(LazyThreadSafetyMode.PUBLICATION) { InsertManyToManyChildBMutationImpl(this) }

  override val insertManyToManyParent by
    lazy(LazyThreadSafetyMode.PUBLICATION) { InsertManyToManyParentMutationImpl(this) }

  override val insertManyToManySelfChild by
    lazy(LazyThreadSafetyMode.PUBLICATION) { InsertManyToManySelfChildMutationImpl(this) }

  override val insertManyToManySelfParent by
    lazy(LazyThreadSafetyMode.PUBLICATION) { InsertManyToManySelfParentMutationImpl(this) }

  override val insertManyToOneChild by
    lazy(LazyThreadSafetyMode.PUBLICATION) { InsertManyToOneChildMutationImpl(this) }

  override val insertManyToOneParent by
    lazy(LazyThreadSafetyMode.PUBLICATION) { InsertManyToOneParentMutationImpl(this) }

  override val insertManyToOneSelfCustomName by
    lazy(LazyThreadSafetyMode.PUBLICATION) { InsertManyToOneSelfCustomNameMutationImpl(this) }

  override val insertManyToOneSelfMatchingName by
    lazy(LazyThreadSafetyMode.PUBLICATION) { InsertManyToOneSelfMatchingNameMutationImpl(this) }

  override val insertNested1 by
    lazy(LazyThreadSafetyMode.PUBLICATION) { InsertNested1MutationImpl(this) }

  override val insertNested2 by
    lazy(LazyThreadSafetyMode.PUBLICATION) { InsertNested2MutationImpl(this) }

  override val insertNested3 by
    lazy(LazyThreadSafetyMode.PUBLICATION) { InsertNested3MutationImpl(this) }

  override val insertOptionalStrings by
    lazy(LazyThreadSafetyMode.PUBLICATION) { InsertOptionalStringsMutationImpl(this) }

  override val insertPrimaryKeyIsComposite by
    lazy(LazyThreadSafetyMode.PUBLICATION) { InsertPrimaryKeyIsCompositeMutationImpl(this) }

  override val insertPrimaryKeyIsDate by
    lazy(LazyThreadSafetyMode.PUBLICATION) { InsertPrimaryKeyIsDateMutationImpl(this) }

  override val insertPrimaryKeyIsFloat by
    lazy(LazyThreadSafetyMode.PUBLICATION) { InsertPrimaryKeyIsFloatMutationImpl(this) }

  override val insertPrimaryKeyIsInt by
    lazy(LazyThreadSafetyMode.PUBLICATION) { InsertPrimaryKeyIsIntMutationImpl(this) }

  override val insertPrimaryKeyIsInt64 by
    lazy(LazyThreadSafetyMode.PUBLICATION) { InsertPrimaryKeyIsInt64MutationImpl(this) }

  override val insertPrimaryKeyIsString by
    lazy(LazyThreadSafetyMode.PUBLICATION) { InsertPrimaryKeyIsStringMutationImpl(this) }

  override val insertPrimaryKeyIsTimestamp by
    lazy(LazyThreadSafetyMode.PUBLICATION) { InsertPrimaryKeyIsTimestampMutationImpl(this) }

  override val insertPrimaryKeyIsUuid by
    lazy(LazyThreadSafetyMode.PUBLICATION) { InsertPrimaryKeyIsUuidMutationImpl(this) }

  override val insertPrimaryKeyNested1 by
    lazy(LazyThreadSafetyMode.PUBLICATION) { InsertPrimaryKeyNested1MutationImpl(this) }

  override val insertPrimaryKeyNested2 by
    lazy(LazyThreadSafetyMode.PUBLICATION) { InsertPrimaryKeyNested2MutationImpl(this) }

  override val insertPrimaryKeyNested3 by
    lazy(LazyThreadSafetyMode.PUBLICATION) { InsertPrimaryKeyNested3MutationImpl(this) }

  override val insertPrimaryKeyNested4 by
    lazy(LazyThreadSafetyMode.PUBLICATION) { InsertPrimaryKeyNested4MutationImpl(this) }

  override val insertPrimaryKeyNested5 by
    lazy(LazyThreadSafetyMode.PUBLICATION) { InsertPrimaryKeyNested5MutationImpl(this) }

  override val insertPrimaryKeyNested6 by
    lazy(LazyThreadSafetyMode.PUBLICATION) { InsertPrimaryKeyNested6MutationImpl(this) }

  override val insertPrimaryKeyNested7 by
    lazy(LazyThreadSafetyMode.PUBLICATION) { InsertPrimaryKeyNested7MutationImpl(this) }

  override val insertStringVariants by
    lazy(LazyThreadSafetyMode.PUBLICATION) { InsertStringVariantsMutationImpl(this) }

  override val insertSyntheticId by
    lazy(LazyThreadSafetyMode.PUBLICATION) { InsertSyntheticIdMutationImpl(this) }

  override val insertTimestampVariants by
    lazy(LazyThreadSafetyMode.PUBLICATION) { InsertTimestampVariantsMutationImpl(this) }

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

private class GetManyToManyChildAbyKeyQueryImpl(override val connector: DemoConnectorImpl) :
  GetManyToManyChildAbyKeyQuery {
  override val operationName by GetManyToManyChildAbyKeyQuery.Companion::operationName
  override val dataDeserializer by GetManyToManyChildAbyKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by GetManyToManyChildAbyKeyQuery.Companion::variablesSerializer

  override fun toString() =
    "GetManyToManyChildAbyKeyQueryImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class GetManyToManySelfChildByKeyQueryImpl(override val connector: DemoConnectorImpl) :
  GetManyToManySelfChildByKeyQuery {
  override val operationName by GetManyToManySelfChildByKeyQuery.Companion::operationName
  override val dataDeserializer by GetManyToManySelfChildByKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by
    GetManyToManySelfChildByKeyQuery.Companion::variablesSerializer

  override fun toString() =
    "GetManyToManySelfChildByKeyQueryImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class GetManyToOneChildByKeyQueryImpl(override val connector: DemoConnectorImpl) :
  GetManyToOneChildByKeyQuery {
  override val operationName by GetManyToOneChildByKeyQuery.Companion::operationName
  override val dataDeserializer by GetManyToOneChildByKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by GetManyToOneChildByKeyQuery.Companion::variablesSerializer

  override fun toString() =
    "GetManyToOneChildByKeyQueryImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class GetManyToOneSelfCustomNameByKeyQueryImpl(override val connector: DemoConnectorImpl) :
  GetManyToOneSelfCustomNameByKeyQuery {
  override val operationName by GetManyToOneSelfCustomNameByKeyQuery.Companion::operationName
  override val dataDeserializer by GetManyToOneSelfCustomNameByKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by
    GetManyToOneSelfCustomNameByKeyQuery.Companion::variablesSerializer

  override fun toString() =
    "GetManyToOneSelfCustomNameByKeyQueryImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class GetManyToOneSelfMatchingNameByKeyQueryImpl(
  override val connector: DemoConnectorImpl
) : GetManyToOneSelfMatchingNameByKeyQuery {
  override val operationName by GetManyToOneSelfMatchingNameByKeyQuery.Companion::operationName
  override val dataDeserializer by
    GetManyToOneSelfMatchingNameByKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by
    GetManyToOneSelfMatchingNameByKeyQuery.Companion::variablesSerializer

  override fun toString() =
    "GetManyToOneSelfMatchingNameByKeyQueryImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class GetNested1byKeyQueryImpl(override val connector: DemoConnectorImpl) :
  GetNested1byKeyQuery {
  override val operationName by GetNested1byKeyQuery.Companion::operationName
  override val dataDeserializer by GetNested1byKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by GetNested1byKeyQuery.Companion::variablesSerializer

  override fun toString() =
    "GetNested1byKeyQueryImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class GetOptionalStringsByKeyQueryImpl(override val connector: DemoConnectorImpl) :
  GetOptionalStringsByKeyQuery {
  override val operationName by GetOptionalStringsByKeyQuery.Companion::operationName
  override val dataDeserializer by GetOptionalStringsByKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by GetOptionalStringsByKeyQuery.Companion::variablesSerializer

  override fun toString() =
    "GetOptionalStringsByKeyQueryImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class GetPrimaryKeyIsCompositeByKeyQueryImpl(override val connector: DemoConnectorImpl) :
  GetPrimaryKeyIsCompositeByKeyQuery {
  override val operationName by GetPrimaryKeyIsCompositeByKeyQuery.Companion::operationName
  override val dataDeserializer by GetPrimaryKeyIsCompositeByKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by
    GetPrimaryKeyIsCompositeByKeyQuery.Companion::variablesSerializer

  override fun toString() =
    "GetPrimaryKeyIsCompositeByKeyQueryImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class GetPrimaryKeyIsDateByKeyQueryImpl(override val connector: DemoConnectorImpl) :
  GetPrimaryKeyIsDateByKeyQuery {
  override val operationName by GetPrimaryKeyIsDateByKeyQuery.Companion::operationName
  override val dataDeserializer by GetPrimaryKeyIsDateByKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by GetPrimaryKeyIsDateByKeyQuery.Companion::variablesSerializer

  override fun toString() =
    "GetPrimaryKeyIsDateByKeyQueryImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class GetPrimaryKeyIsFloatByKeyQueryImpl(override val connector: DemoConnectorImpl) :
  GetPrimaryKeyIsFloatByKeyQuery {
  override val operationName by GetPrimaryKeyIsFloatByKeyQuery.Companion::operationName
  override val dataDeserializer by GetPrimaryKeyIsFloatByKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by GetPrimaryKeyIsFloatByKeyQuery.Companion::variablesSerializer

  override fun toString() =
    "GetPrimaryKeyIsFloatByKeyQueryImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class GetPrimaryKeyIsInt64byKeyQueryImpl(override val connector: DemoConnectorImpl) :
  GetPrimaryKeyIsInt64byKeyQuery {
  override val operationName by GetPrimaryKeyIsInt64byKeyQuery.Companion::operationName
  override val dataDeserializer by GetPrimaryKeyIsInt64byKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by GetPrimaryKeyIsInt64byKeyQuery.Companion::variablesSerializer

  override fun toString() =
    "GetPrimaryKeyIsInt64byKeyQueryImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class GetPrimaryKeyIsIntByKeyQueryImpl(override val connector: DemoConnectorImpl) :
  GetPrimaryKeyIsIntByKeyQuery {
  override val operationName by GetPrimaryKeyIsIntByKeyQuery.Companion::operationName
  override val dataDeserializer by GetPrimaryKeyIsIntByKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by GetPrimaryKeyIsIntByKeyQuery.Companion::variablesSerializer

  override fun toString() =
    "GetPrimaryKeyIsIntByKeyQueryImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class GetPrimaryKeyIsStringByKeyQueryImpl(override val connector: DemoConnectorImpl) :
  GetPrimaryKeyIsStringByKeyQuery {
  override val operationName by GetPrimaryKeyIsStringByKeyQuery.Companion::operationName
  override val dataDeserializer by GetPrimaryKeyIsStringByKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by GetPrimaryKeyIsStringByKeyQuery.Companion::variablesSerializer

  override fun toString() =
    "GetPrimaryKeyIsStringByKeyQueryImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class GetPrimaryKeyIsTimestampByKeyQueryImpl(override val connector: DemoConnectorImpl) :
  GetPrimaryKeyIsTimestampByKeyQuery {
  override val operationName by GetPrimaryKeyIsTimestampByKeyQuery.Companion::operationName
  override val dataDeserializer by GetPrimaryKeyIsTimestampByKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by
    GetPrimaryKeyIsTimestampByKeyQuery.Companion::variablesSerializer

  override fun toString() =
    "GetPrimaryKeyIsTimestampByKeyQueryImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class GetPrimaryKeyIsUuidbyKeyQueryImpl(override val connector: DemoConnectorImpl) :
  GetPrimaryKeyIsUuidbyKeyQuery {
  override val operationName by GetPrimaryKeyIsUuidbyKeyQuery.Companion::operationName
  override val dataDeserializer by GetPrimaryKeyIsUuidbyKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by GetPrimaryKeyIsUuidbyKeyQuery.Companion::variablesSerializer

  override fun toString() =
    "GetPrimaryKeyIsUuidbyKeyQueryImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class GetPrimaryKeyNested7byKeyQueryImpl(override val connector: DemoConnectorImpl) :
  GetPrimaryKeyNested7byKeyQuery {
  override val operationName by GetPrimaryKeyNested7byKeyQuery.Companion::operationName
  override val dataDeserializer by GetPrimaryKeyNested7byKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by GetPrimaryKeyNested7byKeyQuery.Companion::variablesSerializer

  override fun toString() =
    "GetPrimaryKeyNested7byKeyQueryImpl(" +
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

private class GetSyntheticIdByIdQueryImpl(override val connector: DemoConnectorImpl) :
  GetSyntheticIdByIdQuery {
  override val operationName by GetSyntheticIdByIdQuery.Companion::operationName
  override val dataDeserializer by GetSyntheticIdByIdQuery.Companion::dataDeserializer
  override val variablesSerializer by GetSyntheticIdByIdQuery.Companion::variablesSerializer

  override fun toString() =
    "GetSyntheticIdByIdQueryImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class GetTimestampVariantsByIdQueryImpl(override val connector: DemoConnectorImpl) :
  GetTimestampVariantsByIdQuery {
  override val operationName by GetTimestampVariantsByIdQuery.Companion::operationName
  override val dataDeserializer by GetTimestampVariantsByIdQuery.Companion::dataDeserializer
  override val variablesSerializer by GetTimestampVariantsByIdQuery.Companion::variablesSerializer

  override fun toString() =
    "GetTimestampVariantsByIdQueryImpl(" +
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

private class InsertManyToManyChildAMutationImpl(override val connector: DemoConnectorImpl) :
  InsertManyToManyChildAMutation {
  override val operationName by InsertManyToManyChildAMutation.Companion::operationName
  override val dataDeserializer by InsertManyToManyChildAMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertManyToManyChildAMutation.Companion::variablesSerializer

  override fun toString() =
    "InsertManyToManyChildAMutationImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class InsertManyToManyChildBMutationImpl(override val connector: DemoConnectorImpl) :
  InsertManyToManyChildBMutation {
  override val operationName by InsertManyToManyChildBMutation.Companion::operationName
  override val dataDeserializer by InsertManyToManyChildBMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertManyToManyChildBMutation.Companion::variablesSerializer

  override fun toString() =
    "InsertManyToManyChildBMutationImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class InsertManyToManyParentMutationImpl(override val connector: DemoConnectorImpl) :
  InsertManyToManyParentMutation {
  override val operationName by InsertManyToManyParentMutation.Companion::operationName
  override val dataDeserializer by InsertManyToManyParentMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertManyToManyParentMutation.Companion::variablesSerializer

  override fun toString() =
    "InsertManyToManyParentMutationImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class InsertManyToManySelfChildMutationImpl(override val connector: DemoConnectorImpl) :
  InsertManyToManySelfChildMutation {
  override val operationName by InsertManyToManySelfChildMutation.Companion::operationName
  override val dataDeserializer by InsertManyToManySelfChildMutation.Companion::dataDeserializer
  override val variablesSerializer by
    InsertManyToManySelfChildMutation.Companion::variablesSerializer

  override fun toString() =
    "InsertManyToManySelfChildMutationImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class InsertManyToManySelfParentMutationImpl(override val connector: DemoConnectorImpl) :
  InsertManyToManySelfParentMutation {
  override val operationName by InsertManyToManySelfParentMutation.Companion::operationName
  override val dataDeserializer by InsertManyToManySelfParentMutation.Companion::dataDeserializer
  override val variablesSerializer by
    InsertManyToManySelfParentMutation.Companion::variablesSerializer

  override fun toString() =
    "InsertManyToManySelfParentMutationImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class InsertManyToOneChildMutationImpl(override val connector: DemoConnectorImpl) :
  InsertManyToOneChildMutation {
  override val operationName by InsertManyToOneChildMutation.Companion::operationName
  override val dataDeserializer by InsertManyToOneChildMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertManyToOneChildMutation.Companion::variablesSerializer

  override fun toString() =
    "InsertManyToOneChildMutationImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class InsertManyToOneParentMutationImpl(override val connector: DemoConnectorImpl) :
  InsertManyToOneParentMutation {
  override val operationName by InsertManyToOneParentMutation.Companion::operationName
  override val dataDeserializer by InsertManyToOneParentMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertManyToOneParentMutation.Companion::variablesSerializer

  override fun toString() =
    "InsertManyToOneParentMutationImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class InsertManyToOneSelfCustomNameMutationImpl(override val connector: DemoConnectorImpl) :
  InsertManyToOneSelfCustomNameMutation {
  override val operationName by InsertManyToOneSelfCustomNameMutation.Companion::operationName
  override val dataDeserializer by InsertManyToOneSelfCustomNameMutation.Companion::dataDeserializer
  override val variablesSerializer by
    InsertManyToOneSelfCustomNameMutation.Companion::variablesSerializer

  override fun toString() =
    "InsertManyToOneSelfCustomNameMutationImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class InsertManyToOneSelfMatchingNameMutationImpl(
  override val connector: DemoConnectorImpl
) : InsertManyToOneSelfMatchingNameMutation {
  override val operationName by InsertManyToOneSelfMatchingNameMutation.Companion::operationName
  override val dataDeserializer by
    InsertManyToOneSelfMatchingNameMutation.Companion::dataDeserializer
  override val variablesSerializer by
    InsertManyToOneSelfMatchingNameMutation.Companion::variablesSerializer

  override fun toString() =
    "InsertManyToOneSelfMatchingNameMutationImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class InsertNested1MutationImpl(override val connector: DemoConnectorImpl) :
  InsertNested1Mutation {
  override val operationName by InsertNested1Mutation.Companion::operationName
  override val dataDeserializer by InsertNested1Mutation.Companion::dataDeserializer
  override val variablesSerializer by InsertNested1Mutation.Companion::variablesSerializer

  override fun toString() =
    "InsertNested1MutationImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class InsertNested2MutationImpl(override val connector: DemoConnectorImpl) :
  InsertNested2Mutation {
  override val operationName by InsertNested2Mutation.Companion::operationName
  override val dataDeserializer by InsertNested2Mutation.Companion::dataDeserializer
  override val variablesSerializer by InsertNested2Mutation.Companion::variablesSerializer

  override fun toString() =
    "InsertNested2MutationImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class InsertNested3MutationImpl(override val connector: DemoConnectorImpl) :
  InsertNested3Mutation {
  override val operationName by InsertNested3Mutation.Companion::operationName
  override val dataDeserializer by InsertNested3Mutation.Companion::dataDeserializer
  override val variablesSerializer by InsertNested3Mutation.Companion::variablesSerializer

  override fun toString() =
    "InsertNested3MutationImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class InsertOptionalStringsMutationImpl(override val connector: DemoConnectorImpl) :
  InsertOptionalStringsMutation {
  override val operationName by InsertOptionalStringsMutation.Companion::operationName
  override val dataDeserializer by InsertOptionalStringsMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertOptionalStringsMutation.Companion::variablesSerializer

  override fun toString() =
    "InsertOptionalStringsMutationImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class InsertPrimaryKeyIsCompositeMutationImpl(override val connector: DemoConnectorImpl) :
  InsertPrimaryKeyIsCompositeMutation {
  override val operationName by InsertPrimaryKeyIsCompositeMutation.Companion::operationName
  override val dataDeserializer by InsertPrimaryKeyIsCompositeMutation.Companion::dataDeserializer
  override val variablesSerializer by
    InsertPrimaryKeyIsCompositeMutation.Companion::variablesSerializer

  override fun toString() =
    "InsertPrimaryKeyIsCompositeMutationImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class InsertPrimaryKeyIsDateMutationImpl(override val connector: DemoConnectorImpl) :
  InsertPrimaryKeyIsDateMutation {
  override val operationName by InsertPrimaryKeyIsDateMutation.Companion::operationName
  override val dataDeserializer by InsertPrimaryKeyIsDateMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertPrimaryKeyIsDateMutation.Companion::variablesSerializer

  override fun toString() =
    "InsertPrimaryKeyIsDateMutationImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class InsertPrimaryKeyIsFloatMutationImpl(override val connector: DemoConnectorImpl) :
  InsertPrimaryKeyIsFloatMutation {
  override val operationName by InsertPrimaryKeyIsFloatMutation.Companion::operationName
  override val dataDeserializer by InsertPrimaryKeyIsFloatMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertPrimaryKeyIsFloatMutation.Companion::variablesSerializer

  override fun toString() =
    "InsertPrimaryKeyIsFloatMutationImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class InsertPrimaryKeyIsIntMutationImpl(override val connector: DemoConnectorImpl) :
  InsertPrimaryKeyIsIntMutation {
  override val operationName by InsertPrimaryKeyIsIntMutation.Companion::operationName
  override val dataDeserializer by InsertPrimaryKeyIsIntMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertPrimaryKeyIsIntMutation.Companion::variablesSerializer

  override fun toString() =
    "InsertPrimaryKeyIsIntMutationImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class InsertPrimaryKeyIsInt64MutationImpl(override val connector: DemoConnectorImpl) :
  InsertPrimaryKeyIsInt64Mutation {
  override val operationName by InsertPrimaryKeyIsInt64Mutation.Companion::operationName
  override val dataDeserializer by InsertPrimaryKeyIsInt64Mutation.Companion::dataDeserializer
  override val variablesSerializer by InsertPrimaryKeyIsInt64Mutation.Companion::variablesSerializer

  override fun toString() =
    "InsertPrimaryKeyIsInt64MutationImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class InsertPrimaryKeyIsStringMutationImpl(override val connector: DemoConnectorImpl) :
  InsertPrimaryKeyIsStringMutation {
  override val operationName by InsertPrimaryKeyIsStringMutation.Companion::operationName
  override val dataDeserializer by InsertPrimaryKeyIsStringMutation.Companion::dataDeserializer
  override val variablesSerializer by
    InsertPrimaryKeyIsStringMutation.Companion::variablesSerializer

  override fun toString() =
    "InsertPrimaryKeyIsStringMutationImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class InsertPrimaryKeyIsTimestampMutationImpl(override val connector: DemoConnectorImpl) :
  InsertPrimaryKeyIsTimestampMutation {
  override val operationName by InsertPrimaryKeyIsTimestampMutation.Companion::operationName
  override val dataDeserializer by InsertPrimaryKeyIsTimestampMutation.Companion::dataDeserializer
  override val variablesSerializer by
    InsertPrimaryKeyIsTimestampMutation.Companion::variablesSerializer

  override fun toString() =
    "InsertPrimaryKeyIsTimestampMutationImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class InsertPrimaryKeyIsUuidMutationImpl(override val connector: DemoConnectorImpl) :
  InsertPrimaryKeyIsUuidMutation {
  override val operationName by InsertPrimaryKeyIsUuidMutation.Companion::operationName
  override val dataDeserializer by InsertPrimaryKeyIsUuidMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertPrimaryKeyIsUuidMutation.Companion::variablesSerializer

  override fun toString() =
    "InsertPrimaryKeyIsUuidMutationImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class InsertPrimaryKeyNested1MutationImpl(override val connector: DemoConnectorImpl) :
  InsertPrimaryKeyNested1Mutation {
  override val operationName by InsertPrimaryKeyNested1Mutation.Companion::operationName
  override val dataDeserializer by InsertPrimaryKeyNested1Mutation.Companion::dataDeserializer
  override val variablesSerializer by InsertPrimaryKeyNested1Mutation.Companion::variablesSerializer

  override fun toString() =
    "InsertPrimaryKeyNested1MutationImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class InsertPrimaryKeyNested2MutationImpl(override val connector: DemoConnectorImpl) :
  InsertPrimaryKeyNested2Mutation {
  override val operationName by InsertPrimaryKeyNested2Mutation.Companion::operationName
  override val dataDeserializer by InsertPrimaryKeyNested2Mutation.Companion::dataDeserializer
  override val variablesSerializer by InsertPrimaryKeyNested2Mutation.Companion::variablesSerializer

  override fun toString() =
    "InsertPrimaryKeyNested2MutationImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class InsertPrimaryKeyNested3MutationImpl(override val connector: DemoConnectorImpl) :
  InsertPrimaryKeyNested3Mutation {
  override val operationName by InsertPrimaryKeyNested3Mutation.Companion::operationName
  override val dataDeserializer by InsertPrimaryKeyNested3Mutation.Companion::dataDeserializer
  override val variablesSerializer by InsertPrimaryKeyNested3Mutation.Companion::variablesSerializer

  override fun toString() =
    "InsertPrimaryKeyNested3MutationImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class InsertPrimaryKeyNested4MutationImpl(override val connector: DemoConnectorImpl) :
  InsertPrimaryKeyNested4Mutation {
  override val operationName by InsertPrimaryKeyNested4Mutation.Companion::operationName
  override val dataDeserializer by InsertPrimaryKeyNested4Mutation.Companion::dataDeserializer
  override val variablesSerializer by InsertPrimaryKeyNested4Mutation.Companion::variablesSerializer

  override fun toString() =
    "InsertPrimaryKeyNested4MutationImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class InsertPrimaryKeyNested5MutationImpl(override val connector: DemoConnectorImpl) :
  InsertPrimaryKeyNested5Mutation {
  override val operationName by InsertPrimaryKeyNested5Mutation.Companion::operationName
  override val dataDeserializer by InsertPrimaryKeyNested5Mutation.Companion::dataDeserializer
  override val variablesSerializer by InsertPrimaryKeyNested5Mutation.Companion::variablesSerializer

  override fun toString() =
    "InsertPrimaryKeyNested5MutationImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class InsertPrimaryKeyNested6MutationImpl(override val connector: DemoConnectorImpl) :
  InsertPrimaryKeyNested6Mutation {
  override val operationName by InsertPrimaryKeyNested6Mutation.Companion::operationName
  override val dataDeserializer by InsertPrimaryKeyNested6Mutation.Companion::dataDeserializer
  override val variablesSerializer by InsertPrimaryKeyNested6Mutation.Companion::variablesSerializer

  override fun toString() =
    "InsertPrimaryKeyNested6MutationImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class InsertPrimaryKeyNested7MutationImpl(override val connector: DemoConnectorImpl) :
  InsertPrimaryKeyNested7Mutation {
  override val operationName by InsertPrimaryKeyNested7Mutation.Companion::operationName
  override val dataDeserializer by InsertPrimaryKeyNested7Mutation.Companion::dataDeserializer
  override val variablesSerializer by InsertPrimaryKeyNested7Mutation.Companion::variablesSerializer

  override fun toString() =
    "InsertPrimaryKeyNested7MutationImpl(" +
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

private class InsertSyntheticIdMutationImpl(override val connector: DemoConnectorImpl) :
  InsertSyntheticIdMutation {
  override val operationName by InsertSyntheticIdMutation.Companion::operationName
  override val dataDeserializer by InsertSyntheticIdMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertSyntheticIdMutation.Companion::variablesSerializer

  override fun toString() =
    "InsertSyntheticIdMutationImpl(" +
      "operationName=$operationName, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "connector=$connector)"
}

private class InsertTimestampVariantsMutationImpl(override val connector: DemoConnectorImpl) :
  InsertTimestampVariantsMutation {
  override val operationName by InsertTimestampVariantsMutation.Companion::operationName
  override val dataDeserializer by InsertTimestampVariantsMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertTimestampVariantsMutation.Companion::variablesSerializer

  override fun toString() =
    "InsertTimestampVariantsMutationImpl(" +
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
