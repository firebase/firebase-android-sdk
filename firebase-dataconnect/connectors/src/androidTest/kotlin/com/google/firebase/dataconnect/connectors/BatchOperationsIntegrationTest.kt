/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.dataconnect.connectors

import com.google.firebase.dataconnect.OptionalVariable
import com.google.firebase.dataconnect.connectors.batch.BatchConnector
import com.google.firebase.dataconnect.connectors.batch.BatchOrderData
import com.google.firebase.dataconnect.connectors.batch.BatchOrderItemData
import com.google.firebase.dataconnect.connectors.batch.BatchOrderKey
import com.google.firebase.dataconnect.connectors.batch.BatchProductData
import com.google.firebase.dataconnect.connectors.batch.BatchProductKey
import com.google.firebase.dataconnect.connectors.batch.GetBatchOrderQuery
import com.google.firebase.dataconnect.connectors.batch.GetBatchProductQuery
import com.google.firebase.dataconnect.connectors.batch.InsertBatchProductMutation
import com.google.firebase.dataconnect.connectors.batch.execute
import com.google.firebase.dataconnect.connectors.testutil.TestBatchConnectorFactory
import com.google.firebase.dataconnect.testutil.DataConnectIntegrationTestBase
import com.google.firebase.dataconnect.testutil.property.arbitrary.DataConnectArb.FloatRoundTrip
import com.google.firebase.dataconnect.testutil.property.arbitrary.SumPartitionArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.pair
import com.google.firebase.dataconnect.testutil.registerDataConnectKotestTestutilPrinters
import io.kotest.assertions.print.print
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import java.util.UUID
import kotlin.math.absoluteValue
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class BatchOperationsIntegrationTest : DataConnectIntegrationTestBase() {

  @get:Rule
  val batchConnectorFactory = TestBatchConnectorFactory(firebaseAppFactory, dataConnectFactory)

  private val connector: BatchConnector by lazy { batchConnectorFactory.newInstance() }

  @Before
  fun registerPrinters() {
    registerDataConnectKotestTestutilPrinters()
  }

  @Test
  fun insertBatchProduct_singleData_defaultGeneratedId() = runTest {
    checkAll(propTestConfig, Arbs.productName(), Arbs.price()) { name, price ->
      val variables =
        BatchProductData.build(name = name) {
          if (price is OptionalVariable.Value<FloatRoundTrip?>) {
            this.price = price.value?.float
          }
        }

      val mutationResult = connector.insertBatchProduct.execute(variables)

      val key = mutationResult.data.key
      connector.shouldContainBatchProduct(id = key.id, name = name, price = price)
    }
  }

  @Test
  fun insertBatchProductWithId_singleData_explicitId() = runTest {
    checkAll(propTestConfig, Arbs.productName(), Arbs.price()) { name, price ->
      val id = UUID.randomUUID()
      val variables =
        BatchProductData.build(name = name) {
          this.id = id
          if (price is OptionalVariable.Value<FloatRoundTrip?>) {
            this.price = price.value?.float
          }
        }

      val mutationResult = connector.insertBatchProductWithId.execute(variables)

      val key = mutationResult.data.key
      key.id shouldBe id
      connector.shouldContainBatchProduct(id = id, name = name, price = price)
    }
  }

  @Test
  fun insertBatchProducts_batchData_defaultGeneratedIds() = runTest {
    checkAll(propTestConfig, Arb.list(Arbs.productNamePricePair(), 0..5)) { productNamePricePairs ->
      val batchProductDataList =
        productNamePricePairs.map { (name, price) ->
          when (price) {
            OptionalVariable.Undefined -> BatchProductData.build(name = name)
            is OptionalVariable.Value<FloatRoundTrip?> ->
              BatchProductData.build(name = name) { this.price = price.value?.float }
          }
        }

      val mutationResult = connector.insertBatchProducts.execute(batchProductDataList)

      withClue("mutationResult.data.keys=${mutationResult.data.keys.print().value}") {
        mutationResult.data.keys shouldHaveSize productNamePricePairs.size
      }
      connector.shouldContainBatchProducts(
        productIds = mutationResult.data.keys.map { it.id },
        productNamePricePairs,
      )
    }
  }

  @Test
  fun insertBatchProductsWithIds_batchData_explicitIds() = runTest {
    checkAll(propTestConfig, Arb.list(Arbs.productNamePricePair(), 0..5)) { productNamePricePairs ->
      val ids = List(productNamePricePairs.size) { UUID.randomUUID() }
      val batchProductDataList =
        productNamePricePairs.mapIndexed { index, (name, price) ->
          BatchProductData.build(name = name) {
            this.id = ids[index]
            if (price is OptionalVariable.Value<FloatRoundTrip?>) {
              this.price = price.value?.float
            }
          }
        }

      val mutationResult = connector.insertBatchProductsWithIds.execute(batchProductDataList)

      withClue("mutationResult.data.keys") {
        mutationResult.data.keys.map { it.id } shouldContainExactlyInAnyOrder ids
      }
      connector.shouldContainBatchProducts(
        productIds = ids,
        productNamePricePairs,
      )
    }
  }

  @Test
  fun insertBatchOrderWithItems_singleParentWithNestedChildren() = runTest {
    checkAll(
      propTestConfig,
      Arbs.customerNameTotalAmountPair(),
      Arb.list(Arbs.itemDescriptionQuantityPriceTuple(), 0..5)
    ) { (customerName, totalAmount), itemDescriptionQuantityPriceTuples ->
      val batchOrderId = UUID.randomUUID()
      val itemDataIds = List(itemDescriptionQuantityPriceTuples.size) { UUID.randomUUID() }
      val batchOrderData =
        BatchOrderData.build(customerName = customerName) {
          this.id = batchOrderId
          if (totalAmount is OptionalVariable.Value<FloatRoundTrip?>) {
            this.totalAmount = totalAmount.value?.float
          }
          this.batchOrderItems_on_batchOrder =
            itemDescriptionQuantityPriceTuples.zip(itemDataIds).map { (tuple, id) ->
              val (description, quantity, price) = tuple
              BatchOrderItemData.buildForBatchOrderItemsOnBatchOrder(
                itemDescription = description,
                quantity = quantity,
              ) {
                this.id = id
                if (price is OptionalVariable.Value<FloatRoundTrip?>) {
                  this.price = price.value?.float
                }
              }
            }
        }

      val mutationResult = connector.insertBatchOrderWithItems.execute(batchOrderData)

      mutationResult.data.key shouldBe BatchOrderKey(batchOrderId)
      connector.shouldContainBatchOrder(
        batchOrderId = batchOrderId,
        customerName = customerName,
        totalAmount = totalAmount,
        itemIds = itemDataIds,
        items = itemDescriptionQuantityPriceTuples,
      )
    }
  }

  @Test
  fun insertBatchOrdersWithItems_batchParentsWithNestedChildren() = runTest {
    // NOTE: maxListCount must match the "maxCount" argument to the @allow directive in graphql.
    checkAll(propTestConfig, Arbs.batchOrdersArb(maxListCount = 10)) { batchOrderList ->
      val batchOrderIds = List(batchOrderList.size) { UUID.randomUUID() }
      val itemDataIdLists = batchOrderList.map { List(it.items.size) { UUID.randomUUID() } }
      val batchOrderDataList =
        batchOrderList.mapIndexed {
          batchOrderListIndex,
          (customerNameTotalAmountPair, itemDescriptionQuantityPriceTuples) ->
          val (customerName, totalAmount) = customerNameTotalAmountPair
          BatchOrderData.build(customerName = customerName) {
            this.id = batchOrderIds[batchOrderListIndex]
            if (totalAmount is OptionalVariable.Value<FloatRoundTrip?>) {
              this.totalAmount = totalAmount.value?.float
            }

            this.batchOrderItems_on_batchOrder =
              itemDescriptionQuantityPriceTuples.zip(itemDataIdLists[batchOrderListIndex]).map {
                (tuple, id) ->
                val (description, quantity, price) = tuple
                BatchOrderItemData.buildForBatchOrderItemsOnBatchOrder(
                  itemDescription = description,
                  quantity = quantity,
                ) {
                  this.id = id
                  if (price is OptionalVariable.Value<FloatRoundTrip?>) {
                    this.price = price.value?.float
                  }
                }
              }
          }
        }

      val mutationResult = connector.insertBatchOrdersWithItems.execute(batchOrderDataList)

      withClue("mutationResult.data.keys") {
        mutationResult.data.keys.map { it.id } shouldContainExactlyInAnyOrder batchOrderIds
      }
      connector.shouldContainBatchOrders(
        batchOrderIds = batchOrderIds,
        batchOrders = batchOrderList,
        itemDataIdLists = itemDataIdLists,
      )
    }
  }

  @Test
  fun upsert_singleRow_insert() = runTest {
    checkAll(propTestConfig, Arbs.productNamePricePair()) { (name, price) ->
      val id = UUID.randomUUID()
      val variables =
        BatchProductData.build(name = name) {
          this.id = id
          if (price is OptionalVariable.Value<FloatRoundTrip?>) {
            this.price = price.value?.float
          }
        }

      val upsertResult = connector.upsertBatchProduct.execute(variables)

      upsertResult.data.key shouldBe BatchProductKey(id)
      connector.shouldContainBatchProduct(id = id, name = name, price = price)
    }
  }

  @Test
  fun upsert_multipleRows_insert() = runTest {
    checkAll(propTestConfig, Arb.list(Arbs.productNamePricePair(), 0..5)) { productNamePricePairs ->
      val ids = List(productNamePricePairs.size) { UUID.randomUUID() }
      val variables =
        productNamePricePairs.mapIndexed { index, (name, price) ->
          BatchProductData.build(name = name) {
            this.id = ids[index]
            if (price is OptionalVariable.Value<FloatRoundTrip?>) {
              this.price = price.value?.float
            }
          }
        }

      val upsertResult = connector.upsertBatchProducts.execute(variables)

      upsertResult.data.keys shouldContainExactly ids.map(::BatchProductKey)
      connector.shouldContainBatchProducts(productIds = ids, productNamePricePairs)
    }
  }

  @Test
  fun upsert_singleRow_update() = runTest {
    checkAll(propTestConfig, Arbs.productNamePricePair().pair()) {
      (productNamePricePair1, productNamePricePair2) ->
      val id = connector.insertBatchProduct.execute(productNamePricePair1).data.key.id
      val variables =
        BatchProductData.build(name = productNamePricePair2.name) {
          this.id = id
          if (productNamePricePair2.price is OptionalVariable.Value<FloatRoundTrip?>) {
            this.price = productNamePricePair2.price.value?.float
          }
        }

      val upsertResult = connector.upsertBatchProduct.execute(variables)

      upsertResult.data.key shouldBe BatchProductKey(id)
      val expected = productNamePricePair1.withUpsertApplied(productNamePricePair2)
      connector.shouldContainBatchProduct(id = id, name = expected.name, price = expected.price)
    }
  }

  @Test
  fun updateBatchProduct_and_updateBatchOrder() = runTest {
    val productId = UUID.randomUUID()
    val orderId = UUID.randomUUID()

    // Create product and order
    connector.insertBatchProductWithId.execute(
      data =
        BatchProductData.build(name = "Initial Product") {
          this.id = productId
          this.price = 50.0
        }
    )
    connector.insertBatchOrderWithId.execute(
      data =
        BatchOrderData.build(customerName = "Initial Customer") {
          this.id = orderId
          this.totalAmount = 100.0
        }
    )

    // Update product using buildUpdate
    val productUpdateResult =
      connector.updateBatchProduct.execute(
        id = productId,
        data =
          BatchProductData.buildUpdate {
            this.name = "Modified Product Name"
            this.price = 75.0
          }
      )
    productUpdateResult.data.batchProduct_update shouldBe BatchProductKey(id = productId)
    val updatedProduct =
      connector.getBatchProduct.execute(productId).data.batchProduct.shouldNotBeNull()
    updatedProduct.name shouldBe "Modified Product Name"
    updatedProduct.price shouldBe 75.0

    // Update order using buildUpdate
    val orderUpdateResult =
      connector.updateBatchOrder.execute(
        key = BatchOrderKey(orderId),
        data =
          BatchOrderData.buildUpdate {
            this.customerName = "Modified Customer Name"
            this.totalAmount = 150.0
          }
      )
    orderUpdateResult.data.batchOrder_update shouldBe BatchOrderKey(id = orderId)
    val updatedOrder = connector.getBatchOrder.execute(orderId).data.batchOrder.shouldNotBeNull()
    updatedOrder.customerName shouldBe "Modified Customer Name"
    updatedOrder.totalAmount shouldBe 150.0
  }
}

