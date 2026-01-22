import com.google.firebase.ai.ksp.FirebaseSymbolProcessorProvider
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.sourcesGeneratedBySymbolProcessor
import com.tschuchort.compiletesting.symbolProcessorProviders
import com.tschuchort.compiletesting.useKsp2
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Test

class KSPCompilationTest {

  val annotationSource =
    SourceFile.kotlin(
      "Annotations.kt",
      """
        package com.google.firebase.ai.annotations

        @Target(AnnotationTarget.CLASS)
        @Retention(AnnotationRetention.SOURCE)
        public annotation class Generable(public val description: String = "")

        @Target(AnnotationTarget.PROPERTY)
        @Retention(AnnotationRetention.SOURCE)
        public annotation class Guide(
          public val description: String = "",
          public val minimum: Double = -1.0,
          public val maximum: Double = -1.0,
          public val minItems: Int = -1,
          public val maxItems: Int = -1,
          public val format: String = "",
          public val pattern: String = "",
        )

        @Target(AnnotationTarget.FUNCTION)
        @Retention(AnnotationRetention.SOURCE)
        public annotation class Tool(public val description: String = "")

    """,
      true
    )

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun generateComplexType() {
    val typeSource =
      SourceFile.kotlin(
        "TestClassFile.kt",
        """
            import com.google.firebase.ai.annotations.Generable
            import com.google.firebase.ai.annotations.Guide
            import kotlinx.serialization.Serializable

            
            /**
             * A test kdoc
             * @property longTest a test long
             * that takes up multiple lines
             */
            @Generable
            @Serializable
            data class RootSchemaTestClass(
                val integerTest: Int?,
                val longTest: Long,
                val floatTest: Float,
                @Guide(minimum = 5.0)
                val doubleTest: Double?,
                val listTest: List<Int>,
                @Guide(description = "most likely true, very rarely false")
                val booleanTest: Boolean,
                val stringTest: String,
                val objTest: SecondarySchemaTestClass,
                val enumTest: EnumTest,
            )
            
            /**
             * class kdoc should be used if property kdocs aren't present
             */
            data class SecondarySchemaTestClass(val testInt: Int)
            
            enum class EnumTest{
                A,B,C
            }""",
        true
      )
    val compilation =
      KotlinCompilation().apply {
        useKsp2()
        sources = listOf(typeSource, annotationSource)
        symbolProcessorProviders = mutableListOf(FirebaseSymbolProcessorProvider())
        inheritClassPath = true
      }
    val result = compilation.compile()
    val generatedSchema = result.sourcesGeneratedBySymbolProcessor.first()
    assert(generatedSchema.name.equals("RootSchemaTestClassGeneratedSchema.kt"))
    val fileContents = generatedSchema.readLines().foldRight("") { a, b -> a + "\n" + b }
    assert(
      fileContents.trim() ==
        """
            import com.google.firebase.ai.type.JsonSchema
            import javax.`annotation`.processing.Generated

            @Generated
            public fun RootSchemaTestClass.Companion.firebaseAISchema(): JsonSchema<RootSchemaTestClass> = JsonSchema.obj(
              clazz = RootSchemaTestClass::class,
              properties = mapOf(
                "integerTest" to 
                  JsonSchema.integer(
                    title = "integerTest",
                    nullable = true)
                , 
                "longTest" to 
                  JsonSchema.long(
                    title = "longTest",
                    description = "a test long that takes up multiple lines",
                    nullable = false)
                , 
                "floatTest" to 
                  JsonSchema.float(
                    title = "floatTest",
                    nullable = false)
                , 
                "doubleTest" to 
                  JsonSchema.double(
                    title = "doubleTest",
                    minimum = 5.0,
                    nullable = true)
                , 
                "listTest" to 
                  JsonSchema.array(
                    items = 
                    JsonSchema.integer(
                      nullable = false)
                    ,
                    title = "listTest",
                    nullable = false)
                , 
                "booleanTest" to 
                  JsonSchema.boolean(
                    title = "booleanTest",
                    description = null,
                    nullable = false)
                , 
                "stringTest" to 
                  JsonSchema.string(
                    title = "stringTest",
                    nullable = false)
                , 
                "objTest" to 
                  JsonSchema.obj(
                    clazz = SecondarySchemaTestClass::class,
                    properties = mapOf(
                      "testInt" to 
                        JsonSchema.integer(
                          title = "testInt",
                          nullable = false)
                      , 
                    ),
                    title = "objTest",
                    description = null,
                    nullable = false)
                , 
                "enumTest" to 
                  JsonSchema.enumeration(
                    clazz = EnumTest::class,
                    values = listOf(
                      "A", "B", "C"
                    ),
                    title = "enumTest",
                    nullable = false)
                , 
              ),
              description = null,
              nullable = false)
        """
          .trimIndent()
          .trim()
    )
  }

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun nonDataClassShouldFail() {
    val typeSource =
      SourceFile.kotlin(
        "TestClassFile.kt",
        """
            import com.google.firebase.ai.annotations.Generable

            @Generable
            class RootSchemaTestClass(
                val integerTest: Int?,
                val longTest: Long
            )""",
        true
      )
    val compilation =
      KotlinCompilation().apply {
        useKsp2()
        sources = listOf(typeSource, annotationSource)
        symbolProcessorProviders = mutableListOf(FirebaseSymbolProcessorProvider())
        inheritClassPath = true
      }
    val result = compilation.compile()
    assert(result.exitCode == ExitCode.COMPILATION_ERROR)
    assert(result.messages.contains("is not a data class"))
  }
}
