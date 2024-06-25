
@file:Suppress(
  "KotlinRedundantDiagnosticSuppress",
  "LocalVariableName",
  "RedundantVisibilityModifier",
  "RemoveEmptyClassBody",
  "SpellCheckingInspection",
  "LocalVariableName",
  "unused",
)

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
  
    public val getBooleanVariantsByKey: GetBooleanVariantsByKeyQuery
  
    public val getFloatVariantsByKey: GetFloatVariantsByKeyQuery
  
    public val getFooById: GetFooByIdQuery
  
    public val getFoosByBar: GetFoosByBarQuery
  
    public val getHardcodedFoo: GetHardcodedFooQuery
  
    public val getInt64variantsByKey: GetInt64variantsByKeyQuery
  
    public val getIntVariantsByKey: GetIntVariantsByKeyQuery
  
    public val getManyToManyChildAByKey: GetManyToManyChildAByKeyQuery
  
    public val getManyToManySelfChildByKey: GetManyToManySelfChildByKeyQuery
  
    public val getManyToOneChildByKey: GetManyToOneChildByKeyQuery
  
    public val getManyToOneSelfCustomNameByKey: GetManyToOneSelfCustomNameByKeyQuery
  
    public val getManyToOneSelfMatchingNameByKey: GetManyToOneSelfMatchingNameByKeyQuery
  
    public val getNested1byKey: GetNested1byKeyQuery
  
    public val getNonNullDateByKey: GetNonNullDateByKeyQuery
  
    public val getNonNullDatesWithDefaultsByKey: GetNonNullDatesWithDefaultsByKeyQuery
  
    public val getNonNullTimestampByKey: GetNonNullTimestampByKeyQuery
  
    public val getNonNullTimestampsWithDefaultsByKey: GetNonNullTimestampsWithDefaultsByKeyQuery
  
    public val getNonNullableListsByKey: GetNonNullableListsByKeyQuery
  
    public val getNullableDateByKey: GetNullableDateByKeyQuery
  
    public val getNullableDatesWithDefaultsByKey: GetNullableDatesWithDefaultsByKeyQuery
  
    public val getNullableListsByKey: GetNullableListsByKeyQuery
  
    public val getNullableTimestampByKey: GetNullableTimestampByKeyQuery
  
    public val getNullableTimestampsWithDefaultsByKey: GetNullableTimestampsWithDefaultsByKeyQuery
  
    public val getOptionalStringsByKey: GetOptionalStringsByKeyQuery
  
    public val getPrimaryKeyIsCompositeByKey: GetPrimaryKeyIsCompositeByKeyQuery
  
    public val getPrimaryKeyIsDateByKey: GetPrimaryKeyIsDateByKeyQuery
  
    public val getPrimaryKeyIsFloatByKey: GetPrimaryKeyIsFloatByKeyQuery
  
    public val getPrimaryKeyIsInt64byKey: GetPrimaryKeyIsInt64byKeyQuery
  
    public val getPrimaryKeyIsIntByKey: GetPrimaryKeyIsIntByKeyQuery
  
    public val getPrimaryKeyIsStringByKey: GetPrimaryKeyIsStringByKeyQuery
  
    public val getPrimaryKeyIsTimestampByKey: GetPrimaryKeyIsTimestampByKeyQuery
  
    public val getPrimaryKeyIsUuidByKey: GetPrimaryKeyIsUuidByKeyQuery
  
    public val getPrimaryKeyNested7byKey: GetPrimaryKeyNested7byKeyQuery
  
    public val getStringVariantsByKey: GetStringVariantsByKeyQuery
  
    public val getSyntheticIdById: GetSyntheticIdByIdQuery
  
    public val getUuidVariantsByKey: GetUuidVariantsByKeyQuery
  
    public val insertBooleanVariants: InsertBooleanVariantsMutation
  
    public val insertBooleanVariantsWithHardcodedDefaults: InsertBooleanVariantsWithHardcodedDefaultsMutation
  
    public val insertFloatVariants: InsertFloatVariantsMutation
  
    public val insertFloatVariantsWithHardcodedDefaults: InsertFloatVariantsWithHardcodedDefaultsMutation
  
    public val insertFoo: InsertFooMutation
  
    public val insertInt64variants: InsertInt64variantsMutation
  
    public val insertInt64variantsWithHardcodedDefaults: InsertInt64variantsWithHardcodedDefaultsMutation
  
    public val insertIntVariants: InsertIntVariantsMutation
  
    public val insertIntVariantsWithHardcodedDefaults: InsertIntVariantsWithHardcodedDefaultsMutation
  
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
  
    public val insertNonNullDate: InsertNonNullDateMutation
  
    public val insertNonNullDatesWithDefaults: InsertNonNullDatesWithDefaultsMutation
  
    public val insertNonNullTimestamp: InsertNonNullTimestampMutation
  
    public val insertNonNullTimestampsWithDefaults: InsertNonNullTimestampsWithDefaultsMutation
  
    public val insertNonNullableLists: InsertNonNullableListsMutation
  
    public val insertNullableDate: InsertNullableDateMutation
  
    public val insertNullableDatesWithDefaults: InsertNullableDatesWithDefaultsMutation
  
    public val insertNullableLists: InsertNullableListsMutation
  
    public val insertNullableTimestamp: InsertNullableTimestampMutation
  
    public val insertNullableTimestampsWithDefaults: InsertNullableTimestampsWithDefaultsMutation
  
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
  
    public val insertStringVariantsWithHardcodedDefaults: InsertStringVariantsWithHardcodedDefaultsMutation
  
    public val insertSyntheticId: InsertSyntheticIdMutation
  
    public val insertUuidVariants: InsertUuidVariantsMutation
  
    public val insertUuidVariantsWithHardcodedDefaults: InsertUuidVariantsWithHardcodedDefaultsMutation
  
    public val updateBooleanVariantsByKey: UpdateBooleanVariantsByKeyMutation
  
    public val updateFloatVariantsByKey: UpdateFloatVariantsByKeyMutation
  
    public val updateFoo: UpdateFooMutation
  
    public val updateFoosByBar: UpdateFoosByBarMutation
  
    public val updateInt64variantsByKey: UpdateInt64variantsByKeyMutation
  
    public val updateIntVariantsByKey: UpdateIntVariantsByKeyMutation
  
    public val updateNonNullDate: UpdateNonNullDateMutation
  
    public val updateNonNullTimestamp: UpdateNonNullTimestampMutation
  
    public val updateNonNullableListsByKey: UpdateNonNullableListsByKeyMutation
  
    public val updateNullableDate: UpdateNullableDateMutation
  
    public val updateNullableListsByKey: UpdateNullableListsByKeyMutation
  
    public val updateNullableTimestamp: UpdateNullableTimestampMutation
  
    public val updateStringVariantsByKey: UpdateStringVariantsByKeyMutation
  
    public val updateUuidVariantsByKey: UpdateUuidVariantsByKeyMutation
  
    public val upsertFoo: UpsertFooMutation
  
    public val upsertHardcodedFoo: UpsertHardcodedFooMutation
  

  public companion object {
    @Suppress("MemberVisibilityCanBePrivate")
    public val config: ConnectorConfig = ConnectorConfig(
      connector = "demo",
      location = "us-central1",
      serviceId = "sid2ehn9ct8te",
    )

    public fun getInstance(
      dataConnect: FirebaseDataConnect
    ):DemoConnector = synchronized(instances) {
      instances.getOrPut(dataConnect) {
        DemoConnectorImpl(dataConnect)
      }
    }

    private val instances = WeakHashMap<FirebaseDataConnect, DemoConnectorImpl>()
  }
}