@OptIn(ExperimentalKotest::class)
private val propTestConfig =
  PropTestConfig(
    iterations = 5,
    edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2),
    shrinkingMode = ShrinkingMode.Off,
  )

private data class ProductNamePricePair(
  val name: String,
  val price: OptionalVariable<FloatRoundTrip?>,
) {
  override fun toString() =
    "ProductNamePricePair(name=${name.print().value}, price=${price.print().value})"
}

private fun ProductNamePricePair.withUpsertApplied(
  upsert: ProductNamePricePair
): ProductNamePricePair =
  ProductNamePricePair(
    name = upsert.name,
    price =
      when (upsert.price) {
        OptionalVariable.Undefined -> price
        is OptionalVariable.Value<*> -> upsert.price
      },
  )

private data class CustomerNameTotalAmountPair(
  val name: String,
  val totalAmount: OptionalVariable<FloatRoundTrip?>,
) {
  override fun toString() =
    "CustomerNameTotalAmountPair(name=${name.print().value}, " +
      "totalAmount=${totalAmount.print().value})"
}

private data class ItemDescriptionQuantityPriceTuple(
  val description: String,
  val quantity: Int,
  val price: OptionalVariable<FloatRoundTrip?>,
) {
  override fun toString() =
    "ItemDescriptionQuantityPriceTuple(description=${description.print().value}, " +
      "quantity=${quantity.print().value}, price=${price.print().value})"
}

