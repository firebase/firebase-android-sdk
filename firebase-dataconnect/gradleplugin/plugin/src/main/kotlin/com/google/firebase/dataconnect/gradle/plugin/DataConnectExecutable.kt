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
import java.io.Serializable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

// The following command was used to generate the `serialVersionUID` constants for each class.
// serialver -classpath \
//   plugin/build/classes/kotlin/main:$(find $HOME/.gradle/wrapper/dists -name
// gradle-core-api-8.5.jar -printf '%p:') \
// com.google.firebase.dataconnect.gradle.plugin.DataConnectExecutableInput\${VerificationInfo,File,RegularFile,Version}

sealed interface DataConnectExecutable {

  data class VerificationInfo(val fileSizeInBytes: Long, val sha512DigestHex: String) :
    Serializable {

    companion object {
      fun forVersion(version: String): VerificationInfo {
        val versions = VersionsJson.load().versions
        val versionInfo =
          versions[version]
            ?: throw DataConnectGradleException(
              "3svd27ch8y",
              "File size and SHA512 digest is not known for version: $version"
            )
        return VerificationInfo(versionInfo.size, versionInfo.sha512DigestHex)
      }
    }
  }

  data class File(val file: java.io.File, val verificationInfo: VerificationInfo?) :
    DataConnectExecutable

  data class RegularFile(
    val file: org.gradle.api.file.RegularFile,
    val verificationInfo: VerificationInfo?
  ) : DataConnectExecutable

  data class Version(val version: String, val verificationInfo: VerificationInfo?) :
    DataConnectExecutable {
    companion object {

      private val defaultVersion: String
        get() = VersionsJson.load().default

      fun forVersionWithDefaultVerificationInfo(version: String): Version {
        val verificationInfo = DataConnectExecutable.VerificationInfo.forVersion(version)
        return Version(version, verificationInfo)
      }

      fun forDefaultVersionWithDefaultVerificationInfo(): Version =
        forVersionWithDefaultVerificationInfo(defaultVersion)
    }
  }

  @OptIn(ExperimentalSerializationApi::class)
  object VersionsJson {

    private const val RESOURCE_PATH =
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
