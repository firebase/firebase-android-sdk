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
import org.gradle.api.model.ObjectFactory
import org.gradle.kotlin.dsl.newInstance

abstract class DataConnectDslExtension @Inject constructor(objectFactory: ObjectFactory) {

  /** The directory containing `dataconnect.yaml` to use, instead of the default directories. */
  abstract var configDir: File?

  /** The Data Connect executable to use. */
  abstract var dataConnectExecutable: File?

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
  }
}
