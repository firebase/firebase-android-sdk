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

import java.io.File
import javax.inject.Inject
import org.gradle.api.file.RegularFile
import org.gradle.api.model.ObjectFactory
import org.gradle.kotlin.dsl.newInstance

abstract class DataConnectDslExtension @Inject constructor(objectFactory: ObjectFactory) {

  /** The directory containing `dataconnect.yaml` to use, instead of the default directories. */
  abstract var configDir: File?

  /** The Data Connect executable to use. */
  abstract var dataConnectExecutable: DataConnectExecutable?

  /** Convenience DSL for configuring [dataConnectExecutable]. */
  fun dataConnectExecutable(block: DataConnectExecutableBuilder.() -> Unit) {
    dataConnectExecutable =
      DataConnectExecutableBuilderImpl(dataConnectExecutable).apply(block).build()
  }

  /**
   * Values to use when performing code generation, which override the values from those defined in
   * the outer scope.
   */
  val codegen: DataConnectCodegenDslExtension =
    objectFactory.newInstance<DataConnectCodegenDslExtension>()

  /**
   * Configure values to use when performing code generation, which override the values from those
   * defined in the outer scope.
   */
  @Suppress("unused")
  fun codegen(block: DataConnectCodegenDslExtension.() -> Unit): Unit = block(codegen)

  /**
   * Values to use when running the Data Connect emulator, which override the values from those
   * defined in the outer scope.
   */
  val emulator: DataConnectEmulatorDslExtension =
    objectFactory.newInstance<DataConnectEmulatorDslExtension>()

  /**
   * Configure values to use when running the Data Connect emulator, which override the values from
   * those defined in the outer scope.
   */
  @Suppress("unused")
  fun emulator(block: DataConnectEmulatorDslExtension.() -> Unit): Unit = block(emulator)

  /**
   * Values to use when performing code generation, which override the values from those defined in
   * the outer scope.
   */
  abstract class DataConnectCodegenDslExtension {
    /**
     * The IDs of connectors defined by `dataconnect.yaml` for which to generate code.
     *
     * If `null` or an empty list, then generate code for _all_ connectors.
     */
    abstract var connectors: Collection<String>?
  }

  /**
   * Values to use when running the Data Connect emulator, which override the values from those
   * defined in the outer scope.
   */
  abstract class DataConnectEmulatorDslExtension {
    abstract var postgresConnectionUrl: String?
    abstract var schemaExtensionsOutputEnabled: Boolean?
  }

  interface DataConnectExecutableBuilder {
    var version: String?
    var file: File?
    var regularFile: RegularFile?
    var fileSizeInBytes: Long?
    var sha512DigestHex: String?
    var verificationEnabled: Boolean
  }