public val DemoConnector.Companion.instance:DemoConnector
  get() = getInstance(FirebaseDataConnect.getInstance(config))

public fun DemoConnector.Companion.getInstance(
  settings: DataConnectSettings = DataConnectSettings()
):DemoConnector =
  getInstance(FirebaseDataConnect.getInstance(config, settings))

public fun DemoConnector.Companion.getInstance(
  app: FirebaseApp,
  settings: DataConnectSettings = DataConnectSettings()
):DemoConnector =
  getInstance(FirebaseDataConnect.getInstance(app, config, settings))

private class DemoConnectorImpl(
  override val dataConnect: FirebaseDataConnect
) : DemoConnector {
  
    override val deleteFoo by lazy(LazyThreadSafetyMode.PUBLICATION) {
      DeleteFooMutationImpl(this)
    }
  
    override val deleteFoosByBar by lazy(LazyThreadSafetyMode.PUBLICATION) {
      DeleteFoosByBarMutationImpl(this)
    }
  
    override val getBooleanVariantsByKey by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetBooleanVariantsByKeyQueryImpl(this)
    }
  
    override val getFloatVariantsByKey by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetFloatVariantsByKeyQueryImpl(this)
    }
  
    override val getFooById by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetFooByIdQueryImpl(this)
    }
  
    override val getFoosByBar by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetFoosByBarQueryImpl(this)
    }
  
    override val getHardcodedFoo by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetHardcodedFooQueryImpl(this)
    }
  
    override val getInt64variantsByKey by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetInt64variantsByKeyQueryImpl(this)
    }
  
    override val getIntVariantsByKey by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetIntVariantsByKeyQueryImpl(this)
    }
  
    override val getManyToManyChildAByKey by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetManyToManyChildAByKeyQueryImpl(this)
    }
  
    override val getManyToManySelfChildByKey by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetManyToManySelfChildByKeyQueryImpl(this)
    }
  
    override val getManyToOneChildByKey by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetManyToOneChildByKeyQueryImpl(this)
    }
  
    override val getManyToOneSelfCustomNameByKey by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetManyToOneSelfCustomNameByKeyQueryImpl(this)
    }
  
    override val getManyToOneSelfMatchingNameByKey by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetManyToOneSelfMatchingNameByKeyQueryImpl(this)
    }
  
    override val getNested1byKey by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetNested1byKeyQueryImpl(this)
    }
  
    override val getNonNullDateByKey by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetNonNullDateByKeyQueryImpl(this)
    }
  
    override val getNonNullDatesWithDefaultsByKey by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetNonNullDatesWithDefaultsByKeyQueryImpl(this)
    }
  
    override val getNonNullTimestampByKey by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetNonNullTimestampByKeyQueryImpl(this)
    }
  
    override val getNonNullTimestampsWithDefaultsByKey by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetNonNullTimestampsWithDefaultsByKeyQueryImpl(this)
    }
  
    override val getNonNullableListsByKey by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetNonNullableListsByKeyQueryImpl(this)
    }
  
    override val getNullableDateByKey by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetNullableDateByKeyQueryImpl(this)
    }
  
    override val getNullableDatesWithDefaultsByKey by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetNullableDatesWithDefaultsByKeyQueryImpl(this)
    }
  
    override val getNullableListsByKey by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetNullableListsByKeyQueryImpl(this)
    }
  
    override val getNullableTimestampByKey by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetNullableTimestampByKeyQueryImpl(this)
    }
  
    override val getNullableTimestampsWithDefaultsByKey by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetNullableTimestampsWithDefaultsByKeyQueryImpl(this)
    }
  
    override val getOptionalStringsByKey by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetOptionalStringsByKeyQueryImpl(this)
    }
  
    override val getPrimaryKeyIsCompositeByKey by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetPrimaryKeyIsCompositeByKeyQueryImpl(this)
    }
  
    override val getPrimaryKeyIsDateByKey by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetPrimaryKeyIsDateByKeyQueryImpl(this)
    }
  
    override val getPrimaryKeyIsFloatByKey by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetPrimaryKeyIsFloatByKeyQueryImpl(this)
    }
  
    override val getPrimaryKeyIsInt64byKey by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetPrimaryKeyIsInt64byKeyQueryImpl(this)
    }
  
    override val getPrimaryKeyIsIntByKey by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetPrimaryKeyIsIntByKeyQueryImpl(this)
    }
  
    override val getPrimaryKeyIsStringByKey by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetPrimaryKeyIsStringByKeyQueryImpl(this)
    }
  
    override val getPrimaryKeyIsTimestampByKey by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetPrimaryKeyIsTimestampByKeyQueryImpl(this)
    }
  
    override val getPrimaryKeyIsUuidByKey by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetPrimaryKeyIsUuidByKeyQueryImpl(this)
    }
  
    override val getPrimaryKeyNested7byKey by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetPrimaryKeyNested7byKeyQueryImpl(this)
    }
  
    override val getStringVariantsByKey by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetStringVariantsByKeyQueryImpl(this)
    }
  
    override val getSyntheticIdById by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetSyntheticIdByIdQueryImpl(this)
    }
  
    override val getUuidVariantsByKey by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetUuidVariantsByKeyQueryImpl(this)
    }
  
    override val insertBooleanVariants by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertBooleanVariantsMutationImpl(this)
    }
  
    override val insertBooleanVariantsWithHardcodedDefaults by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertBooleanVariantsWithHardcodedDefaultsMutationImpl(this)
    }
  
    override val insertFloatVariants by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertFloatVariantsMutationImpl(this)
    }
  
    override val insertFloatVariantsWithHardcodedDefaults by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertFloatVariantsWithHardcodedDefaultsMutationImpl(this)
    }
  
    override val insertFoo by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertFooMutationImpl(this)
    }
  
    override val insertInt64variants by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertInt64variantsMutationImpl(this)
    }
  
    override val insertInt64variantsWithHardcodedDefaults by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertInt64variantsWithHardcodedDefaultsMutationImpl(this)
    }
  
    override val insertIntVariants by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertIntVariantsMutationImpl(this)
    }
  
    override val insertIntVariantsWithHardcodedDefaults by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertIntVariantsWithHardcodedDefaultsMutationImpl(this)
    }
  
    override val insertManyToManyChildA by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertManyToManyChildAMutationImpl(this)
    }
  
    override val insertManyToManyChildB by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertManyToManyChildBMutationImpl(this)
    }
  
    override val insertManyToManyParent by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertManyToManyParentMutationImpl(this)
    }
  
    override val insertManyToManySelfChild by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertManyToManySelfChildMutationImpl(this)
    }
  
    override val insertManyToManySelfParent by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertManyToManySelfParentMutationImpl(this)
    }
  
    override val insertManyToOneChild by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertManyToOneChildMutationImpl(this)
    }
  
    override val insertManyToOneParent by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertManyToOneParentMutationImpl(this)
    }
  
    override val insertManyToOneSelfCustomName by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertManyToOneSelfCustomNameMutationImpl(this)
    }
  
    override val insertManyToOneSelfMatchingName by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertManyToOneSelfMatchingNameMutationImpl(this)
    }
  
    override val insertNested1 by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertNested1MutationImpl(this)
    }
  
    override val insertNested2 by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertNested2MutationImpl(this)
    }
  
    override val insertNested3 by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertNested3MutationImpl(this)
    }
  
    override val insertNonNullDate by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertNonNullDateMutationImpl(this)
    }
  
    override val insertNonNullDatesWithDefaults by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertNonNullDatesWithDefaultsMutationImpl(this)
    }
  
    override val insertNonNullTimestamp by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertNonNullTimestampMutationImpl(this)
    }
  
    override val insertNonNullTimestampsWithDefaults by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertNonNullTimestampsWithDefaultsMutationImpl(this)
    }
  
    override val insertNonNullableLists by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertNonNullableListsMutationImpl(this)
    }
  
    override val insertNullableDate by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertNullableDateMutationImpl(this)
    }
  
    override val insertNullableDatesWithDefaults by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertNullableDatesWithDefaultsMutationImpl(this)
    }
  
    override val insertNullableLists by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertNullableListsMutationImpl(this)
    }
  
    override val insertNullableTimestamp by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertNullableTimestampMutationImpl(this)
    }
  
    override val insertNullableTimestampsWithDefaults by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertNullableTimestampsWithDefaultsMutationImpl(this)
    }
  
    override val insertOptionalStrings by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertOptionalStringsMutationImpl(this)
    }
  
    override val insertPrimaryKeyIsComposite by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertPrimaryKeyIsCompositeMutationImpl(this)
    }
  
    override val insertPrimaryKeyIsDate by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertPrimaryKeyIsDateMutationImpl(this)
    }
  
    override val insertPrimaryKeyIsFloat by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertPrimaryKeyIsFloatMutationImpl(this)
    }
  
    override val insertPrimaryKeyIsInt by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertPrimaryKeyIsIntMutationImpl(this)
    }
  
    override val insertPrimaryKeyIsInt64 by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertPrimaryKeyIsInt64MutationImpl(this)
    }
  
    override val insertPrimaryKeyIsString by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertPrimaryKeyIsStringMutationImpl(this)
    }
  
    override val insertPrimaryKeyIsTimestamp by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertPrimaryKeyIsTimestampMutationImpl(this)
    }
  
    override val insertPrimaryKeyIsUuid by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertPrimaryKeyIsUuidMutationImpl(this)
    }
  
    override val insertPrimaryKeyNested1 by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertPrimaryKeyNested1MutationImpl(this)
    }
  
    override val insertPrimaryKeyNested2 by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertPrimaryKeyNested2MutationImpl(this)
    }
  
    override val insertPrimaryKeyNested3 by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertPrimaryKeyNested3MutationImpl(this)
    }
  
    override val insertPrimaryKeyNested4 by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertPrimaryKeyNested4MutationImpl(this)
    }
  
    override val insertPrimaryKeyNested5 by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertPrimaryKeyNested5MutationImpl(this)
    }
  
    override val insertPrimaryKeyNested6 by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertPrimaryKeyNested6MutationImpl(this)
    }
  
    override val insertPrimaryKeyNested7 by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertPrimaryKeyNested7MutationImpl(this)
    }
  
    override val insertStringVariants by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertStringVariantsMutationImpl(this)
    }
  
    override val insertStringVariantsWithHardcodedDefaults by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertStringVariantsWithHardcodedDefaultsMutationImpl(this)
    }
  
    override val insertSyntheticId by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertSyntheticIdMutationImpl(this)
    }
  
    override val insertUuidVariants by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertUuidVariantsMutationImpl(this)
    }
  
    override val insertUuidVariantsWithHardcodedDefaults by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertUuidVariantsWithHardcodedDefaultsMutationImpl(this)
    }
  
    override val updateBooleanVariantsByKey by lazy(LazyThreadSafetyMode.PUBLICATION) {
      UpdateBooleanVariantsByKeyMutationImpl(this)
    }
  
    override val updateFloatVariantsByKey by lazy(LazyThreadSafetyMode.PUBLICATION) {
      UpdateFloatVariantsByKeyMutationImpl(this)
    }
  
    override val updateFoo by lazy(LazyThreadSafetyMode.PUBLICATION) {
      UpdateFooMutationImpl(this)
    }
  
    override val updateFoosByBar by lazy(LazyThreadSafetyMode.PUBLICATION) {
      UpdateFoosByBarMutationImpl(this)
    }
  
    override val updateInt64variantsByKey by lazy(LazyThreadSafetyMode.PUBLICATION) {
      UpdateInt64variantsByKeyMutationImpl(this)
    }
  
    override val updateIntVariantsByKey by lazy(LazyThreadSafetyMode.PUBLICATION) {
      UpdateIntVariantsByKeyMutationImpl(this)
    }
  
    override val updateNonNullDate by lazy(LazyThreadSafetyMode.PUBLICATION) {
      UpdateNonNullDateMutationImpl(this)
    }
  
    override val updateNonNullTimestamp by lazy(LazyThreadSafetyMode.PUBLICATION) {
      UpdateNonNullTimestampMutationImpl(this)
    }
  
    override val updateNonNullableListsByKey by lazy(LazyThreadSafetyMode.PUBLICATION) {
      UpdateNonNullableListsByKeyMutationImpl(this)
    }
  
    override val updateNullableDate by lazy(LazyThreadSafetyMode.PUBLICATION) {
      UpdateNullableDateMutationImpl(this)
    }
  
    override val updateNullableListsByKey by lazy(LazyThreadSafetyMode.PUBLICATION) {
      UpdateNullableListsByKeyMutationImpl(this)
    }
  
    override val updateNullableTimestamp by lazy(LazyThreadSafetyMode.PUBLICATION) {
      UpdateNullableTimestampMutationImpl(this)
    }
  
    override val updateStringVariantsByKey by lazy(LazyThreadSafetyMode.PUBLICATION) {
      UpdateStringVariantsByKeyMutationImpl(this)
    }
  
    override val updateUuidVariantsByKey by lazy(LazyThreadSafetyMode.PUBLICATION) {
      UpdateUuidVariantsByKeyMutationImpl(this)
    }
  
    override val upsertFoo by lazy(LazyThreadSafetyMode.PUBLICATION) {
      UpsertFooMutationImpl(this)
    }
  
    override val upsertHardcodedFoo by lazy(LazyThreadSafetyMode.PUBLICATION) {
      UpsertHardcodedFooMutationImpl(this)
    }
  

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "DemoConnectorImpl(dataConnect=$dataConnect)"
}


  private class DeleteFooMutationImpl(
    override val connector: DemoConnectorImpl
  ) : DeleteFooMutation {
  override val operationName by DeleteFooMutation.Companion::operationName
  override val dataDeserializer by DeleteFooMutation.Companion::dataDeserializer
  override val variablesSerializer by DeleteFooMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "DeleteFooMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class DeleteFoosByBarMutationImpl(
    override val connector: DemoConnectorImpl
  ) : DeleteFoosByBarMutation {
  override val operationName by DeleteFoosByBarMutation.Companion::operationName
  override val dataDeserializer by DeleteFoosByBarMutation.Companion::dataDeserializer
  override val variablesSerializer by DeleteFoosByBarMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "DeleteFoosByBarMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class GetBooleanVariantsByKeyQueryImpl(
    override val connector: DemoConnectorImpl
  ) : GetBooleanVariantsByKeyQuery {
  override val operationName by GetBooleanVariantsByKeyQuery.Companion::operationName
  override val dataDeserializer by GetBooleanVariantsByKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by GetBooleanVariantsByKeyQuery.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "GetBooleanVariantsByKeyQueryImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class GetFloatVariantsByKeyQueryImpl(
    override val connector: DemoConnectorImpl
  ) : GetFloatVariantsByKeyQuery {
  override val operationName by GetFloatVariantsByKeyQuery.Companion::operationName
  override val dataDeserializer by GetFloatVariantsByKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by GetFloatVariantsByKeyQuery.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "GetFloatVariantsByKeyQueryImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class GetFooByIdQueryImpl(
    override val connector: DemoConnectorImpl
  ) : GetFooByIdQuery {
  override val operationName by GetFooByIdQuery.Companion::operationName
  override val dataDeserializer by GetFooByIdQuery.Companion::dataDeserializer
  override val variablesSerializer by GetFooByIdQuery.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "GetFooByIdQueryImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class GetFoosByBarQueryImpl(
    override val connector: DemoConnectorImpl
  ) : GetFoosByBarQuery {
  override val operationName by GetFoosByBarQuery.Companion::operationName
  override val dataDeserializer by GetFoosByBarQuery.Companion::dataDeserializer
  override val variablesSerializer by GetFoosByBarQuery.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "GetFoosByBarQueryImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class GetHardcodedFooQueryImpl(
    override val connector: DemoConnectorImpl
  ) : GetHardcodedFooQuery {
  override val operationName by GetHardcodedFooQuery.Companion::operationName
  override val dataDeserializer by GetHardcodedFooQuery.Companion::dataDeserializer
  override val variablesSerializer by GetHardcodedFooQuery.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "GetHardcodedFooQueryImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class GetInt64variantsByKeyQueryImpl(
    override val connector: DemoConnectorImpl
  ) : GetInt64variantsByKeyQuery {
  override val operationName by GetInt64variantsByKeyQuery.Companion::operationName
  override val dataDeserializer by GetInt64variantsByKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by GetInt64variantsByKeyQuery.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "GetInt64variantsByKeyQueryImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class GetIntVariantsByKeyQueryImpl(
    override val connector: DemoConnectorImpl
  ) : GetIntVariantsByKeyQuery {
  override val operationName by GetIntVariantsByKeyQuery.Companion::operationName
  override val dataDeserializer by GetIntVariantsByKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by GetIntVariantsByKeyQuery.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "GetIntVariantsByKeyQueryImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class GetManyToManyChildAByKeyQueryImpl(
    override val connector: DemoConnectorImpl
  ) : GetManyToManyChildAByKeyQuery {
  override val operationName by GetManyToManyChildAByKeyQuery.Companion::operationName
  override val dataDeserializer by GetManyToManyChildAByKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by GetManyToManyChildAByKeyQuery.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "GetManyToManyChildAByKeyQueryImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class GetManyToManySelfChildByKeyQueryImpl(
    override val connector: DemoConnectorImpl
  ) : GetManyToManySelfChildByKeyQuery {
  override val operationName by GetManyToManySelfChildByKeyQuery.Companion::operationName
  override val dataDeserializer by GetManyToManySelfChildByKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by GetManyToManySelfChildByKeyQuery.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "GetManyToManySelfChildByKeyQueryImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class GetManyToOneChildByKeyQueryImpl(
    override val connector: DemoConnectorImpl
  ) : GetManyToOneChildByKeyQuery {
  override val operationName by GetManyToOneChildByKeyQuery.Companion::operationName
  override val dataDeserializer by GetManyToOneChildByKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by GetManyToOneChildByKeyQuery.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "GetManyToOneChildByKeyQueryImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class GetManyToOneSelfCustomNameByKeyQueryImpl(
    override val connector: DemoConnectorImpl
  ) : GetManyToOneSelfCustomNameByKeyQuery {
  override val operationName by GetManyToOneSelfCustomNameByKeyQuery.Companion::operationName
  override val dataDeserializer by GetManyToOneSelfCustomNameByKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by GetManyToOneSelfCustomNameByKeyQuery.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "GetManyToOneSelfCustomNameByKeyQueryImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class GetManyToOneSelfMatchingNameByKeyQueryImpl(
    override val connector: DemoConnectorImpl
  ) : GetManyToOneSelfMatchingNameByKeyQuery {
  override val operationName by GetManyToOneSelfMatchingNameByKeyQuery.Companion::operationName
  override val dataDeserializer by GetManyToOneSelfMatchingNameByKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by GetManyToOneSelfMatchingNameByKeyQuery.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "GetManyToOneSelfMatchingNameByKeyQueryImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class GetNested1byKeyQueryImpl(
    override val connector: DemoConnectorImpl
  ) : GetNested1byKeyQuery {
  override val operationName by GetNested1byKeyQuery.Companion::operationName
  override val dataDeserializer by GetNested1byKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by GetNested1byKeyQuery.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "GetNested1byKeyQueryImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class GetNonNullDateByKeyQueryImpl(
    override val connector: DemoConnectorImpl
  ) : GetNonNullDateByKeyQuery {
  override val operationName by GetNonNullDateByKeyQuery.Companion::operationName
  override val dataDeserializer by GetNonNullDateByKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by GetNonNullDateByKeyQuery.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "GetNonNullDateByKeyQueryImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class GetNonNullDatesWithDefaultsByKeyQueryImpl(
    override val connector: DemoConnectorImpl
  ) : GetNonNullDatesWithDefaultsByKeyQuery {
  override val operationName by GetNonNullDatesWithDefaultsByKeyQuery.Companion::operationName
  override val dataDeserializer by GetNonNullDatesWithDefaultsByKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by GetNonNullDatesWithDefaultsByKeyQuery.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "GetNonNullDatesWithDefaultsByKeyQueryImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class GetNonNullTimestampByKeyQueryImpl(
    override val connector: DemoConnectorImpl
  ) : GetNonNullTimestampByKeyQuery {
  override val operationName by GetNonNullTimestampByKeyQuery.Companion::operationName
  override val dataDeserializer by GetNonNullTimestampByKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by GetNonNullTimestampByKeyQuery.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "GetNonNullTimestampByKeyQueryImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class GetNonNullTimestampsWithDefaultsByKeyQueryImpl(
    override val connector: DemoConnectorImpl
  ) : GetNonNullTimestampsWithDefaultsByKeyQuery {
  override val operationName by GetNonNullTimestampsWithDefaultsByKeyQuery.Companion::operationName
  override val dataDeserializer by GetNonNullTimestampsWithDefaultsByKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by GetNonNullTimestampsWithDefaultsByKeyQuery.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "GetNonNullTimestampsWithDefaultsByKeyQueryImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class GetNonNullableListsByKeyQueryImpl(
    override val connector: DemoConnectorImpl
  ) : GetNonNullableListsByKeyQuery {
  override val operationName by GetNonNullableListsByKeyQuery.Companion::operationName
  override val dataDeserializer by GetNonNullableListsByKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by GetNonNullableListsByKeyQuery.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "GetNonNullableListsByKeyQueryImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class GetNullableDateByKeyQueryImpl(
    override val connector: DemoConnectorImpl
  ) : GetNullableDateByKeyQuery {
  override val operationName by GetNullableDateByKeyQuery.Companion::operationName
  override val dataDeserializer by GetNullableDateByKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by GetNullableDateByKeyQuery.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "GetNullableDateByKeyQueryImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class GetNullableDatesWithDefaultsByKeyQueryImpl(
    override val connector: DemoConnectorImpl
  ) : GetNullableDatesWithDefaultsByKeyQuery {
  override val operationName by GetNullableDatesWithDefaultsByKeyQuery.Companion::operationName
  override val dataDeserializer by GetNullableDatesWithDefaultsByKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by GetNullableDatesWithDefaultsByKeyQuery.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "GetNullableDatesWithDefaultsByKeyQueryImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class GetNullableListsByKeyQueryImpl(
    override val connector: DemoConnectorImpl
  ) : GetNullableListsByKeyQuery {
  override val operationName by GetNullableListsByKeyQuery.Companion::operationName
  override val dataDeserializer by GetNullableListsByKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by GetNullableListsByKeyQuery.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "GetNullableListsByKeyQueryImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class GetNullableTimestampByKeyQueryImpl(
    override val connector: DemoConnectorImpl
  ) : GetNullableTimestampByKeyQuery {
  override val operationName by GetNullableTimestampByKeyQuery.Companion::operationName
  override val dataDeserializer by GetNullableTimestampByKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by GetNullableTimestampByKeyQuery.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "GetNullableTimestampByKeyQueryImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class GetNullableTimestampsWithDefaultsByKeyQueryImpl(
    override val connector: DemoConnectorImpl
  ) : GetNullableTimestampsWithDefaultsByKeyQuery {
  override val operationName by GetNullableTimestampsWithDefaultsByKeyQuery.Companion::operationName
  override val dataDeserializer by GetNullableTimestampsWithDefaultsByKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by GetNullableTimestampsWithDefaultsByKeyQuery.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "GetNullableTimestampsWithDefaultsByKeyQueryImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class GetOptionalStringsByKeyQueryImpl(
    override val connector: DemoConnectorImpl
  ) : GetOptionalStringsByKeyQuery {
  override val operationName by GetOptionalStringsByKeyQuery.Companion::operationName
  override val dataDeserializer by GetOptionalStringsByKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by GetOptionalStringsByKeyQuery.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "GetOptionalStringsByKeyQueryImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class GetPrimaryKeyIsCompositeByKeyQueryImpl(
    override val connector: DemoConnectorImpl
  ) : GetPrimaryKeyIsCompositeByKeyQuery {
  override val operationName by GetPrimaryKeyIsCompositeByKeyQuery.Companion::operationName
  override val dataDeserializer by GetPrimaryKeyIsCompositeByKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by GetPrimaryKeyIsCompositeByKeyQuery.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "GetPrimaryKeyIsCompositeByKeyQueryImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class GetPrimaryKeyIsDateByKeyQueryImpl(
    override val connector: DemoConnectorImpl
  ) : GetPrimaryKeyIsDateByKeyQuery {
  override val operationName by GetPrimaryKeyIsDateByKeyQuery.Companion::operationName
  override val dataDeserializer by GetPrimaryKeyIsDateByKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by GetPrimaryKeyIsDateByKeyQuery.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "GetPrimaryKeyIsDateByKeyQueryImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class GetPrimaryKeyIsFloatByKeyQueryImpl(
    override val connector: DemoConnectorImpl
  ) : GetPrimaryKeyIsFloatByKeyQuery {
  override val operationName by GetPrimaryKeyIsFloatByKeyQuery.Companion::operationName
  override val dataDeserializer by GetPrimaryKeyIsFloatByKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by GetPrimaryKeyIsFloatByKeyQuery.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "GetPrimaryKeyIsFloatByKeyQueryImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class GetPrimaryKeyIsInt64byKeyQueryImpl(
    override val connector: DemoConnectorImpl
  ) : GetPrimaryKeyIsInt64byKeyQuery {
  override val operationName by GetPrimaryKeyIsInt64byKeyQuery.Companion::operationName
  override val dataDeserializer by GetPrimaryKeyIsInt64byKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by GetPrimaryKeyIsInt64byKeyQuery.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "GetPrimaryKeyIsInt64byKeyQueryImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class GetPrimaryKeyIsIntByKeyQueryImpl(
    override val connector: DemoConnectorImpl
  ) : GetPrimaryKeyIsIntByKeyQuery {
  override val operationName by GetPrimaryKeyIsIntByKeyQuery.Companion::operationName
  override val dataDeserializer by GetPrimaryKeyIsIntByKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by GetPrimaryKeyIsIntByKeyQuery.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "GetPrimaryKeyIsIntByKeyQueryImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class GetPrimaryKeyIsStringByKeyQueryImpl(
    override val connector: DemoConnectorImpl
  ) : GetPrimaryKeyIsStringByKeyQuery {
  override val operationName by GetPrimaryKeyIsStringByKeyQuery.Companion::operationName
  override val dataDeserializer by GetPrimaryKeyIsStringByKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by GetPrimaryKeyIsStringByKeyQuery.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "GetPrimaryKeyIsStringByKeyQueryImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class GetPrimaryKeyIsTimestampByKeyQueryImpl(
    override val connector: DemoConnectorImpl
  ) : GetPrimaryKeyIsTimestampByKeyQuery {
  override val operationName by GetPrimaryKeyIsTimestampByKeyQuery.Companion::operationName
  override val dataDeserializer by GetPrimaryKeyIsTimestampByKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by GetPrimaryKeyIsTimestampByKeyQuery.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "GetPrimaryKeyIsTimestampByKeyQueryImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class GetPrimaryKeyIsUuidByKeyQueryImpl(
    override val connector: DemoConnectorImpl
  ) : GetPrimaryKeyIsUuidByKeyQuery {
  override val operationName by GetPrimaryKeyIsUuidByKeyQuery.Companion::operationName
  override val dataDeserializer by GetPrimaryKeyIsUuidByKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by GetPrimaryKeyIsUuidByKeyQuery.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "GetPrimaryKeyIsUuidByKeyQueryImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class GetPrimaryKeyNested7byKeyQueryImpl(
    override val connector: DemoConnectorImpl
  ) : GetPrimaryKeyNested7byKeyQuery {
  override val operationName by GetPrimaryKeyNested7byKeyQuery.Companion::operationName
  override val dataDeserializer by GetPrimaryKeyNested7byKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by GetPrimaryKeyNested7byKeyQuery.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "GetPrimaryKeyNested7byKeyQueryImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class GetStringVariantsByKeyQueryImpl(
    override val connector: DemoConnectorImpl
  ) : GetStringVariantsByKeyQuery {
  override val operationName by GetStringVariantsByKeyQuery.Companion::operationName
  override val dataDeserializer by GetStringVariantsByKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by GetStringVariantsByKeyQuery.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "GetStringVariantsByKeyQueryImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class GetSyntheticIdByIdQueryImpl(
    override val connector: DemoConnectorImpl
  ) : GetSyntheticIdByIdQuery {
  override val operationName by GetSyntheticIdByIdQuery.Companion::operationName
  override val dataDeserializer by GetSyntheticIdByIdQuery.Companion::dataDeserializer
  override val variablesSerializer by GetSyntheticIdByIdQuery.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "GetSyntheticIdByIdQueryImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class GetUuidVariantsByKeyQueryImpl(
    override val connector: DemoConnectorImpl
  ) : GetUuidVariantsByKeyQuery {
  override val operationName by GetUuidVariantsByKeyQuery.Companion::operationName
  override val dataDeserializer by GetUuidVariantsByKeyQuery.Companion::dataDeserializer
  override val variablesSerializer by GetUuidVariantsByKeyQuery.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "GetUuidVariantsByKeyQueryImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertBooleanVariantsMutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertBooleanVariantsMutation {
  override val operationName by InsertBooleanVariantsMutation.Companion::operationName
  override val dataDeserializer by InsertBooleanVariantsMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertBooleanVariantsMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertBooleanVariantsMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertBooleanVariantsWithHardcodedDefaultsMutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertBooleanVariantsWithHardcodedDefaultsMutation {
  override val operationName by InsertBooleanVariantsWithHardcodedDefaultsMutation.Companion::operationName
  override val dataDeserializer by InsertBooleanVariantsWithHardcodedDefaultsMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertBooleanVariantsWithHardcodedDefaultsMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertBooleanVariantsWithHardcodedDefaultsMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertFloatVariantsMutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertFloatVariantsMutation {
  override val operationName by InsertFloatVariantsMutation.Companion::operationName
  override val dataDeserializer by InsertFloatVariantsMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertFloatVariantsMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertFloatVariantsMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertFloatVariantsWithHardcodedDefaultsMutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertFloatVariantsWithHardcodedDefaultsMutation {
  override val operationName by InsertFloatVariantsWithHardcodedDefaultsMutation.Companion::operationName
  override val dataDeserializer by InsertFloatVariantsWithHardcodedDefaultsMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertFloatVariantsWithHardcodedDefaultsMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertFloatVariantsWithHardcodedDefaultsMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertFooMutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertFooMutation {
  override val operationName by InsertFooMutation.Companion::operationName
  override val dataDeserializer by InsertFooMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertFooMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertFooMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertInt64variantsMutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertInt64variantsMutation {
  override val operationName by InsertInt64variantsMutation.Companion::operationName
  override val dataDeserializer by InsertInt64variantsMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertInt64variantsMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertInt64variantsMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertInt64variantsWithHardcodedDefaultsMutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertInt64variantsWithHardcodedDefaultsMutation {
  override val operationName by InsertInt64variantsWithHardcodedDefaultsMutation.Companion::operationName
  override val dataDeserializer by InsertInt64variantsWithHardcodedDefaultsMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertInt64variantsWithHardcodedDefaultsMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertInt64variantsWithHardcodedDefaultsMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertIntVariantsMutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertIntVariantsMutation {
  override val operationName by InsertIntVariantsMutation.Companion::operationName
  override val dataDeserializer by InsertIntVariantsMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertIntVariantsMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertIntVariantsMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertIntVariantsWithHardcodedDefaultsMutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertIntVariantsWithHardcodedDefaultsMutation {
  override val operationName by InsertIntVariantsWithHardcodedDefaultsMutation.Companion::operationName
  override val dataDeserializer by InsertIntVariantsWithHardcodedDefaultsMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertIntVariantsWithHardcodedDefaultsMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertIntVariantsWithHardcodedDefaultsMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertManyToManyChildAMutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertManyToManyChildAMutation {
  override val operationName by InsertManyToManyChildAMutation.Companion::operationName
  override val dataDeserializer by InsertManyToManyChildAMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertManyToManyChildAMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertManyToManyChildAMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertManyToManyChildBMutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertManyToManyChildBMutation {
  override val operationName by InsertManyToManyChildBMutation.Companion::operationName
  override val dataDeserializer by InsertManyToManyChildBMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertManyToManyChildBMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertManyToManyChildBMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertManyToManyParentMutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertManyToManyParentMutation {
  override val operationName by InsertManyToManyParentMutation.Companion::operationName
  override val dataDeserializer by InsertManyToManyParentMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertManyToManyParentMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertManyToManyParentMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertManyToManySelfChildMutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertManyToManySelfChildMutation {
  override val operationName by InsertManyToManySelfChildMutation.Companion::operationName
  override val dataDeserializer by InsertManyToManySelfChildMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertManyToManySelfChildMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertManyToManySelfChildMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertManyToManySelfParentMutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertManyToManySelfParentMutation {
  override val operationName by InsertManyToManySelfParentMutation.Companion::operationName
  override val dataDeserializer by InsertManyToManySelfParentMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertManyToManySelfParentMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertManyToManySelfParentMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertManyToOneChildMutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertManyToOneChildMutation {
  override val operationName by InsertManyToOneChildMutation.Companion::operationName
  override val dataDeserializer by InsertManyToOneChildMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertManyToOneChildMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertManyToOneChildMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertManyToOneParentMutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertManyToOneParentMutation {
  override val operationName by InsertManyToOneParentMutation.Companion::operationName
  override val dataDeserializer by InsertManyToOneParentMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertManyToOneParentMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertManyToOneParentMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertManyToOneSelfCustomNameMutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertManyToOneSelfCustomNameMutation {
  override val operationName by InsertManyToOneSelfCustomNameMutation.Companion::operationName
  override val dataDeserializer by InsertManyToOneSelfCustomNameMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertManyToOneSelfCustomNameMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertManyToOneSelfCustomNameMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertManyToOneSelfMatchingNameMutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertManyToOneSelfMatchingNameMutation {
  override val operationName by InsertManyToOneSelfMatchingNameMutation.Companion::operationName
  override val dataDeserializer by InsertManyToOneSelfMatchingNameMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertManyToOneSelfMatchingNameMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertManyToOneSelfMatchingNameMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertNested1MutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertNested1Mutation {
  override val operationName by InsertNested1Mutation.Companion::operationName
  override val dataDeserializer by InsertNested1Mutation.Companion::dataDeserializer
  override val variablesSerializer by InsertNested1Mutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertNested1MutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertNested2MutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertNested2Mutation {
  override val operationName by InsertNested2Mutation.Companion::operationName
  override val dataDeserializer by InsertNested2Mutation.Companion::dataDeserializer
  override val variablesSerializer by InsertNested2Mutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertNested2MutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertNested3MutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertNested3Mutation {
  override val operationName by InsertNested3Mutation.Companion::operationName
  override val dataDeserializer by InsertNested3Mutation.Companion::dataDeserializer
  override val variablesSerializer by InsertNested3Mutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertNested3MutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertNonNullDateMutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertNonNullDateMutation {
  override val operationName by InsertNonNullDateMutation.Companion::operationName
  override val dataDeserializer by InsertNonNullDateMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertNonNullDateMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertNonNullDateMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertNonNullDatesWithDefaultsMutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertNonNullDatesWithDefaultsMutation {
  override val operationName by InsertNonNullDatesWithDefaultsMutation.Companion::operationName
  override val dataDeserializer by InsertNonNullDatesWithDefaultsMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertNonNullDatesWithDefaultsMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertNonNullDatesWithDefaultsMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertNonNullTimestampMutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertNonNullTimestampMutation {
  override val operationName by InsertNonNullTimestampMutation.Companion::operationName
  override val dataDeserializer by InsertNonNullTimestampMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertNonNullTimestampMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertNonNullTimestampMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertNonNullTimestampsWithDefaultsMutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertNonNullTimestampsWithDefaultsMutation {
  override val operationName by InsertNonNullTimestampsWithDefaultsMutation.Companion::operationName
  override val dataDeserializer by InsertNonNullTimestampsWithDefaultsMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertNonNullTimestampsWithDefaultsMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertNonNullTimestampsWithDefaultsMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertNonNullableListsMutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertNonNullableListsMutation {
  override val operationName by InsertNonNullableListsMutation.Companion::operationName
  override val dataDeserializer by InsertNonNullableListsMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertNonNullableListsMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertNonNullableListsMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertNullableDateMutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertNullableDateMutation {
  override val operationName by InsertNullableDateMutation.Companion::operationName
  override val dataDeserializer by InsertNullableDateMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertNullableDateMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertNullableDateMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertNullableDatesWithDefaultsMutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertNullableDatesWithDefaultsMutation {
  override val operationName by InsertNullableDatesWithDefaultsMutation.Companion::operationName
  override val dataDeserializer by InsertNullableDatesWithDefaultsMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertNullableDatesWithDefaultsMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertNullableDatesWithDefaultsMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertNullableListsMutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertNullableListsMutation {
  override val operationName by InsertNullableListsMutation.Companion::operationName
  override val dataDeserializer by InsertNullableListsMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertNullableListsMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertNullableListsMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertNullableTimestampMutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertNullableTimestampMutation {
  override val operationName by InsertNullableTimestampMutation.Companion::operationName
  override val dataDeserializer by InsertNullableTimestampMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertNullableTimestampMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertNullableTimestampMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertNullableTimestampsWithDefaultsMutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertNullableTimestampsWithDefaultsMutation {
  override val operationName by InsertNullableTimestampsWithDefaultsMutation.Companion::operationName
  override val dataDeserializer by InsertNullableTimestampsWithDefaultsMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertNullableTimestampsWithDefaultsMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertNullableTimestampsWithDefaultsMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertOptionalStringsMutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertOptionalStringsMutation {
  override val operationName by InsertOptionalStringsMutation.Companion::operationName
  override val dataDeserializer by InsertOptionalStringsMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertOptionalStringsMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertOptionalStringsMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertPrimaryKeyIsCompositeMutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertPrimaryKeyIsCompositeMutation {
  override val operationName by InsertPrimaryKeyIsCompositeMutation.Companion::operationName
  override val dataDeserializer by InsertPrimaryKeyIsCompositeMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertPrimaryKeyIsCompositeMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertPrimaryKeyIsCompositeMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertPrimaryKeyIsDateMutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertPrimaryKeyIsDateMutation {
  override val operationName by InsertPrimaryKeyIsDateMutation.Companion::operationName
  override val dataDeserializer by InsertPrimaryKeyIsDateMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertPrimaryKeyIsDateMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertPrimaryKeyIsDateMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertPrimaryKeyIsFloatMutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertPrimaryKeyIsFloatMutation {
  override val operationName by InsertPrimaryKeyIsFloatMutation.Companion::operationName
  override val dataDeserializer by InsertPrimaryKeyIsFloatMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertPrimaryKeyIsFloatMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertPrimaryKeyIsFloatMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertPrimaryKeyIsIntMutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertPrimaryKeyIsIntMutation {
  override val operationName by InsertPrimaryKeyIsIntMutation.Companion::operationName
  override val dataDeserializer by InsertPrimaryKeyIsIntMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertPrimaryKeyIsIntMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertPrimaryKeyIsIntMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertPrimaryKeyIsInt64MutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertPrimaryKeyIsInt64Mutation {
  override val operationName by InsertPrimaryKeyIsInt64Mutation.Companion::operationName
  override val dataDeserializer by InsertPrimaryKeyIsInt64Mutation.Companion::dataDeserializer
  override val variablesSerializer by InsertPrimaryKeyIsInt64Mutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertPrimaryKeyIsInt64MutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertPrimaryKeyIsStringMutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertPrimaryKeyIsStringMutation {
  override val operationName by InsertPrimaryKeyIsStringMutation.Companion::operationName
  override val dataDeserializer by InsertPrimaryKeyIsStringMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertPrimaryKeyIsStringMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertPrimaryKeyIsStringMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertPrimaryKeyIsTimestampMutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertPrimaryKeyIsTimestampMutation {
  override val operationName by InsertPrimaryKeyIsTimestampMutation.Companion::operationName
  override val dataDeserializer by InsertPrimaryKeyIsTimestampMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertPrimaryKeyIsTimestampMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertPrimaryKeyIsTimestampMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertPrimaryKeyIsUuidMutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertPrimaryKeyIsUuidMutation {
  override val operationName by InsertPrimaryKeyIsUuidMutation.Companion::operationName
  override val dataDeserializer by InsertPrimaryKeyIsUuidMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertPrimaryKeyIsUuidMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertPrimaryKeyIsUuidMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertPrimaryKeyNested1MutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertPrimaryKeyNested1Mutation {
  override val operationName by InsertPrimaryKeyNested1Mutation.Companion::operationName
  override val dataDeserializer by InsertPrimaryKeyNested1Mutation.Companion::dataDeserializer
  override val variablesSerializer by InsertPrimaryKeyNested1Mutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertPrimaryKeyNested1MutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertPrimaryKeyNested2MutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertPrimaryKeyNested2Mutation {
  override val operationName by InsertPrimaryKeyNested2Mutation.Companion::operationName
  override val dataDeserializer by InsertPrimaryKeyNested2Mutation.Companion::dataDeserializer
  override val variablesSerializer by InsertPrimaryKeyNested2Mutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertPrimaryKeyNested2MutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertPrimaryKeyNested3MutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertPrimaryKeyNested3Mutation {
  override val operationName by InsertPrimaryKeyNested3Mutation.Companion::operationName
  override val dataDeserializer by InsertPrimaryKeyNested3Mutation.Companion::dataDeserializer
  override val variablesSerializer by InsertPrimaryKeyNested3Mutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertPrimaryKeyNested3MutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertPrimaryKeyNested4MutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertPrimaryKeyNested4Mutation {
  override val operationName by InsertPrimaryKeyNested4Mutation.Companion::operationName
  override val dataDeserializer by InsertPrimaryKeyNested4Mutation.Companion::dataDeserializer
  override val variablesSerializer by InsertPrimaryKeyNested4Mutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertPrimaryKeyNested4MutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertPrimaryKeyNested5MutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertPrimaryKeyNested5Mutation {
  override val operationName by InsertPrimaryKeyNested5Mutation.Companion::operationName
  override val dataDeserializer by InsertPrimaryKeyNested5Mutation.Companion::dataDeserializer
  override val variablesSerializer by InsertPrimaryKeyNested5Mutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertPrimaryKeyNested5MutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertPrimaryKeyNested6MutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertPrimaryKeyNested6Mutation {
  override val operationName by InsertPrimaryKeyNested6Mutation.Companion::operationName
  override val dataDeserializer by InsertPrimaryKeyNested6Mutation.Companion::dataDeserializer
  override val variablesSerializer by InsertPrimaryKeyNested6Mutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertPrimaryKeyNested6MutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertPrimaryKeyNested7MutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertPrimaryKeyNested7Mutation {
  override val operationName by InsertPrimaryKeyNested7Mutation.Companion::operationName
  override val dataDeserializer by InsertPrimaryKeyNested7Mutation.Companion::dataDeserializer
  override val variablesSerializer by InsertPrimaryKeyNested7Mutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertPrimaryKeyNested7MutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertStringVariantsMutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertStringVariantsMutation {
  override val operationName by InsertStringVariantsMutation.Companion::operationName
  override val dataDeserializer by InsertStringVariantsMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertStringVariantsMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertStringVariantsMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertStringVariantsWithHardcodedDefaultsMutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertStringVariantsWithHardcodedDefaultsMutation {
  override val operationName by InsertStringVariantsWithHardcodedDefaultsMutation.Companion::operationName
  override val dataDeserializer by InsertStringVariantsWithHardcodedDefaultsMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertStringVariantsWithHardcodedDefaultsMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertStringVariantsWithHardcodedDefaultsMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertSyntheticIdMutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertSyntheticIdMutation {
  override val operationName by InsertSyntheticIdMutation.Companion::operationName
  override val dataDeserializer by InsertSyntheticIdMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertSyntheticIdMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertSyntheticIdMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertUuidVariantsMutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertUuidVariantsMutation {
  override val operationName by InsertUuidVariantsMutation.Companion::operationName
  override val dataDeserializer by InsertUuidVariantsMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertUuidVariantsMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertUuidVariantsMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class InsertUuidVariantsWithHardcodedDefaultsMutationImpl(
    override val connector: DemoConnectorImpl
  ) : InsertUuidVariantsWithHardcodedDefaultsMutation {
  override val operationName by InsertUuidVariantsWithHardcodedDefaultsMutation.Companion::operationName
  override val dataDeserializer by InsertUuidVariantsWithHardcodedDefaultsMutation.Companion::dataDeserializer
  override val variablesSerializer by InsertUuidVariantsWithHardcodedDefaultsMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "InsertUuidVariantsWithHardcodedDefaultsMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class UpdateBooleanVariantsByKeyMutationImpl(
    override val connector: DemoConnectorImpl
  ) : UpdateBooleanVariantsByKeyMutation {
  override val operationName by UpdateBooleanVariantsByKeyMutation.Companion::operationName
  override val dataDeserializer by UpdateBooleanVariantsByKeyMutation.Companion::dataDeserializer
  override val variablesSerializer by UpdateBooleanVariantsByKeyMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "UpdateBooleanVariantsByKeyMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class UpdateFloatVariantsByKeyMutationImpl(
    override val connector: DemoConnectorImpl
  ) : UpdateFloatVariantsByKeyMutation {
  override val operationName by UpdateFloatVariantsByKeyMutation.Companion::operationName
  override val dataDeserializer by UpdateFloatVariantsByKeyMutation.Companion::dataDeserializer
  override val variablesSerializer by UpdateFloatVariantsByKeyMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "UpdateFloatVariantsByKeyMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class UpdateFooMutationImpl(
    override val connector: DemoConnectorImpl
  ) : UpdateFooMutation {
  override val operationName by UpdateFooMutation.Companion::operationName
  override val dataDeserializer by UpdateFooMutation.Companion::dataDeserializer
  override val variablesSerializer by UpdateFooMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "UpdateFooMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class UpdateFoosByBarMutationImpl(
    override val connector: DemoConnectorImpl
  ) : UpdateFoosByBarMutation {
  override val operationName by UpdateFoosByBarMutation.Companion::operationName
  override val dataDeserializer by UpdateFoosByBarMutation.Companion::dataDeserializer
  override val variablesSerializer by UpdateFoosByBarMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "UpdateFoosByBarMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class UpdateInt64variantsByKeyMutationImpl(
    override val connector: DemoConnectorImpl
  ) : UpdateInt64variantsByKeyMutation {
  override val operationName by UpdateInt64variantsByKeyMutation.Companion::operationName
  override val dataDeserializer by UpdateInt64variantsByKeyMutation.Companion::dataDeserializer
  override val variablesSerializer by UpdateInt64variantsByKeyMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "UpdateInt64variantsByKeyMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class UpdateIntVariantsByKeyMutationImpl(
    override val connector: DemoConnectorImpl
  ) : UpdateIntVariantsByKeyMutation {
  override val operationName by UpdateIntVariantsByKeyMutation.Companion::operationName
  override val dataDeserializer by UpdateIntVariantsByKeyMutation.Companion::dataDeserializer
  override val variablesSerializer by UpdateIntVariantsByKeyMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "UpdateIntVariantsByKeyMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class UpdateNonNullDateMutationImpl(
    override val connector: DemoConnectorImpl
  ) : UpdateNonNullDateMutation {
  override val operationName by UpdateNonNullDateMutation.Companion::operationName
  override val dataDeserializer by UpdateNonNullDateMutation.Companion::dataDeserializer
  override val variablesSerializer by UpdateNonNullDateMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "UpdateNonNullDateMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class UpdateNonNullTimestampMutationImpl(
    override val connector: DemoConnectorImpl
  ) : UpdateNonNullTimestampMutation {
  override val operationName by UpdateNonNullTimestampMutation.Companion::operationName
  override val dataDeserializer by UpdateNonNullTimestampMutation.Companion::dataDeserializer
  override val variablesSerializer by UpdateNonNullTimestampMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "UpdateNonNullTimestampMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class UpdateNonNullableListsByKeyMutationImpl(
    override val connector: DemoConnectorImpl
  ) : UpdateNonNullableListsByKeyMutation {
  override val operationName by UpdateNonNullableListsByKeyMutation.Companion::operationName
  override val dataDeserializer by UpdateNonNullableListsByKeyMutation.Companion::dataDeserializer
  override val variablesSerializer by UpdateNonNullableListsByKeyMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "UpdateNonNullableListsByKeyMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class UpdateNullableDateMutationImpl(
    override val connector: DemoConnectorImpl
  ) : UpdateNullableDateMutation {
  override val operationName by UpdateNullableDateMutation.Companion::operationName
  override val dataDeserializer by UpdateNullableDateMutation.Companion::dataDeserializer
  override val variablesSerializer by UpdateNullableDateMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "UpdateNullableDateMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class UpdateNullableListsByKeyMutationImpl(
    override val connector: DemoConnectorImpl
  ) : UpdateNullableListsByKeyMutation {
  override val operationName by UpdateNullableListsByKeyMutation.Companion::operationName
  override val dataDeserializer by UpdateNullableListsByKeyMutation.Companion::dataDeserializer
  override val variablesSerializer by UpdateNullableListsByKeyMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "UpdateNullableListsByKeyMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class UpdateNullableTimestampMutationImpl(
    override val connector: DemoConnectorImpl
  ) : UpdateNullableTimestampMutation {
  override val operationName by UpdateNullableTimestampMutation.Companion::operationName
  override val dataDeserializer by UpdateNullableTimestampMutation.Companion::dataDeserializer
  override val variablesSerializer by UpdateNullableTimestampMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "UpdateNullableTimestampMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class UpdateStringVariantsByKeyMutationImpl(
    override val connector: DemoConnectorImpl
  ) : UpdateStringVariantsByKeyMutation {
  override val operationName by UpdateStringVariantsByKeyMutation.Companion::operationName
  override val dataDeserializer by UpdateStringVariantsByKeyMutation.Companion::dataDeserializer
  override val variablesSerializer by UpdateStringVariantsByKeyMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "UpdateStringVariantsByKeyMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class UpdateUuidVariantsByKeyMutationImpl(
    override val connector: DemoConnectorImpl
  ) : UpdateUuidVariantsByKeyMutation {
  override val operationName by UpdateUuidVariantsByKeyMutation.Companion::operationName
  override val dataDeserializer by UpdateUuidVariantsByKeyMutation.Companion::dataDeserializer
  override val variablesSerializer by UpdateUuidVariantsByKeyMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "UpdateUuidVariantsByKeyMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class UpsertFooMutationImpl(
    override val connector: DemoConnectorImpl
  ) : UpsertFooMutation {
  override val operationName by UpsertFooMutation.Companion::operationName
  override val dataDeserializer by UpsertFooMutation.Companion::dataDeserializer
  override val variablesSerializer by UpsertFooMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "UpsertFooMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

  private class UpsertHardcodedFooMutationImpl(
    override val connector: DemoConnectorImpl
  ) : UpsertHardcodedFooMutation {
  override val operationName by UpsertHardcodedFooMutation.Companion::operationName
  override val dataDeserializer by UpsertHardcodedFooMutation.Companion::dataDeserializer
  override val variablesSerializer by UpsertHardcodedFooMutation.Companion::variablesSerializer

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString() = "UpsertHardcodedFooMutationImpl(" +
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
