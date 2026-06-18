# Implementation Plan - Structured Output API for Firebase AI On-Device

## Goal Description

Design and implement the architectural bridging layer across `firebase-ai-ondevice-interop`, `firebase-ai-ondevice`, and `firebase-ai` to enable On-Device Structured Output generation (`generateObject`). Specifically, resolve the core technical challenge of implementing `transformSchemaToMlKitTypedClass(schema: SchemaObject<T>): Class<*>` to translate Firebase AI's runtime `JsonSchema` definitions into ML Kit's reflection-based `Class<?>` execution model.

---

## User Review Required

> [!IMPORTANT]
> **The Retention Mismatch Dilemma**
> In Firebase AI, the `@Generable` and `@Guide` annotations defined in `com.google.firebase.ai.annotations` have `@Retention(AnnotationRetention.SOURCE)`. They exist strictly during KSP compile-time processing to construct `JsonSchema` objects and **do not exist at runtime**.
> Conversely, ML Kit's Structured Output API (`GenerateTypedContentRequest`) takes an `outputClass: Class<*>` at runtime and inspects it via **Java reflection**, expecting ML Kit's `@com.google.mlkit.genai.structuredoutput.annotations.Generable` and `@Guide` runtime annotations.
> Furthermore, Firebase AI developers can manually construct schemas via `JsonSchema.obj(..., clazz = ComprehensiveRecipe::class)` or even dynamic structures (`clazz = JsonObject::class`).

To bridge Firebase AI's schema representation (`JsonSchema<T>`) to ML Kit's target `Class<?>`, we evaluated three distinct architectural designs:

### Design Option 1 (Recommended): Runtime Synthetic Bytecode Generation via DexMaker

* **Mechanism**: At runtime on the device, `transformSchemaToMlKitTypedClass(schema)` inspects the `JsonSchema` tree. It uses `com.linkedin.dexmaker:dexmaker` (already cataloged in `gradle/libs.versions.toml`) to dynamically emit Dalvik `.dex` bytecode in `context.codeCacheDir`. For the target schema, it generates a synthetic class (e.g., `com.google.firebase.ai.ondevice.synthetic.Schema$ComprehensiveRecipe`) where each schema property becomes a Dalvik field annotated with Dalvik runtime annotation bytecode (`RuntimeVisibleAnnotations`) matching ML Kit's `@Guide(description=..., minimum=..., enumValues=...)` and `@Generable`.
* **Pros**:
  - **Universal Compatibility**: Works seamlessly for all KSP-generated classes, manual data classes, and anonymous `JsonSchema.obj` definitions.
  - **Zero Developer Friction**: No breaking changes, no KSP plugin modifications required.
* **Cons**:
  - Requires adding `dexmaker` (~100KB) as an `implementation` dependency in `firebase-ai-ondevice`.
  - Minimal ~5-15ms first-time dex generation overhead per unique schema (can be cached in memory).

### Design Option 2: Compile-Time Mirror Generation via `firebase-ai-ksp-processor`

* **Mechanism**: Update `firebase-ai-ksp-processor`. When processing a Firebase AI `@Generable` class, KSP simultaneously generates a shadow companion class annotated with ML Kit's `@Generable` and `@Guide` annotations (e.g., `ComprehensiveRecipe$$MlKitStructuredOutput`).
* **Pros**: Zero runtime dex generation, zero external libraries added to runtime APK.
* **Cons**: Fails completely for manual `JsonSchema.obj(...)` schemas created without KSP annotations. Creates tight compile-time coupling between Firebase AI KSP and ML Kit annotations.

### Design Option 3 (Long-Term Ideal): Low-Level JSON Schema Bypass in ML Kit

* **Mechanism**: Under the hood, ML Kit's reflection engine converts `Class<?>` into an AICore `Schema` / JSON Schema string. Collaborate with ML Kit to expose an overload `generateTypedContentRequest(String rawJsonSchema)` or accept a schema proto directly.
* **Pros**: Cleanest architectural design, zero bytecode hacks, zero runtime reflection.
* **Cons**: Requires ML Kit GenAI SDK upstream API changes.

---

## Open Questions

> [!WARNING]
> **Key Decisions Needed Before Coding**

1. **Upstream ML Kit Capabilities**: Does `com.google.mlkit:genai-prompt` (v1.0.0-beta2) currently possess an internal/hidden EAP request builder that accepts raw JSON schemas or `JSONObject`, bypassing `Class<?>` reflection?
2. **Dependency & Size Budget**: If Option 1 is chosen, do we have explicit approval to include `com.linkedin.dexmaker:dexmaker` as an external runtime dependency in `firebase-ai-ondevice`?
3. **Anonymous Schema Scope**: Should on-device structured output support arbitrary dynamic schemas (`JsonSchema.obj(..., clazz = JsonObject::class)`), or should we enforce that `T` must be a developer-defined Kotlin class?

---

## Proposed Changes

Assuming **Design Option 1 (Synthetic Dex Generation)** or a clean schema wrapper interface, the changes across the bridging layer are structured as follows:

### 1. Module: `ai-logic:firebase-ai-ondevice-interop`

#### [NEW] `SchemaObject.kt`

