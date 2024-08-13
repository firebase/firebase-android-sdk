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

// The following command was used to generate the `serialVersionUID` constants for each class.
// serialver -classpath \
//   plugin/build/classes/kotlin/main:$(find $HOME/.gradle/wrapper/dists -name
// gradle-core-api-8.5.jar -printf '%p:') \
// com.google.firebase.dataconnect.gradle.plugin.DataConnectExecutable\${VerificationInfo,File,RegularFile,Version}

sealed interface DataConnectExecutable : java.io.Serializable {

  data class VerificationInfo(val fileSizeInBytes: Long, val sha512DigestHex: String) :
    java.io.Serializable {
    companion object {
      private const val serialVersionUID: Long = 9100812685436842226L
      fun forVersion(version: String): VerificationInfo =
        when (version) {
          "1.3.4" ->
            VerificationInfo(
              fileSizeInBytes = 24125592L,
              sha512DigestHex =
                "3ec9317db593ebeacfea9756cdd08a02849296fbab67f32f3d811a766be6ce2506fc7a0cf5f5ea880926f0c4defa5ded965268f5dfe5d07eb80cef926f216c7e"
            )
          else ->
            throw DataConnectGradleException(
              "3svd27ch8y",
              "File size and SHA512 digest is not known for version: $version"
            )
        }
    }
  }

  data class File(val file: java.io.File, val verificationInfo: VerificationInfo?) :
    DataConnectExecutable {
    companion object {
      private const val serialVersionUID: Long = 1418763632146586078L
    }
  }

  data class RegularFile(
    val file: org.gradle.api.file.RegularFile,
    val verificationInfo: VerificationInfo?
  ) : DataConnectExecutable {
    companion object {
      private const val serialVersionUID: Long = 4824964901603066595L
    }
  }

  data class Version(val version: String, val verificationInfo: VerificationInfo?) :
    DataConnectExecutable {
    companion object {
      private const val serialVersionUID: Long = 1502768139059311352L
    }
  }
}
