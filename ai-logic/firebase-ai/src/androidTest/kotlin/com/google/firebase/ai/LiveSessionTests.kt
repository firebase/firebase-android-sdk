/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.ai

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.ai.type.AudioTranscriptionConfig
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.InlineData
import com.google.firebase.ai.type.LiveGenerationConfig
import com.google.firebase.ai.type.LiveServerContent
import com.google.firebase.ai.type.LiveServerToolCall
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.LiveSessionResumptionUpdate
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.SessionResumptionConfig
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.liveGenerationConfig
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.ktor.util.toLowerCasePreservingASCIIRules
import java.io.ByteArrayOutputStream
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Ignore
import org.junit.Test

@OptIn(PublicPreviewAPI::class)
class LiveSessionTests {
  private val modelName = "gemini-2.5-flash-native-audio-preview-12-2025"

  private val tools =
    listOf(
      Tool.functionDeclarations(
        listOf(
          com.google.firebase.ai.type.FunctionDeclaration(
            name = "getLastName",
            description = "Gets the last name of a person.",
            parameters =
              mapOf(
                "firstName" to
                  Schema.string(description = "The first name of the person to lookup.")
              )
          )
        )
      )
    )

  private val generationConfig = liveGenerationConfig {
    responseModality = ResponseModality.AUDIO
    outputAudioTranscription = AudioTranscriptionConfig()
  }

  object SystemInstructions {
    val yesOrNo = content(role = "system") { text("You can only respond with \"yes\" or \"no\".") }

    val helloGoodbye =
      content(role = "system") {
        text(
          "When you hear \"Hello\" say \"Goodbye\". If you hear anything else, say \"The audio file is broken\"."
        )
      }

    val lastNames =
      content(role = "system") {
        text(
          "When you receive a message, if the message is a single word, assume it's the first name of a person, and call the getLastName tool to get the last name of said person. Once you get the response, say the response."
        )
      }

    val animalInVideo =
      content(role = "system") {
        text(
          "Send a one word response of what ANIMAL is in the video. If you don't receive a video, send \"Test is broken, I didn't receive a video.\"."
        )
      }
  }

  private fun getLiveModel(
    modelName: String,
    config: LiveGenerationConfig? = null,
    systemInstruction: Content? = null,
    tools: List<Tool>? = null
  ): LiveGenerativeModel {
    val firebaseAI = FirebaseAI.getInstance(AIModels.app(), GenerativeBackend.googleAI())
    return firebaseAI.liveModel(
      modelName = modelName,
      generationConfig = config,
      systemInstruction = systemInstruction,
      tools = tools
    )
  }

  fun resourceAsBytes(resource: Int): ByteArray {
    val context = ApplicationProvider.getApplicationContext<Context>()
    return context.resources.openRawResource(resource).use { it.readBytes() }
  }

  @Test
  fun testSendAudioRealtime_receiveAudioOutputTranscripts(): Unit = runBlocking {
    val liveModel =
      getLiveModel(
        modelName = modelName,
        config = generationConfig,
        systemInstruction = SystemInstructions.helloGoodbye
      )

    val session = liveModel.connect()
    try {
      val audioBytes = resourceAsBytes(R.raw.hello)
      session.sendAudioRealtime(InlineData(audioBytes, "audio/pcm"))
      session.sendAudioRealtime(InlineData(ByteArray(audioBytes.size) { 0 }, "audio/pcm"))

      val text = withTimeoutOrNull(30.seconds) { session.collectNextAudioOutputTranscript() } ?: ""
      text.toLowerCasePreservingASCIIRules() shouldContain "goodbye"
    } finally {
      session.close()
    }
  }