private data class BatchOrder(
  val customerNameTotalAmountPair: CustomerNameTotalAmountPair,
  val items: List<ItemDescriptionQuantityPriceTuple>,
)

private object Arbs {

  fun productName(): Arb<String> = Arb.dataConnect.id().map { "ProductName_$it" }

  fun price(): Arb<OptionalVariable<FloatRoundTrip?>> =
    Arb.dataConnect.nullableOptionalVariable(
      Arb.dataConnect.float(),
      undefinedProbability = 0.33,
      nullableProbability = 0.33
    )

  fun productNamePricePair(
    productName: Arb<String> = productName(),
    price: Arb<OptionalVariable<FloatRoundTrip?>> = price(),
  ): Arb<ProductNamePricePair> = Arb.bind(productName, price, ::ProductNamePricePair)

  fun customerName(): Arb<String> = Arb.dataConnect.id().map { "CustomerName_$it" }

  fun totalAmount(): Arb<OptionalVariable<FloatRoundTrip?>> =
    Arb.dataConnect.nullableOptionalVariable(
      Arb.dataConnect.float(),
      undefinedProbability = 0.33,
      nullableProbability = 0.33
    )

  fun customerNameTotalAmountPair(
    customerName: Arb<String> = customerName(),
    totalAmount: Arb<OptionalVariable<FloatRoundTrip?>> = totalAmount(),
  ): Arb<CustomerNameTotalAmountPair> =
    Arb.bind(customerName, totalAmount, ::CustomerNameTotalAmountPair)

