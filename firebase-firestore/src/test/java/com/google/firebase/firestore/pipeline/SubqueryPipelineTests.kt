package com.google.firebase.firestore.pipeline

import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.Pipeline
import com.google.firebase.firestore.TestUtil
import com.google.firebase.firestore.pipeline.Expression.Companion.currentDocument
import com.google.firebase.firestore.pipeline.Expression.Companion.field
import com.google.firebase.firestore.pipeline.Expression.Companion.variable
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class SubqueryPipelineTests {

  private val db = TestUtil.firestore()
  private val userDataReader = TestUtil.USER_DATA_READER

  @Test
  fun `define creates DefineStage in proto`() {
    // Manually construct Pipeline or use a helper
    // Since Pipeline constructor is internal, we can access it from this internal class in the same
    // module
    val pipeline = Pipeline(db, userDataReader, emptyList()).define(field("title").alias("t"))

    val proto = pipeline.toPipelineProto(userDataReader)
    assertThat(proto.stagesCount).isEqualTo(1)
    val stage = proto.getStages(0)
    assertThat(stage.name).isEqualTo("let")
    // Verify args or options contains the variable
    // DefineStage puts variables in args as map
    assertThat(stage.argsCount).isEqualTo(1)
    val mapValue = stage.getArgs(0).mapValue
    assertThat(mapValue).isNotNull()
    // Verify variable mapping
  }

  @Test
  fun `subcollection creates pipeline with SubcollectionSource`() {
    val pipeline = Pipeline.subcollection("reviews")
    // We must provide a reader override since "subcollection" uses null internally
    val proto = pipeline.toPipelineProto(userDataReader)

    assertThat(proto.stagesCount).isEqualTo(1)
    val stage = proto.getStages(0)
    assertThat(stage.name).isEqualTo("subcollection")
    val pathArg = stage.getArgs(0)
    // args(0) is path string encoded?
    // SubcollectionSource args: sequenceOf(encodeValue(path))
    assertThat(pathArg.stringValue).isEqualTo("reviews")
  }

  @Test
  fun `toArrayExpression creates FunctionExpression`() {
    val subPipeline = Pipeline.subcollection("sub_items")
    val expr = subPipeline.toArrayExpression()

    val protoValue = expr.toProto(userDataReader)
    assertThat(protoValue.hasFunctionValue()).isTrue()
    assertThat(protoValue.functionValue.name).isEqualTo("array")
    assertThat(protoValue.functionValue.argsCount).isEqualTo(1)
    val pipelineArg = protoValue.functionValue.getArgs(0)
    assertThat(pipelineArg.hasPipelineValue()).isTrue()
  }

  @Test
  fun `toScalarExpression creates FunctionExpression`() {
    val subPipeline = Pipeline.subcollection("sub_items")
    val expr = subPipeline.toScalarExpression()

    val protoValue = expr.toProto(userDataReader)
    assertThat(protoValue.hasFunctionValue()).isTrue()
    assertThat(protoValue.functionValue.name).isEqualTo("scalar")
    assertThat(protoValue.functionValue.argsCount).isEqualTo(1)
    val pipelineArg = protoValue.functionValue.getArgs(0)
    assertThat(pipelineArg.hasPipelineValue()).isTrue()
  }

  @Test
  fun `variable expression proto`() {
    val v = variable("my_var")
    val proto = v.toProto(userDataReader)
    assertThat(proto.variableReferenceValue).isEqualTo("my_var")
  }

  @Test
  fun `currentDocument expression proto`() {
    val cd = currentDocument()
    val proto = cd.toProto(userDataReader)
    assertThat(proto.hasFunctionValue()).isTrue()
    assertThat(proto.functionValue.name).isEqualTo("current_document")
    assertThat(proto.functionValue.argsCount).isEqualTo(0)
  }

  @Test
  fun `Expression getField creates field`() {
    val v = variable("my_var")
    val f2 = Expression.getField(v, "sub")

    val proto2 = f2.toProto(userDataReader)

    assertThat(proto2.hasFunctionValue()).isTrue()
    assertThat(proto2.functionValue.name).isEqualTo("field")
    assertThat(proto2.functionValue.argsCount).isEqualTo(2)
    assertThat(proto2.functionValue.getArgs(0).variableReferenceValue).isEqualTo("my_var")
    assertThat(proto2.functionValue.getArgs(1).stringValue).isEqualTo("sub")
  }
}
