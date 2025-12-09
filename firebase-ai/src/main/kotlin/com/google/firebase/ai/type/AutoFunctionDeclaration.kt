package com.google.firebase.ai.type

import kotlinx.serialization.json.JsonObject

public class AutoFunctionDeclaration<I : Any, O : Any>
internal constructor(
  public val name: String,
  public val description: String,
  public val inputSchema: JsonSchema<I>,
  public val outputSchema: JsonSchema<O>? = null,
  public val functionReference: (suspend (I) -> O)? = null
) {
  public companion object {
    public fun <I : Any, O : Any> create(
      functionName: String,
      description: String,
      inputSchema: JsonSchema<I>,
      outputSchema: JsonSchema<O>,
      functionReference: ((I) -> O)? = null
    ): AutoFunctionDeclaration<I, O> {
      return AutoFunctionDeclaration<I, O>(
        functionName,
        description,
        inputSchema,
        outputSchema,
        functionReference
      )
    }

    public fun <I : Any> create(
      functionName: String,
      description: String,
      inputSchema: JsonSchema<I>,
      functionReference: ((I) -> JsonObject)? = null
    ): AutoFunctionDeclaration<I, JsonObject> {
      return AutoFunctionDeclaration<I, JsonObject>(
        functionName,
        description,
        inputSchema,
        null,
        functionReference
      )
    }
  }

  internal fun toInternal(): FunctionDeclaration.Internal {
    return FunctionDeclaration.Internal(
      name,
      description,
      null,
      JsonSchema.obj(mapOf("param" to inputSchema)).toInternalJson(),
      outputSchema?.toInternalJson()
    )
  }
}
