/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.dataconnect.gradle.plugin

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

abstract class DataConnectEmulatorTask : DefaultTask() {

  @TaskAction
  fun run() = runBlocking {
    logger.lifecycle("zzyzx got here A")
    val client =
      HttpClient(CIO) {
        expectSuccess = true
        engine {
          endpoint.connectAttempts = 10
          endpoint.connectTimeout = 5.seconds.inWholeMilliseconds
        }
        install(ContentNegotiation) { json(Json { prettyPrint = true }) }
      }
    val response = client.get("https://ktor.io/")
    logger.lifecycle("response.status=${response.status}")
    logger.lifecycle("zzyzx got here B")
  }
}