  private class DataConnectExecutableBuilderImpl(initialValues: DataConnectExecutable?) :
    DataConnectExecutableBuilder {
    private var _version: String? = null
    override var version: String?
      get() = _version
      set(value) {
        _version = value
        _file = null
        _regularFile = null
      }
    private var _file: File? = null
    override var file: File?
      get() = _file
      set(value) {
        _version = null
        _file = value
        _regularFile = null
      }
    private var _regularFile: RegularFile? = null
    override var regularFile: RegularFile?
      get() = _regularFile
      set(value) {
        _version = null
        _file = null
        _regularFile = value
      }

    override var fileSizeInBytes: Long? = null
    override var sha512DigestHex: String? = null
    override var verificationEnabled: Boolean = true

    fun updateFrom(info: DataConnectExecutable.File) {
      file = info.file
      updateFrom(info.verificationInfo)
    }

    fun updateFrom(info: DataConnectExecutable.RegularFile) {
      regularFile = info.file
      updateFrom(info.verificationInfo)
    }

    fun updateFrom(info: DataConnectExecutable.Version) {
      version = info.version
      updateFrom(info.verificationInfo)
    }

    fun updateFrom(info: DataConnectExecutable.VerificationInfo?) {
      verificationEnabled = info !== null
      fileSizeInBytes = info?.fileSizeInBytes
      sha512DigestHex = info?.sha512DigestHex
    }

    init {
      when (initialValues) {
        is DataConnectExecutable.File -> updateFrom(initialValues)
        is DataConnectExecutable.RegularFile -> updateFrom(initialValues)
        is DataConnectExecutable.Version -> updateFrom(initialValues)
        null -> {}
      }
    }

    fun build(): DataConnectExecutable? {
      val version = version
      val file = file
      val regularFile = regularFile
      val fileSizeInBytes = fileSizeInBytes
      val sha512DigestHex = sha512DigestHex
      val verificationEnabled = verificationEnabled

      if (version === null && file === null && regularFile === null) {
        return null
      } else if (version !== null && file !== null && regularFile !== null) {
        throw DataConnectGradleException(
          "vhtb9jjz87",
          "All of 'version', 'file', and 'regularFile' are set," +
            " but at most *one* of them may be set" +
            " (version=$version, file=$file, regularFile=$regularFile)"
        )
      } else if (version !== null && file !== null) {
        throw DataConnectGradleException(
          "fj95rq5t8k",
          "Both 'version' and 'file' are set," +
            " but at most *one* of 'version', 'file', and 'regularFile' may be set" +
            " (version=$version, file=$file, regularFile=$regularFile)"
        )
      } else if (version !== null && regularFile !== null) {
        throw DataConnectGradleException(
          "ye6abzj5jz",
          "Both 'version' and 'regularFile' are set," +
            " but at most *one* of 'version', 'file', and 'regularFile' may be set" +
            " (version=$version, file=$file, regularFile=$regularFile)"
        )
      } else if (file !== null && regularFile !== null) {
        throw DataConnectGradleException(
          "nw79x53zdq",
          "Both 'file' and 'regularFile' are set," +
            " but at most *one* of 'version', 'file', and 'regularFile' may be set" +
            " (version=$version, file=$file, regularFile=$regularFile)"
        )
      }

      val verificationInfo: DataConnectExecutable.VerificationInfo? =
        if (!verificationEnabled) {
          null
        } else if (fileSizeInBytes === null && sha512DigestHex === null) {
          if (version !== null) {
            DataConnectExecutable.VerificationInfo.forVersion(version)
          } else {
            throw DataConnectGradleException(
              "8s9venv4ch",
              "Both 'fileSizeInBytes' and 'sha512DigestHex' were null" +
                " but _both_ must be set when verificationEnabled==true" +
                " and file!=null or regularFile!=null" +
                " (file=$file regularFile=$regularFile)"
            )
          }
        } else if (fileSizeInBytes === null || sha512DigestHex === null) {
          throw DataConnectGradleException(
            "gjzykv9pqq",
            "Both 'fileSizeInBytes' and 'sha512DigestHex' have to be set or both unset" +
              " when verificationEnabled==true, but one of them was set and the other was not" +
              " (fileSizeInBytes=$fileSizeInBytes, sha512DigestHex=$sha512DigestHex)"
          )
        } else {
          DataConnectExecutable.VerificationInfo(
            fileSizeInBytes = fileSizeInBytes,
            sha512DigestHex = sha512DigestHex,
          )
        }

      return if (version !== null) {
        DataConnectExecutable.Version(version = version, verificationInfo = verificationInfo)
      } else if (file !== null) {
        DataConnectExecutable.File(file = file, verificationInfo = verificationInfo)
      } else if (regularFile !== null) {
        DataConnectExecutable.RegularFile(file = regularFile, verificationInfo = verificationInfo)
      } else {
        throw DataConnectGradleException(
          "yg49q5nzxt",
          "INTERNAL ERROR: version===null && file===null && regularFile===null"
        )
      }
    }
  }
}