  @Test
  fun testSendVideoRealtime_receiveAudioOutputTranscripts(): Unit = runBlocking {
    val liveModel =
      getLiveModel(
        modelName = modelName,
        config = generationConfig,
        systemInstruction = SystemInstructions.animalInVideo
      )

    val session = liveModel.connect()
    try {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val retriever = MediaMetadataRetriever()
      try {
        val fd = context.resources.openRawResourceFd(R.raw.videoplayback)
        retriever.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
        fd.close()

        val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        val durationMs = durationStr?.toLong() ?: 0L

        durationMs shouldBeGreaterThan 100

        // Extract frames every 1 second
        for (timeMs in 0 until durationMs step 1000) {
          val bitmap =
            retriever.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

          bitmap shouldNotBe null

          if (bitmap != null) {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            session.sendVideoRealtime(InlineData(stream.toByteArray(), "image/png"))
          }
        }
      } finally {
        retriever.release()
      }

      // The model doesn't respond unless we send some audio too (according to iOS test)
      val audioBytes = resourceAsBytes(R.raw.hello)
      session.sendAudioRealtime(InlineData(audioBytes, "audio/pcm"))
      session.sendAudioRealtime(InlineData(ByteArray(audioBytes.size) { 0 }, "audio/pcm"))

      val text = withTimeoutOrNull(30.seconds) { session.collectNextAudioOutputTranscript() } ?: ""
      val response = text.toLowerCasePreservingASCIIRules()
      // Expected responses for the video could be "cat", "kitten", "kitty"
      // Based on iOS: #expect(["kitten", "cat", "kitty"].contains(modelResponse))
      val matches = listOf("cat", "kitten", "kitty").any { response.contains(it) }
      matches shouldBe true // Real model calls might be flakey
    } finally {
      session.close()
    }
  }

  @Test
  fun testRealtime_functionCalling(): Unit = runBlocking {
    val liveModel =
      getLiveModel(
        modelName = modelName,
        config = generationConfig,
        tools = tools,
        systemInstruction = SystemInstructions.lastNames
      )

    val session = liveModel.connect()
    try {
      session.sendTextRealtime("Alex")

      val toolCall =
        withTimeoutOrNull(30.seconds) {
          session.receive().filterIsInstance<LiveServerToolCall>().first()
        }

      toolCall.shouldNotBeNull()
      toolCall.functionCalls.size shouldBe 1
      val functionCall = toolCall.functionCalls.first()
      functionCall.name shouldBe "getLastName"

      val firstName = (functionCall.args["firstName"] as? JsonPrimitive)?.content
      firstName shouldBe "Alex"

      val response = "Smith"
      session.sendFunctionResponse(
        listOf(
          FunctionResponsePart(
            name = functionCall.name,
            response = JsonObject(mapOf("lastName" to JsonPrimitive(response))),
            id = functionCall.id
          )
        )
      )

      val text = withTimeoutOrNull(30.seconds) { session.collectNextAudioOutputTranscript() } ?: ""
      text.toLowerCasePreservingASCIIRules() shouldContain "smith"
    } finally {
      session.close()
    }
  }

  @Test
  @Ignore("This test fails because we do not implement setting turnComplete at all")
  fun testIncremental_works(): Unit = runBlocking {
    val liveModel =
      getLiveModel(
        modelName = modelName,
        config = generationConfig,
        systemInstruction = SystemInstructions.yesOrNo
      )

    val session = liveModel.connect()
    try {
      session.send("Does five plus")
      session.send(" five equal ten?")

      val text = withTimeoutOrNull(30.seconds) { session.collectNextAudioOutputTranscript() } ?: ""
      text.toLowerCasePreservingASCIIRules() shouldContain "yes"
    } finally {
      session.close()
    }
  }

  @Test
  fun testResumption(): Unit = runBlocking {
    val liveModel =
      getLiveModel(
        modelName = modelName,
        config = generationConfig
      )
    val session = liveModel.connect(SessionResumptionConfig())
    session.send("My favorite color is blue. Remember that.", true)
    var lastResumptionUpdate: LiveSessionResumptionUpdate? = null
    withTimeout(30_000) {
      session
        .receive()
        .takeWhile {
          if (it is LiveSessionResumptionUpdate) {
            lastResumptionUpdate = it
          } else if (it is LiveServerContent) {
            !it.turnComplete
          }
          true
        }
        .collect {}
    }
    lastResumptionUpdate shouldNotBe null
    val handle = lastResumptionUpdate?.newHandle
    handle.shouldNotBeNull()
    session.resumeSession(SessionResumptionConfig(handle))
    session.send("What is my favorite color?")
    val text = withTimeoutOrNull(30.seconds) { session.collectNextAudioOutputTranscript() } ?: ""
    text.toLowerCasePreservingASCIIRules() shouldContain "blue"
  }

  private suspend fun LiveSession.collectNextAudioOutputTranscript(): String {
    val transcriptBuilder = StringBuilder()
    this.receive()
      .takeWhile {
        if (it is LiveServerContent) {
          transcriptBuilder.append(it.outputTranscription?.text ?: "")
          !it.turnComplete
        } else {
          true
        }
      }
      .collect {}
    return transcriptBuilder.toString()
  }
}
