/*
 * Copyright 2026 Google LLC
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

package com.google.firebase.dataconnect.util

import com.google.firebase.dataconnect.testutil.property.arbitrary.distinctPair
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.map
import io.kotest.property.arbs.color
import io.kotest.property.arbs.products.brand
import io.kotest.property.arbs.stockExchanges
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

@Suppress("ReplaceCallWithBinaryOperator")
class TaggedReferenceUnitTest {

  @Test
  fun `tag property returns the same object given to the constructor`() = runTest {
    checkAll(propTestConfig, tagArb(), refArb()) { tag, ref ->
      val taggedRef = TaggedReference(tag, ref)
      taggedRef.tag shouldBeSameInstanceAs tag
    }
  }

  @Test
  fun `ref property returns the same object given to the constructor`() = runTest {
    checkAll(propTestConfig, tagArb(), refArb()) { tag, ref ->
      val taggedRef = TaggedReference(tag, ref)
      taggedRef.ref shouldBeSameInstanceAs ref
    }
  }

  @Test
  fun `equals() on itself returns true`() = runTest {
    checkAll(propTestConfig, tagArb(), refArb()) { tag, ref ->
      val taggedRef = TaggedReference(tag, ref)
      taggedRef.equals(taggedRef) shouldBe true
    }
  }

  @Test
  fun `equals() with null returns false`() = runTest {
    checkAll(propTestConfig, tagArb(), refArb()) { tag, ref ->
      val taggedRef = TaggedReference(tag, ref)
      taggedRef.equals(null) shouldBe false
    }
  }

  @Test
  fun `equals() with distinct type returns false`() = runTest {
    checkAll(propTestConfig, tagArb(), refArb()) { tag, ref ->
      val taggedRef = TaggedReference(tag, ref)
      taggedRef.equals("not a TaggedReference") shouldBe false
    }
  }

  @Test
  fun `equals() on distinct instance with same properties returns true`() = runTest {
    checkAll(propTestConfig, tagArb(), refArb()) { tag, ref ->
      val taggedRef1 = TaggedReference(tag, ref)
      val taggedRef2 = TaggedReference(tag, ref)
      taggedRef1.equals(taggedRef2) shouldBe true
    }
  }

  @Test
  fun `equals() on distinct instance with equal, but distinct, properties returns true`() =
    runTest {
      checkAll(propTestConfig, tagArb(), refArb()) { tag, ref ->
        val taggedRef1 = TaggedReference(tag, ref)
        val taggedRef2 = TaggedReference(tag.copy(), ref.copy())
        taggedRef1.equals(taggedRef2) shouldBe true
      }
    }

  @Test
  fun `equals() when tag is unequal returns false`() = runTest {
    checkAll(propTestConfig, tagArb().distinctPair(), refArb()) { (tag1, tag2), ref ->
      val taggedRef1 = TaggedReference(tag1, ref)
      val taggedRef2 = TaggedReference(tag2, ref)
      taggedRef1.equals(taggedRef2) shouldBe false
    }
  }

  @Test
  fun `equals() when ref is unequal returns false`() = runTest {
    checkAll(propTestConfig, tagArb(), refArb().distinctPair()) { tag, (ref1, ref2) ->
      val taggedRef1 = TaggedReference(tag, ref1)
      val taggedRef2 = TaggedReference(tag, ref2)
      taggedRef1.equals(taggedRef2) shouldBe false
    }
  }

  @Test
  fun `hashCode() on itself always returns same value`() = runTest {
    checkAll(propTestConfig, tagArb(), refArb()) { tag, ref ->
      val taggedRef = TaggedReference(tag, ref)
      taggedRef.hashCode() shouldBe taggedRef.hashCode()
    }
  }

  @Test
  fun `hashCode() on distinct instance with same properties returns same value`() = runTest {
    checkAll(propTestConfig, tagArb(), refArb()) { tag, ref ->
      val taggedRef1 = TaggedReference(tag, ref)
      val taggedRef2 = TaggedReference(tag, ref)
      taggedRef1.hashCode() shouldBe taggedRef2.hashCode()
    }
  }

  @Test
  fun `hashCode() on distinct instance with equal, but distinct, properties returns same value`() =
    runTest {
      checkAll(propTestConfig, tagArb(), refArb()) { tag, ref ->
        val taggedRef1 = TaggedReference(tag, ref)
        val taggedRef2 = TaggedReference(tag.copy(), ref.copy())
        taggedRef1.hashCode() shouldBe taggedRef2.hashCode()
      }
    }

  @Test
  fun `hashCode() when tag is unequal returns a different value`() = runTest {
    checkAll(hashPropTestConfig, tagArb().distinctPair(), refArb()) { (tag1, tag2), ref ->
      val taggedRef1 = TaggedReference(tag1, ref)
      val taggedRef2 = TaggedReference(tag2, ref)
      taggedRef1.hashCode() shouldNotBe taggedRef2.hashCode()
    }
  }

  @Test
  fun `hashCode() when ref is unequal returns a different value`() = runTest {
    checkAll(hashPropTestConfig, tagArb(), refArb().distinctPair()) { tag, (ref1, ref2) ->
      val taggedRef1 = TaggedReference(tag, ref1)
      val taggedRef2 = TaggedReference(tag, ref2)
      taggedRef1.hashCode() shouldNotBe taggedRef2.hashCode()
    }
  }

  @Test
  fun `toString() returns expected format`() = runTest {
    checkAll(propTestConfig, tagArb(), refArb()) { tag, ref ->
      val taggedRef = TaggedReference(tag, ref)
      taggedRef.toString() shouldContainWithNonAbuttingText "TaggedReference"
      taggedRef.toString() shouldContainWithNonAbuttingText "tag=$tag"
      taggedRef.toString() shouldContainWithNonAbuttingText "ref=$ref"
    }
  }

  @Test
  fun `map transforms reference while keeping the same tag`() = runTest {
    checkAll(propTestConfig, tagArb(), refArb(), Arb.brand()) { tag, ref1, ref2 ->
      val taggedRef = TaggedReference(tag, ref1)
      val mappedRef = taggedRef.map { ref2 }
      mappedRef.tag shouldBeSameInstanceAs tag
      mappedRef.ref shouldBeSameInstanceAs ref2
    }
  }
}

@OptIn(ExperimentalKotest::class)
private val propTestConfig =
  PropTestConfig(iterations = 100, edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2))

// Allow a small number of failures to account for the rare, but possible, situation where two
// unequal instances produce the same hash code.
@OptIn(ExperimentalKotest::class)
private val hashPropTestConfig =
  propTestConfig.copy(
    minSuccess = propTestConfig.iterations!! - 2,
    maxFailure = 2,
  )

private data class TestTag(val value: String)

private fun tagArb(): Arb<TestTag> = Arb.stockExchanges().map { TestTag(it.name) }

private data class TestRef(val value: String)

private fun refArb(): Arb<TestRef> = Arb.color().map { TestRef(it.value) }
