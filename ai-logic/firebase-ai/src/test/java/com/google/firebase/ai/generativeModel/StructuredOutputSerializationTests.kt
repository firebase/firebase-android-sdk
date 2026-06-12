package com.google.firebase.ai.generativemodel

import com.google.firebase.ai.InferenceMode
import com.google.firebase.ai.OnDeviceConfig
import com.google.firebase.ai.common.APIController
import com.google.firebase.ai.ondevice.interop.GenerativeModel as OnDeviceGenerativeModel
import com.google.firebase.ai.ondevice.interop.Candidate as OnDeviceCandidate
import com.google.firebase.ai.ondevice.interop.FinishReason as OnDeviceFinishReason
import com.google.firebase.ai.ondevice.interop.GenerateContentResponse as OnDeviceGenerateContentResponse
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.JsonSchema
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.TextPart
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Test
import com.google.firebase.ai.common.GenerateContentRequest
import com.google.firebase.ai.ondevice.interop.GenerateContentRequest as OnDeviceGenerateContentRequest

@OptIn(PublicPreviewAPI::class)
class StructuredOutputSerializationTests {

  @Test
  fun `serialization works the same for cloud and on-device`(): Unit = runBlocking {
    val schema = JsonSchema.obj(mapOf("test" to JsonSchema.string()))
    val prompt = listOf(Content(parts = listOf(TextPart("hello"))))

    // 1. Capture Cloud Request
    val mockController = mockk<APIController>()
    val cloudRequestSlot = slot<GenerateContentRequest>()
    val mockCloudResponse = mockk<com.google.firebase.ai.type.GenerateContentResponse.Internal>(relaxed = true)
    
    coEvery { mockController.generateContent(capture(cloudRequestSlot)) } returns mockCloudResponse

    val cloudProvider = CloudGenerativeModelProvider(
      modelName = "gemini-2.5-flash",
      generativeBackend = GenerativeBackend.googleAI(),
      controller = mockController
    )
    
    try {
      cloudProvider.generateObject(schema, prompt)
    } catch(e: Exception) {
      // Ignored if validation fails since we just want to capture the request
    }

    val cloudSchemaJsonString = com.google.firebase.ai.common.JSON.encodeToString(
      com.google.firebase.ai.type.Schema.InternalJson.serializer(),
      cloudRequestSlot.captured.generationConfig!!.responseJsonSchema!!
    )

    // 2. Capture OnDevice Request
    val mockOnDeviceModel = mockk<OnDeviceGenerativeModel>()
    val onDeviceRequestSlot = slot<OnDeviceGenerateContentRequest>()
    val mockOnDeviceResponse = OnDeviceGenerateContentResponse(
        listOf(OnDeviceCandidate("{\"test\":\"value\"}", OnDeviceFinishReason.STOP))
      )

    coEvery { mockOnDeviceModel.isAvailable() } returns true
    coEvery { mockOnDeviceModel.generateContent(capture(onDeviceRequestSlot)) } returns mockOnDeviceResponse

    val onDeviceProvider = OnDeviceGenerativeModelProvider(
      mockOnDeviceModel,
      OnDeviceConfig(mode = InferenceMode.ONLY_ON_DEVICE)
    )

    onDeviceProvider.generateObject(schema, prompt)

    val onDevicePromptText = onDeviceRequestSlot.captured.text?.text ?: ""
    
    // Verify that the on-device prompt explicitly contains the EXACT serialized schema from Cloud
    val expectedPrefix = "Output a JSON object that conforms to the following schema:\n$cloudSchemaJsonString"
    assert(onDevicePromptText.startsWith(expectedPrefix)) {
        "On-device schema serialization did not match cloud schema serialization!\nExpected prefix: $expectedPrefix\nActual: $onDevicePromptText"
    }
  }
}