  fun itemDescription(): Arb<String> = Arb.dataConnect.id().map { "ItemDescription_$it" }

  fun quantity(): Arb<Int> = Arb.int(1..100)

  fun itemDescriptionQuantityPriceTuple(
    itemDescription: Arb<String> = itemDescription(),
    quantity: Arb<Int> = quantity(),
    price: Arb<OptionalVariable<FloatRoundTrip?>> = price(),
  ): Arb<ItemDescriptionQuantityPriceTuple> =
    Arb.bind(itemDescription, quantity, price, ::ItemDescriptionQuantityPriceTuple)

  fun batchOrdersArb(
    maxListCount: Int,
    customerNameTotalAmountPair: Arb<CustomerNameTotalAmountPair> = customerNameTotalAmountPair(),
    itemDescriptionQuantityPriceTuple: Arb<ItemDescriptionQuantityPriceTuple> =
      itemDescriptionQuantityPriceTuple(),
  ): Arb<List<BatchOrder>> {
    require(maxListCount >= 0) { "invalid maxListCount: $maxListCount" }
    val listCountArb = Arb.int(0..maxListCount)
    return arbitrary {
      val curMaxListCount = listCountArb.bind()
      val batchOrderCount = Arb.int(0..curMaxListCount).bind()
      if (batchOrderCount == 0) {
        emptyList()
      } else {
        val totalItemCount = curMaxListCount - batchOrderCount
        val itemListSizesArb = SumPartitionArb(sum = totalItemCount, summandCount = batchOrderCount)
        val itemLists =
          itemListSizesArb.bind().summands.map {
            Arb.list(itemDescriptionQuantityPriceTuple, it..it).bind()
          }
        List(batchOrderCount) { batchOrderIndex ->
          val customerNameTotalAmountPair = customerNameTotalAmountPair.bind()
          val items = itemLists[batchOrderIndex]
          BatchOrder(customerNameTotalAmountPair, items)
        }
      }
    }
  }
}

