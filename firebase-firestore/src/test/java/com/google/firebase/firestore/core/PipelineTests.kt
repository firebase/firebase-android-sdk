package com.google.firebase.firestore.core

import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.RealtimePipelineSource
import com.google.firebase.firestore.TestUtil
import com.google.firebase.firestore.model.MutableDocument
import com.google.firebase.firestore.model.Values.timestamp
import com.google.firebase.firestore.pipeline.Expr.Companion.field
import com.google.firebase.firestore.pipeline.minus
import com.google.firebase.firestore.pipeline.plus
import com.google.firebase.firestore.testutil.TestUtilKtx.doc
import com.google.protobuf.Timestamp
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Test

internal class PipelineTests {

  @Test
  fun `runPipeline executes without error`(): Unit = runBlocking {
    val firestore = TestUtil.firestore()
    val pipeline = RealtimePipelineSource(firestore).collection("foo").where(field("bar").eq(42))

    val doc1: MutableDocument = doc("foo/1", 0, mapOf("bar" to 42))
    val doc2: MutableDocument = doc("foo/2", 0, mapOf("bar" to "43"))
    val doc3: MutableDocument = doc("xxx/1", 0, mapOf("bar" to 42))

    val list = runPipeline(pipeline, flowOf(doc1, doc2, doc3)).toList()

    assertThat(list).hasSize(1)
  }

  @Test
  fun xxx(): Unit = runBlocking {
    val zero: Timestamp = timestamp(0, 0)

    assertThat(plus(zero, 0, 0))
      .isEqualTo(zero)

    assertThat(plus(timestamp(1, 1), 1, 1))
      .isEqualTo(timestamp(2, 2))

    assertThat(plus(timestamp(1, 1), 0, 1))
      .isEqualTo(timestamp(1, 2))

    assertThat(plus(timestamp(1, 1), 1, 0))
      .isEqualTo(timestamp(2, 1))

    assertThat(minus(zero, 0, 0))
      .isEqualTo(zero)

    assertThat(minus(timestamp(1, 1), 1, 1))
      .isEqualTo(zero)

    assertThat(minus(timestamp(1, 1), 0, 1))
      .isEqualTo(timestamp(1, 0))

    assertThat(minus(timestamp(1, 1), 1, 0))
      .isEqualTo(timestamp(0, 1))
  }
}
