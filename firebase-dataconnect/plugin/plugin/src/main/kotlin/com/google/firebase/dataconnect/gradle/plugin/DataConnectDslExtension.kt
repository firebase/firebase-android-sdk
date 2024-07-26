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

import org.gradle.api.model.ObjectFactory
import org.gradle.kotlin.dsl.newInstance
import java.io.File
import javax.inject.Inject

/** The common settings that apply to both code generation and running the emulator. */
abstract class DataConnectBaseDslExtension {

  /** Returns the empty list, but using this function instead can improve readability. */
  fun allConnectors(): List<String> = emptyList()

  /** The Data Connect executable to use. */
  abstract var dataConnectExecutable: File?

  /** The directory containing `dataconnect.yaml` to use, instead of the default directories. */
  abstract var configDir: File?

  /** The IDs of connectors defined by `dataconnect.yaml` to use. */
  abstract var connectors: Collection<String>?
}

abstract class DataConnectDslExtension @Inject constructor(objectFactory: ObjectFactory) :
  DataConnectBaseDslExtension() {

  /**
   * Values to use when performing code generation, which override the values from those defined in
   * the outer scope.
   */
  val codegen: DataConnectCodegenDslExtension = objectFactory.newInstance<DataConnectCodegenDslExtension>()

  /**
   * Configure values to use when performing code generation, which override the values from those
   * defined in the outer scope.
   */
  @Suppress("unused")
  fun codegen(block: DataConnectCodegenDslExtension.() -> Unit) {
    block(codegen)
  }

  /**
   * Values to use when running the Data Connect emulator, which override the values from those
   * defined in the outer scope.
   */
  val emulator: DataConnectEmulatorDslExtension = objectFactory.newInstance<DataConnectEmulatorDslExtension>()

  /**
   * Configure values to use when running the Data Connect emulator, which override the values from
   * those defined in the outer scope.
   */
  @Suppress("unused")
  fun emulator(block: DataConnectEmulatorDslExtension.() -> Unit) {
    block(emulator)
  }

  /**
   * Values to use when performing code generation, which override the values from those defined in
   * the outer scope.
   */
  abstract class DataConnectCodegenDslExtension : DataConnectBaseDslExtension()

  /**
   * Values to use when running the Data Connect emulator, which override the values from those
   * defined in the outer scope.
   */
  abstract class DataConnectEmulatorDslExtension : DataConnectBaseDslExtension() {
    abstract var postgresConnectionUrl: String?
  }
}