private suspend fun InsertBatchProductMutation.execute(pair: ProductNamePricePair) =
  execute(
    BatchProductData.build(name = pair.name) {
      if (pair.price is OptionalVariable.Value<FloatRoundTrip?>) {
        this.price = pair.price.value?.float
      }
    }
  )

private suspend fun GetBatchOrderQuery.execute(id: UUID) = execute(BatchOrderKey(id))

private suspend fun GetBatchProductQuery.execute(id: UUID) = execute(BatchProductKey(id))

private fun batchProductFromComponents(id: UUID, productNamePricePair: ProductNamePricePair) =
  batchProductFromComponents(
    id = id,
    name = productNamePricePair.name,
    price = productNamePricePair.price
  )

private fun batchProductFromComponents(
  id: UUID,
  name: String,
  price: OptionalVariable<FloatRoundTrip?>
) =
  GetBatchProductQuery.Data.BatchProduct(
    id = id,
    name = name,
    price = price.valueOrNull()?.roundTripFloat,
  )

private suspend fun BatchConnector.shouldContainBatchProduct(
  id: UUID,
  name: String,
  price: OptionalVariable<FloatRoundTrip?>,
) {
  val queryResult = getBatchProduct.execute(id)

  val expectedProduct: GetBatchProductQuery.Data.BatchProduct =
    batchProductFromComponents(id = id, name = name, price = price)

  queryResult.data shouldBe GetBatchProductQuery.Data(expectedProduct)
}

private suspend fun BatchConnector.shouldContainBatchProducts(
  productIds: List<UUID>,
  productNamePricePairs: List<ProductNamePricePair>
) {
  require(productIds.size == productNamePricePairs.size) {
    "productIds.size must equal productNamePricePairs.size, but they are unequal: " +
      "productIds.size=${productIds.size}, " +
      "productNamePricePairs.size=${productNamePricePairs.size}, " +
      "difference=${(productNamePricePairs.size-productIds.size).absoluteValue}"
  }

  val products: List<GetBatchProductQuery.Data.BatchProduct> =
    productIds.mapIndexedNotNull { index, id ->
      withClue("getBatchProduct.execute(id=$id) index=$index size=${productIds.size}") {
        getBatchProduct.execute(id).data.batchProduct
      }
    }

  val expectedProducts: List<GetBatchProductQuery.Data.BatchProduct> =
    productIds.zip(productNamePricePairs).map { (id, productNamePricePair) ->
      batchProductFromComponents(id, productNamePricePair)
    }

  products shouldContainExactly expectedProducts
}

