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

import java.io.InputStream
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

sealed interface DataConnectExecutable {

  data class File(val file: java.io.File) : DataConnectExecutable

  data class RegularFile(val file: org.gradle.api.file.RegularFile) : DataConnectExecutable

  data class Version(val version: String) : DataConnectExecutable {
    companion object {
      val default: Version
        get() = Version(VersionsJson.load().default)
    }
  }

  @OptIn(ExperimentalSerializationApi::class)
  object VersionsJson {

    const val RESOURCE_PATH =
      "com/google/firebase/dataconnect/gradle/plugin/DataConnectExecutableVersions.json"

    fun load(): Root = openFile().use { Json.decodeFromStream<Root>(it) }

    private fun openFile(): InputStream =
      this::class.java.classLoader.getResourceAsStream(RESOURCE_PATH)
        ?: throw DataConnectGradleException("antkaw2gjp", "resource not found: $RESOURCE_PATH")

    @kotlinx.serialization.Serializable
    data class Root(
      val default: String,
      val versions: Map<String, VerificationInfo>,
    )

    @kotlinx.serialization.Serializable
    data class VerificationInfo(val size: Long, val sha512DigestHex: String)
  }
}
