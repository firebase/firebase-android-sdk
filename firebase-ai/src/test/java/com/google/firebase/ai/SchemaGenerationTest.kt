package com.google.firebase.ai

import com.google.firebase.ai.annotation.ListSchemaDetails
import com.google.firebase.ai.annotation.NumSchemaDetails
import com.google.firebase.ai.annotation.SchemaDetails
import com.google.firebase.ai.annotation.StringSchemaDetails
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.StringFormat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.junit.Test

class SchemaGenerationTest {

  @Test
  fun testSchemaGeneration() {
    val generatedSchema = Schema.fromClass(TestClass1::class)
    val schema =
      Schema.obj(
        description = "A test class (1)",
        title = "TestClass1",
        properties =
          mapOf(
            "val1" to Schema.integer("A test field (1)", false, "var1"),
            "val2" to Schema.long("A test field (2)", false, "var2", 20.0, 30.0),
            "val3" to Schema.boolean("A test field (3)", false, "var3"),
            "val4" to Schema.float("A test field (4)", false, "var4"),
            "val5" to Schema.double("A test field (5)", false, "var5"),
            "val6" to
              Schema.string("A test field (6)", false, StringFormat.Custom("StringFormat"), "var6"),
            "val7" to
              Schema.array(
                Schema.obj(
                  mapOf("val1" to Schema.integer(nullable = true)),
                  emptyList(),
                  "A test class (2)",
                  false,
                  "TestClass2",
                ),
                "A test field (7)",
                false,
                "var7",
                0,
                500,
              ),
            "val8" to
              Schema.obj(
                mapOf("customSerialName" to Schema.array(Schema.string(), minItems = 0, maxItems = 500)),
                emptyList(),
                "A test field (8)",
                false,
                "var8",
              ),
          ),
      )
      assert(schema.toInternal() == generatedSchema.toInternal())
  }

  @Serializable
  @SchemaDetails("A test class (1)", "TestClass1")
  data class TestClass1(
    @SchemaDetails("A test field (1)", "var1")
    val val1: Int,
    @NumSchemaDetails(minimum = 20.0, maximum = 30.0)
    @SchemaDetails("A test field (2)", "var2")
    val val2: Long,
    @SchemaDetails("A test field (3)", "var3")
    val val3: Boolean,
    @SchemaDetails("A test field (4)", "var4")
    val val4: Float,
    @SchemaDetails("A test field (5)", "var5")
    val val5: Double,
    @SchemaDetails("A test field (6)", "var6")
    @StringSchemaDetails("StringFormat")
    val val6: String,
    @SchemaDetails("A test field (7)", "var7")
    @ListSchemaDetails(0, 500, TestClass2::class)
    val val7: List<TestClass2>,
    @SchemaDetails("A test field (8)", "var8")
    val val8: TestClass3,
  )

  @Serializable
  @SchemaDetails("A test class (2)", "TestClass2")
  data class TestClass2(val val1: Int?)

  @Serializable
  @SchemaDetails("A test class (3)", "TestClass3")
  data class TestClass3(
    @ListSchemaDetails(0, 500, String::class)
    @SerialName("customSerialName")
    val val1: List<String>
  )
}
