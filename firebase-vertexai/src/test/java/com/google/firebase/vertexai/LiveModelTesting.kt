package com.google.firebase.vertexai

import com.google.firebase.vertexai.type.Content
import com.google.firebase.vertexai.type.ContentModality
import com.google.firebase.vertexai.type.FunctionDeclaration
import com.google.firebase.vertexai.type.FunctionResponsePart
import com.google.firebase.vertexai.type.Schema
import com.google.firebase.vertexai.type.SpeechConfig
import com.google.firebase.vertexai.type.Tool
import com.google.firebase.vertexai.type.Voices
import com.google.firebase.vertexai.type.liveGenerationConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
class LiveModelTesting {

    data class Location(val city: String, val state: String)


    suspend fun fetchWeather(type:String): JsonObject {

        // TODO(developer): Write a standard function that would call to an external weather API.

        // For demo purposes, this hypothetical response is hardcoded here in the expected format.
        return JsonObject(mapOf(
            "status" to JsonPrimitive("partlyCloudy")
        ))
    }
    @Test
    fun `function calling test`() {
        val API_KEY = "AIzaSyBBuSQWjk5VJJnHwOfJxw6M7XXjpnW_j-s"
        val liveGenerationConfig = liveGenerationConfig {
            responseModalities = listOf( ContentModality.TEXT)
        }
        val fetchLightStatus = FunctionDeclaration(
            "fetchLight",
            "Get the light status of different lights.",
            mapOf("type" to Schema.string("The type of the light to get status of?"))
        )


        val generativeModel =
            LiveGenerativeModel(
                modelName =
                "projects/vertexaiinfirebase-test/locations/us-central1/publishers/google/models/gemini-2.0-flash-exp",
                apiKey = API_KEY,
                config = liveGenerationConfig,
                tools = listOf(Tool.functionDeclarations(listOf(fetchLightStatus)))
            )
        runBlocking{
            val session =  generativeModel.connect()
            session!!.send("\"Tell me the status of the light tubelight")

            session!!.receive(listOf(ContentModality.TEXT)).collect {
                if(!it.functionCalls.isNullOrEmpty()) {
                    val fetchWeatherCall = it.functionCalls!!.find { it.name == "fetchLight" }

                    val functionResponse = fetchWeatherCall?.let {
                        // Alternatively, if your `Location` class is marked as @Serializable, you can use

                        val type = it.args["type"]!!.jsonPrimitive.content
                        fetchWeather(type)
                    }
                    session!!.sendFunctionResponse(listOf(FunctionResponsePart("fetchLight",  functionResponse!!)))
                } else {
                    println(it.text)
                }

            }

        }

    }
}