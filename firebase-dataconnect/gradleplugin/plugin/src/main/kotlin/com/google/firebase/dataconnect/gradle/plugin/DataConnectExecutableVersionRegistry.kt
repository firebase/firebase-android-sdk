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
import java.io.OutputStream
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream

@OptIn(ExperimentalSerializationApi::class)
object DataConnectExecutableVersionsRegistry {

  const val PATH =
    "com/google/firebase/dataconnect/gradle/plugin/DataConnectExecutableVersions.json"

  fun load(): Root = openResourceForReading().use { Json.decodeFromStream<Root>(it) }

  fun save(root: Root, dest: OutputStream) {
    Json.encodeToStream(root, dest)
  }

  private fun openResourceForReading(): InputStream =
    this::class.java.classLoader.getResourceAsStream(PATH)
      ?: throw DataConnectGradleException("antkaw2gjp", "resource not found: $PATH")

  @kotlinx.serialization.Serializable
  data class Root(
    val defaultVersion: String,
    val versions: List<VersionInfo>,
  )

  @kotlinx.serialization.Serializable
  data class VersionInfo(
    val version: String,
    val os: OperatingSystem,
    val size: Long,
    val sha512DigestHex: String,
  )
}
