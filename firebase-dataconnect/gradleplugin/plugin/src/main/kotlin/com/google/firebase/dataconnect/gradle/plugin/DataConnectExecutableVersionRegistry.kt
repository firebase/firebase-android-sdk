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
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream

@OptIn(ExperimentalSerializationApi::class)
object DataConnectExecutableVersionsRegistry {

  const val PATH =
    "com/google/firebase/dataconnect/gradle/plugin/DataConnectExecutableVersions.json"

  fun load(): Root = openResourceForReading().use { load(it) }

  fun load(file: java.io.File): Root = file.inputStream().use { load(it) }

  fun load(stream: InputStream): Root = Json.decodeFromStream<Root>(stream)

  fun save(root: Root, dest: java.io.File) = dest.outputStream().use { save(root, it) }

  fun save(root: Root, dest: OutputStream) {
    Json.encodeToStream(root, dest)
  }

  private fun openResourceForReading(): InputStream =
    this::class.java.classLoader.getResourceAsStream(PATH)
      ?: throw DataConnectGradleException("antkaw2gjp", "resource not found: $PATH")

  @Serializable
  data class Root(
    val defaultVersion: String,
    val versions: List<VersionInfo>,
  )

  @Serializable
  data class VersionInfo(
    val version: String,
    @Serializable(with = OperationSystemSerializer::class) val os: OperatingSystem,
    val size: Long,
    val sha512DigestHex: String,
  )

  private object OperationSystemSerializer : KSerializer<OperatingSystem> {
    override val descriptor = PrimitiveSerialDescriptor("os", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): OperatingSystem =
      when (val name = decoder.decodeString()) {
        "windows" -> OperatingSystem.Windows
        "macos" -> OperatingSystem.MacOS
        "linux" -> OperatingSystem.Linux
        else ->
          throw DataConnectGradleException(
            "nd5z2jk4hr",
            "Unknown operating system: $name (must be windows, linux, or macos)"
          )
      }

    override fun serialize(encoder: Encoder, value: OperatingSystem) =
      encoder.encodeString(
        when (value) {
          OperatingSystem.Windows -> "windows"
          OperatingSystem.MacOS -> "macos"
          OperatingSystem.Linux -> "linux"
        }
      )
  }
}
