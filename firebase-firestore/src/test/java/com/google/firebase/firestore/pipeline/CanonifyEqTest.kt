// Copyright 2025 Google LLC
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

package com.google.firebase.firestore.pipeline

import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.RealtimePipelineSource
import com.google.firebase.firestore.TestUtil
import com.google.firebase.firestore.pipeline.Expression.Companion.field
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class CanonifyEqTest {

  private val db = TestUtil.firestore()

  @Test
  fun `canonify simple where`() {
    val pipeline = RealtimePipelineSource(db).collection("test").where(field("foo").equal(42L))
    assertThat(pipeline.canonicalId())
      .isEqualTo("collection(test)|where(fn(equal[fld(foo),cst(42)]))|sort(fld(__name__)asc)")
  }

  @Test
  fun `canonify multiple stages`() {
    val pipeline =
      RealtimePipelineSource(db)
        .collection("test")
        .where(field("foo").equal("42L"))
        .limit(10)
        .sort(field("bar").descending())
    assertThat(pipeline.canonicalId())
      .isEqualTo(
        "collection(test)|where(fn(equal[fld(foo),cst(42L)]))|sort(fld(__name__)asc)|limit(10)|sort(fld(bar)desc,fld(__name__)asc)"
      )
  }

  @Test
  fun `canonify collection group source`() {
    val pipeline = RealtimePipelineSource(db).collectionGroup("cities")
    assertThat(pipeline.canonicalId()).isEqualTo("collection_group(cities)|sort(fld(__name__)asc)")
  }

  @Test
  fun `eq returns true for identical pipelines`() {
    val p1 = RealtimePipelineSource(db).collection("test").where(field("foo").equal(42L))
    val p2 = RealtimePipelineSource(db).collection("test").where(field("foo").equal(42L))
    assertThat(p1.equals(p2)).isTrue()
  }

  @Test
  fun `eq returns false for different stages`() {
    val p1 = RealtimePipelineSource(db).collection("test").where(field("foo").equal(42L))
    val p2 = RealtimePipelineSource(db).collection("test").limit(10)
    assertThat(p1.equals(p2)).isFalse()
  }

  @Test
  fun `eq returns false for different params in stage`() {
    val p1 = RealtimePipelineSource(db).collection("test").where(field("foo").equal(42L))
    val p2 = RealtimePipelineSource(db).collection("test").where(field("bar").equal(42L))
    assertThat(p1.equals(p2)).isFalse()
  }

  @Test
  fun `eq returns false for different stage order`() {
    val p1 = RealtimePipelineSource(db).collection("test").where(field("foo").equal(42L)).limit(10)
    val p2 = RealtimePipelineSource(db).collection("test").limit(10).where(field("foo").equal(42L))
    assertThat(p1.equals(p2)).isFalse()
  }

  @Test
  fun `eq returns false for same canonicalId but different types`() {
    val p1 = RealtimePipelineSource(db).collection("test").where(field("foo").equal("42"))
    val p2 = RealtimePipelineSource(db).collection("test").where(field("foo").equal(42))
    assertThat(p1.canonicalId()).isEqualTo(p2.canonicalId())
    assertThat(p1.equals(p2)).isFalse()
  }
}