private fun batchOrderFromComponents(
  batchOrderId: UUID,
  customerName: String,
  totalAmount: OptionalVariable<FloatRoundTrip?>,
  itemIds: List<UUID>,
  items: List<ItemDescriptionQuantityPriceTuple>,
) =
  GetBatchOrderQuery.Data.BatchOrder(
    id = batchOrderId,
    customerName = customerName,
    totalAmount = totalAmount.valueOrNull()?.roundTripFloat,
    items =
      itemIds.zip(items).map { (id, itemDescriptionQuantityPriceTuple) ->
        val (description, quantity, price) = itemDescriptionQuantityPriceTuple
        GetBatchOrderQuery.Data.BatchOrder.ItemsItem(
          id = id,
          itemDescription = description,
          quantity = quantity,
          price = price.valueOrNull()?.roundTripFloat,
        )
      }
  )

private suspend fun BatchConnector.shouldContainBatchOrder(
  batchOrderId: UUID,
  customerName: String,
  totalAmount: OptionalVariable<FloatRoundTrip?>,
  itemIds: List<UUID>,
  items: List<ItemDescriptionQuantityPriceTuple>,
) {
  require(itemIds.size == items.size) {
    "itemIds.size must equal items.size, but they are unequal: " +
      "itemIds.size=${itemIds.size}, items.size=${items.size}, " +
      "difference=${(items.size-itemIds.size).absoluteValue}"
  }

  val queryResult = getBatchOrder.execute(batchOrderId)

  val expectedBatchOrder =
    batchOrderFromComponents(
      batchOrderId = batchOrderId,
      customerName = customerName,
      totalAmount = totalAmount,
      itemIds = itemIds,
      items = items,
    )

  queryResult.data shouldBe GetBatchOrderQuery.Data(expectedBatchOrder)
}

private suspend fun BatchConnector.shouldContainBatchOrders(
  batchOrderIds: List<UUID>,
  batchOrders: List<BatchOrder>,
  itemDataIdLists: List<List<UUID>>,
) {
  require(batchOrderIds.size == batchOrders.size) {
    "batchOrderIds.size must equal batchOrders.size, but they are unequal: " +
      "batchOrderIds.size=${batchOrderIds.size}, batchOrders.size=${batchOrders.size}, " +
      "difference=${(batchOrders.size-batchOrderIds.size).absoluteValue}"
  }
  require(batchOrderIds.size == itemDataIdLists.size) {
    "batchOrderIds.size must equal itemDataIdLists.size, but they are unequal: " +
      "batchOrderIds.size=${batchOrderIds.size}, itemDataIdLists.size=${itemDataIdLists.size}, " +
      "difference=${(itemDataIdLists.size-batchOrderIds.size).absoluteValue}"
  }
  batchOrders.zip(itemDataIdLists).forEachIndexed { index, (batchOrder, itemDataIdList) ->
    require(batchOrder.items.size == itemDataIdList.size) {
      "batchOrders[$index].items.size must equal itemDataIdLists[$index].size, " +
        "but they are unequal: "
      "batchOrders[$index].items.size=${batchOrder.items.size}, " +
        "itemDataIdLists[$index].size=${itemDataIdList.size}, " +
        "difference=${(batchOrder.items.size-itemDataIdList.size).absoluteValue}"
    }
  }

  val orders: List<GetBatchOrderQuery.Data.BatchOrder> =
    batchOrderIds.mapIndexedNotNull { index, id ->
      withClue("getBatchOrder.execute(id=$id) index=$index size=${batchOrderIds.size}") {
        getBatchOrder.execute(id).data.batchOrder
      }
    }

  val expectedOrders: List<GetBatchOrderQuery.Data.BatchOrder> =
    List(batchOrderIds.size) { i ->
      val batchOrder = batchOrders[i]
      val (customerName, totalAmount) = batchOrder.customerNameTotalAmountPair

      batchOrderFromComponents(
        batchOrderId = batchOrderIds[i],
        customerName = customerName,
        totalAmount = totalAmount,
        itemIds = itemDataIdLists[i],
        items = batchOrder.items,
      )
    }

  orders shouldContainExactly expectedOrders
}
