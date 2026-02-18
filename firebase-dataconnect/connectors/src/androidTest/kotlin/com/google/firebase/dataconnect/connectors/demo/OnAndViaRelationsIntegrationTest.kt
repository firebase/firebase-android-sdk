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

package com.google.firebase.dataconnect.connectors.demo

import com.google.firebase.dataconnect.connectors.demo.testutil.DemoConnectorIntegrationTestBase
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Ignore
import org.junit.Test

/** See go/firemat:api:relations */
class OnAndViaRelationsIntegrationTest : DemoConnectorIntegrationTestBase() {

  @Test
  fun manyToOne() = runTest {
    val children = List(2) { connector.insertManyToOneChild.execute().data.key }
    val parents =
      List(6) {
        val childKey = children[it % children.size]
        connector.insertManyToOneParent.execute { child = childKey }.data.key
      }

    run {
      val queryResult = connector.getManyToOneChildByKey.execute(children[0])
      val manyToOneChild =
        withClue("manyToOneChild1") { queryResult.data.manyToOneChild.shouldNotBeNull() }
      withClue("parents1") {
        manyToOneChild.parents.shouldContainExactlyInAnyOrder(
          GetManyToOneChildByKeyQuery.Data.ManyToOneChild.ParentsItem(parents[0].id),
          GetManyToOneChildByKeyQuery.Data.ManyToOneChild.ParentsItem(parents[2].id),
          GetManyToOneChildByKeyQuery.Data.ManyToOneChild.ParentsItem(parents[4].id),
        )
      }
    }

    run {
      val queryResult = connector.getManyToOneChildByKey.execute(children[1])
      val manyToOneChild =
        withClue("manyToOneChild2") { queryResult.data.manyToOneChild.shouldNotBeNull() }
      withClue("parents2") {
        manyToOneChild.parents.shouldContainExactlyInAnyOrder(
          GetManyToOneChildByKeyQuery.Data.ManyToOneChild.ParentsItem(parents[1].id),
          GetManyToOneChildByKeyQuery.Data.ManyToOneChild.ParentsItem(parents[3].id),
          GetManyToOneChildByKeyQuery.Data.ManyToOneChild.ParentsItem(parents[5].id),
        )
      }
    }
  }

  @Test
  @Ignore("Write this test once I figure out why the @unique directive fails to compile")
  fun oneToOne() {
    // This test is just here as a placeholder, to be written later.
  }

  @Test
  fun manyToMany() = runTest {
    val childAKey = connector.insertManyToManyChildA.execute().data.key
    val childBKeys = List(3) { connector.insertManyToManyChildB.execute().data.key }
    repeat(3) { connector.insertManyToManyParent.execute(childAKey, childBKeys[it]).data.key }

    val queryResult = connector.getManyToManyChildAByKey.execute(childAKey)

    val manyToManyChildA =
      withClue("manyToManyChildA") { queryResult.data.manyToManyChildA.shouldNotBeNull() }
    withClue("manyToManyChildBS_via_ManyToManyParent") {
      manyToManyChildA.manyToManyChildBS_via_ManyToManyParent shouldContainExactlyInAnyOrder
        childBKeys.map {
          GetManyToManyChildAByKeyQuery.Data.ManyToManyChildA
            .ManyToManyChildBsViaManyToManyParentItem(it.id)
        }
    }
  }

  @Test
  fun manyToOneSelfCustomName() = runTest {
    val key1 = connector.insertManyToOneSelfCustomName.execute { ref = null }.data.key
    val key2 = connector.insertManyToOneSelfCustomName.execute { ref = key1 }.data.key
    val key3 = connector.insertManyToOneSelfCustomName.execute { ref = key2 }.data.key

    val queryResult = connector.getManyToOneSelfCustomNameByKey.execute(key3)

    queryResult.data shouldBe
      GetManyToOneSelfCustomNameByKeyQuery.Data(
        GetManyToOneSelfCustomNameByKeyQuery.Data.ManyToOneSelfCustomName(
          key3.id,
          GetManyToOneSelfCustomNameByKeyQuery.Data.ManyToOneSelfCustomName.Ref(key2.id, key1.id)
        )
      )
  }

  @Test
  fun manyToOneSelfMatchingName() = runTest {
    val key1 = connector.insertManyToOneSelfMatchingName.execute { ref = null }.data.key
    val key2 = connector.insertManyToOneSelfMatchingName.execute { ref = key1 }.data.key
    val key3 = connector.insertManyToOneSelfMatchingName.execute { ref = key2 }.data.key

    val queryResult = connector.getManyToOneSelfMatchingNameByKey.execute(key3)

    queryResult.data shouldBe
      GetManyToOneSelfMatchingNameByKeyQuery.Data(
        GetManyToOneSelfMatchingNameByKeyQuery.Data.ManyToOneSelfMatchingName(
          key3.id,
          GetManyToOneSelfMatchingNameByKeyQuery.Data.ManyToOneSelfMatchingName
            .ManyToOneSelfMatchingName(key2.id, key1.id)
        )
      )
  }

  @Test
  fun manyToManySelf() = runTest {
    val childKeys = List(6) { connector.insertManyToManySelfChild.execute().data.key }
    connector.insertManyToManySelfParent.execute(childKeys[0], childKeys[0]).data.key
    connector.insertManyToManySelfParent.execute(childKeys[0], childKeys[1]).data.key
    connector.insertManyToManySelfParent.execute(childKeys[0], childKeys[2]).data.key
    connector.insertManyToManySelfParent.execute(childKeys[1], childKeys[0]).data.key
    connector.insertManyToManySelfParent.execute(childKeys[5], childKeys[0]).data.key
    connector.insertManyToManySelfParent.execute(childKeys[3], childKeys[4]).data.key
    connector.insertManyToManySelfParent.execute(childKeys[5], childKeys[4]).data.key

    val queryResults = childKeys.map { connector.getManyToManySelfChildByKey.execute(it) }

    fun GetManyToManySelfChildByKeyQuery.Data.assertEquals(
      keys1: List<ManyToManySelfChildKey>,
      keys2: List<ManyToManySelfChildKey>
    ) {
      val child = withClue("child") { manyToManySelfChild.shouldNotBeNull() }
      withClue("child1") {
        val children =
          child.manyToManySelfChildren_via_ManyToManySelfParent_on_child1.shouldNotBeNull()
        children.map { it.id } shouldContainExactlyInAnyOrder keys1.map { it.id }
      }
      withClue("child2") {
        val children =
          child.manyToManySelfChildren_via_ManyToManySelfParent_on_child2.shouldNotBeNull()
        children.map { it.id } shouldContainExactlyInAnyOrder keys2.map { it.id }
      }
    }

    queryResults[0]
      .data
      .assertEquals(
        listOf(childKeys[0], childKeys[1], childKeys[5]),
        listOf(childKeys[0], childKeys[1], childKeys[2])
      )
    queryResults[1].data.assertEquals(listOf(childKeys[0]), listOf(childKeys[0]))
    queryResults[2].data.assertEquals(listOf(childKeys[0]), emptyList())
    queryResults[3].data.assertEquals(emptyList(), listOf(childKeys[4]))
    queryResults[4].data.assertEquals(listOf(childKeys[3], childKeys[5]), emptyList())
    queryResults[5].data.assertEquals(emptyList(), listOf(childKeys[0], childKeys[4]))
  }
}
