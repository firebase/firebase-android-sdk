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

import io.github.z4kn4fein.semver.LooseVersionSerializer
import io.github.z4kn4fein.semver.Version
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

  private val json: Json by lazy {
    Json {
      prettyPrint = true
      prettyPrintIndent = "  "
      allowTrailingComma = true
    }
  }

  fun load(): Root = openResourceForReading().use { load(it) }

  fun load(file: java.io.File): Root = file.inputStream().use { load(it) }

  fun load(stream: InputStream): Root = json.decodeFromStream<Root>(stream)

  fun save(root: Root, dest: java.io.File) = dest.outputStream().use { save(root, it) }

  fun save(root: Root, dest: OutputStream) {
    json.encodeToStream(root, dest)
  }

  private fun openResourceForReading(): InputStream =
    this::class.java.classLoader.getResourceAsStream(PATH)
      ?: throw DataConnectGradleException("antkaw2gjp", "resource not found: $PATH")

  @Serializable
  data class Root(
    @Serializable(with = LooseVersionSerializer::class) val defaultVersion: Version,
    val versions: List<VersionInfo>,
  )

  @Serializable
  data class VersionInfo(
    @Serializable(with = LooseVersionSerializer::class) val version: Version,
    @Serializable(with = OperatingSystemSerializer::class) val os: OperatingSystem,
    @Serializable(with = CpuArchitectureSerializer::class) val arch: CpuArchitecture? = null,
    val size: Long,
    val sha512DigestHex: String,
  )

  private object OperatingSystemSerializer : KSerializer<OperatingSystem> {
    override val descriptor =
      PrimitiveSerialDescriptor(
        "com.google.firebase.dataconnect.gradle.plugin.OperatingSystem",
        PrimitiveKind.STRING,
      )

    override fun deserialize(decoder: Decoder): OperatingSystem =
      decoder.decodeString().let { serializedValue ->
        OperatingSystem.entries.singleOrNull { it.serializedValue == serializedValue }
          ?: throw DataConnectGradleException(
            "nd5z2jk4hr",
            "Unknown operating system: $serializedValue " +
              "(must be one of ${OperatingSystem.entries.joinToString { it.serializedValue }})"
          )
      }

    override fun serialize(encoder: Encoder, value: OperatingSystem) =
      encoder.encodeString(value.serializedValue)
  }

  private object CpuArchitectureSerializer : KSerializer<CpuArchitecture> {
    override val descriptor =
      PrimitiveSerialDescriptor(
        "com.google.firebase.dataconnect.gradle.plugin.CpuArchitecture",
        PrimitiveKind.STRING,
      )

    override fun deserialize(decoder: Decoder): CpuArchitecture =
      decoder.decodeString().let { serializedValue ->
        CpuArchitecture.entries.singleOrNull { it.serializedValue == serializedValue }
          ?: throw DataConnectGradleException(
            "yxnvjm2nxe",
            "Unknown CPU architecture: $serializedValue " +
              "(must be one of ${CpuArchitecture.entries.joinToString { it.serializedValue }})"
          )
      }

    override fun serialize(encoder: Encoder, value: CpuArchitecture) =
      encoder.encodeString(value.serializedValue)
  }

  val OperatingSystem.serializedValue: String
    get() =
      when (this) {
        OperatingSystem.Windows -> "windows"
        OperatingSystem.MacOS -> "macos"
        OperatingSystem.Linux -> "linux"
      }

  val CpuArchitecture.serializedValue: String
    get() =
      when (this) {
        CpuArchitecture.AMD64 -> "amd64"
        CpuArchitecture.ARM64 -> "arm64"
      }
}
