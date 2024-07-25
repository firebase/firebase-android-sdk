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

import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantExtension
import com.android.build.api.variant.VariantExtensionConfig
import com.google.firebase.dataconnect.gradle.plugin.DataConnectDslExtension.DataConnectCodegenDslExtension
import com.google.firebase.dataconnect.gradle.plugin.DataConnectDslExtension.DataConnectEmulatorDslExtension
import javax.inject.Inject
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

/** The common settings that apply to both code generation and running the emulator. */
abstract class DataConnectVariantBaseDslExtension(
  variant: Variant,
  buildTypeExtension: DataConnectBaseDslExtension,
  productFlavorExtensions: List<DataConnectBaseDslExtension>
) {

  abstract val connectors: Property<Collection<String>>
  abstract val dataConnectExecutable: RegularFileProperty
  abstract val configDir: DirectoryProperty

  init {
    val productFlavorExtByName = buildMap {
      val productFlavorNames = variant.productFlavors.map { "${it.first}=${it.second}" }
      productFlavorExtensions.forEachIndexed { index, productFlavorExtension ->
        put("productFlavor:${productFlavorNames[index]}", productFlavorExtension)
      }
    }

    fun <T> initializeProperty(
      name: String,
      getter: (DataConnectBaseDslExtension) -> T?,
      callback: (T) -> Unit,
    ) {
      val valueBySource =
        buildMap<String, T> {
          getter(buildTypeExtension)?.let { put("buildType:${variant.buildType}", it) }
          productFlavorExtByName.forEach { productFlavorName, productFlavorExtension ->
            getter(productFlavorExtension)?.let { put(productFlavorName, it) }
          }
        }

      if (valueBySource.size == 1) {
        callback(valueBySource.values.single())
      } else if (valueBySource.size > 1) {
        throw ConflictingSettingsException(
          "'$name' has conflicting values set ${valueBySource.size} places" +
            " (at most 1 place is supported): ${valueBySource.keys.sorted().joinToString(", ")}"
        )
      }
    }

    initializeProperty("connectors", DataConnectBaseDslExtension::connectors) { connectors.set(it) }

    initializeProperty(
      "dataConnectExecutable",
      DataConnectBaseDslExtension::dataConnectExecutable
    ) {
      dataConnectExecutable.set(it)
    }

    initializeProperty("configDir", DataConnectBaseDslExtension::configDir) { configDir.set(it) }
  }

  class ConflictingSettingsException(message: String) : GradleException(message)
}

/**
 * This is the extension type for extending [com.android.build.api.variant.Variant].
 *
 * There will be an instance of this type for each variant and the instance can be retrieved using
 * the [com.android.build.api.variant.Variant.getExtension] method.
 *
 * Variant objects and custom extensions can be passed to multiple plugins that have registered a
 * block with the `androidComponents.onVariants` method. Each plugin can reset the variant's field
 * values set by a predecessor in the invocation order (usually the registration order). Therefore,
 * all variant custom extension should use `org.gradle.api.provider.Property` for their fields.
 * These `org.gradle.api.provider.Property` can then be used as Task's Input and be sure to obtain
 * the last value set even if the value was reset after the Task was created.
 *
 * The instance is created by providing a configuration block to the
 * [com.android.build.api.variant.AndroidComponentsExtension.registerExtension] method.
 */
@Suppress("UnstableApiUsage")
abstract class DataConnectVariantDslExtension(
  variant: Variant,
  buildTypeExtension: DataConnectDslExtension,
  productFlavorExtensions: List<DataConnectDslExtension>,
  objectFactory: ObjectFactory,
) :
  DataConnectVariantBaseDslExtension(variant, buildTypeExtension, productFlavorExtensions),
  VariantExtension {

  @Inject
  @Suppress("unused")
  constructor(
    extensionConfig: VariantExtensionConfig<*>,
    objectFactory: ObjectFactory
  ) : this(
    extensionConfig.variant,
    extensionConfig.buildTypeExtension(DataConnectDslExtension::class.java),
    extensionConfig.productFlavorsExtensions(DataConnectDslExtension::class.java),
    objectFactory = objectFactory
  )

  val codegen: DataConnectCodegenVariantDslExtension =
    objectFactory.newInstance(
      DataConnectCodegenVariantDslExtension::class.java,
      variant,
      buildTypeExtension.codegen,
      productFlavorExtensions.map { it.codegen }
    )

  val emulator: DataConnectEmulatorVariantDslExtension =
    objectFactory.newInstance(
      DataConnectEmulatorVariantDslExtension::class.java,
      variant,
      buildTypeExtension.emulator,
      productFlavorExtensions.map { it.emulator }
    )

  /**
   * Values to use when performing code generation, which override the values from those defined in
   * the outer scope.
   */
  abstract class DataConnectCodegenVariantDslExtension
  @Inject
  constructor(
    variant: Variant,
    buildTypeExtension: DataConnectCodegenDslExtension,
    productFlavorExtensions: List<DataConnectCodegenDslExtension>
  ) : DataConnectVariantBaseDslExtension(variant, buildTypeExtension, productFlavorExtensions)

  /**
   * Values to use when running the Data Connect emulator, which override the values from those
   * defined in the outer scope.
   */
  abstract class DataConnectEmulatorVariantDslExtension
  @Inject
  constructor(
    variant: Variant,
    buildTypeExtension: DataConnectEmulatorDslExtension,
    productFlavorExtensions: List<DataConnectEmulatorDslExtension>
  ) : DataConnectVariantBaseDslExtension(variant, buildTypeExtension, productFlavorExtensions)
}