Define the interop representation of a schema (mirroring necessary properties of `JsonSchema`).

```kotlin
package com.google.firebase.ai.ondevice.interop

import kotlin.reflect.KClass

public class SchemaObject<T : Any>(
  public val type: String,
  public val clazz: KClass<T>,
  public val description: String? = null,
  public val properties: Map<String, SchemaObject<*>>? = null,
  public val required: List<String>? = null,
  public val items: SchemaObject<*>? = null,
  public val enumValues: List<String>? = null,
  public val minimum: Double? = null,
  public val maximum: Double? = null,
  public val minItems: Int? = null,
  public val maxItems: Int? = null,
  public val nullable: Boolean? = null,
)
```

#### [NEW] `GenerateObjectResponseInterop.kt`

Define the interop response wrapper.

```kotlin
package com.google.firebase.ai.ondevice.interop

public class GenerateObjectResponseInterop<T : Any>(
  public val instance: T,
  public val rawJson: String
)
```

#### [MODIFY] `GenerativeModel.kt`

Add `generateObject` contract to the interop interface.

```kotlin
public interface GenerativeModel {
  // Existing methods...
  
  public suspend fun <T : Any> generateObject(
    request: GenerateContentRequest,
    schema: SchemaObject<T>
  ): GenerateObjectResponseInterop<T>
}
```

---

### 2. Module: `ai-logic:firebase-ai-ondevice`

#### [MODIFY] `firebase-ai-ondevice.gradle.kts`

Add DexMaker dependency (if Option 1 approved).

```kotlin
dependencies {
  implementation(libs.dexmaker)
  // existing dependencies...
}
```

#### [NEW] `SchemaTransform.kt`

Implement `transformSchemaToMlKitTypedClass(schema: SchemaObject<T>): Class<*>` using synthetic dex emission or reflection caching.

```kotlin
package com.google.firebase.ai.ondevice

import com.google.firebase.ai.ondevice.interop.SchemaObject

internal object SchemaTransform {
  private val classCache = mutableMapOf<SchemaObject<*>, Class<*>>()

  fun <T : Any> transformSchemaToMlKitTypedClass(schema: SchemaObject<T>): Class<*> {
    return classCache.getOrPut(schema) {
      generateSyntheticMlKitClass(schema)
    }
  }

  private fun generateSyntheticMlKitClass(schema: SchemaObject<*>): Class<*> {
    // 1. Check if clazz already has ML Kit @Generable runtime annotations
    // 2. If not, emit Dalvik bytecode (.dex) with DexMaker attaching MLKit annotations:
    //    @com.google.mlkit.genai.structuredoutput.annotations.Generable
    //    @com.google.mlkit.genai.structuredoutput.annotations.Guide(...)
    // 3. Load synthetic class via DexClassLoader
    TODO("Implement synthetic Dalvik class emission")
  }
}
```

#### [MODIFY] `GenerativeModelImpl.kt`

Override `generateObject` delegating to ML Kit.

```kotlin
override suspend fun <T : Any> generateObject(
  request: GenerateContentRequest,
  schema: SchemaObject<T>
): GenerateObjectResponseInterop<T> = try {
  val mlKitRequest = generateTypedContentRequest(
    generateContentRequest = request.toMlKit(),
    outputClass = SchemaTransform.transformSchemaToMlKitTypedClass(schema),
    includeSchemaInPrompt = true
  )
  val typedResponse = mlkitModel.generateContent(mlKitRequest)
  typedResponse.toInteropObject(schema)
} catch (e: GenAiException) {
  throw getMappingException(e)
}
```

---

### 3. Module: `ai-logic:firebase-ai`

#### [MODIFY] `OnDeviceGenerativeModelProvider.kt`

Replace the stub in `generateObject` (lines 143-150) to bridge `JsonSchema` to `SchemaObject`.

```kotlin
override suspend fun <T : Any> generateObject(
  jsonSchema: JsonSchema<T>,
  prompt: List<Content>
): GenerateObjectResponse<T> = withFirebaseAIExceptionHandling {
  ensureOnDeviceModelAvailable()
  val request = buildOnDeviceGenerateContentRequest(prompt)
  val interopSchema = jsonSchema.toInteropSchema()
  
  val interopResponse = onDeviceModel.generateObject(request, interopSchema)
  GenerateObjectResponse(interopResponse.instance)
}
```

---

## Verification Plan

### Automated Tests

1. **Unit & Converter Tests**: Run `./gradlew :ai-logic:firebase-ai-ondevice:check` to verify schema mapping logic and synthetic annotation generation.
2. **Robolectric Tests**: Verify `SchemaTransform` synthetic Dalvik class loading under Android runtime simulation.
3. **Provider Delegation Tests**: Run `./gradlew :ai-logic:firebase-ai:check` to ensure `OnDeviceGenerativeModelProvider` correctly delegates `generateObject` calls without throwing `IllegalArgumentException`.

### Manual Verification

1. Create a sample test app with an `@Serializable @Generable data class Recipe(...)`.
2. Instantiate `FirebaseApp` and execute `generativeModel.generateObject(recipeSchema, prompt)`.
3. Verify that the returned object is strongly typed and correctly populated by on-device AICore inference.

