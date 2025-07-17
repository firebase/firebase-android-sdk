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
@file:Suppress("LeakingThis")

package com.google.firebase.dataconnect.gradle.plugin

import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantExtension
import com.android.build.api.variant.VariantExtensionConfig
import com.google.firebase.dataconnect.gradle.plugin.DataConnectDslExtension.DataConnectCodegenDslExtension
import com.google.firebase.dataconnect.gradle.plugin.DataConnectDslExtension.DataConnectEmulatorDslExtension
import com.google.firebase.dataconnect.gradle.plugin.DataConnectDslExtension.DataConnectKtfmtDslExtension
import java.io.File
import javax.inject.Inject
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.newInstance

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
) : VariantExtension {

  @Inject
  @Suppress("unused")
  constructor(
    extensionConfig: VariantExtensionConfig<*>,
    objectFactory: ObjectFactory
  ) : this(
    extensionConfig.variant,
    extensionConfig.buildTypeExtension(),
    extensionConfig.productFlavorsExtensions(),
    objectFactory,
  )

  /** @see DataConnectDslExtension.configDir */
  abstract val configDir: Property<File>
  init {
    configDir.setFrom(
      variant,
      buildTypeExtension,
      productFlavorExtensions,
      "configDir",
      DataConnectDslExtension::configDir,
    )
  }

  /** @see DataConnectDslExtension.dataConnectExecutable */
  abstract val dataConnectExecutable: Property<DataConnectExecutable>
  init {
    dataConnectExecutable.setFrom(
      variant,
      buildTypeExtension,
      productFlavorExtensions,
      "dataConnectExecutable",
      DataConnectDslExtension::dataConnectExecutable,
    )
  }

  /** @see DataConnectDslExtension.codegen */
  val codegen: DataConnectCodegenVariantDslExtension =
    objectFactory.newInstance(
      variant,
      buildTypeExtension.codegen,
      productFlavorExtensions.map { it.codegen },
    )

  /** @see DataConnectDslExtension.emulator */
  val emulator: DataConnectEmulatorVariantDslExtension =
    objectFactory.newInstance(
      variant,
      buildTypeExtension.emulator,
      productFlavorExtensions.map { it.emulator },
    )

  /** @see DataConnectDslExtension.ktfmt */
  val ktfmt: DataConnectKtfmtVariantDslExtension =
    objectFactory.newInstance(
      variant,
      buildTypeExtension.ktfmt,
      productFlavorExtensions.map { it.ktfmt },
    )

  /** Values to use when performing code generation. */
  abstract class DataConnectCodegenVariantDslExtension
  @Inject
  constructor(
    variant: Variant,
    buildTypeExtension: DataConnectCodegenDslExtension,
    productFlavorExtensions: List<DataConnectCodegenDslExtension>,
  ) {
    /** @see DataConnectCodegenDslExtension.connectors */
    abstract val connectors: Property<Collection<String>>
    init {
      connectors.setFrom(
        variant,
        buildTypeExtension,
        productFlavorExtensions,
        "connectors",
        DataConnectCodegenDslExtension::connectors,
      )
    }
  }

  /** Values to use when running the Data Connect emulator. */
  abstract class DataConnectEmulatorVariantDslExtension
  @Inject
  constructor(
    variant: Variant,
    buildTypeExtension: DataConnectEmulatorDslExtension,
    productFlavorExtensions: List<DataConnectEmulatorDslExtension>,
  ) {
    /** @see DataConnectEmulatorDslExtension.postgresConnectionUrl */
    abstract val postgresConnectionUrl: Property<String>
    init {
      postgresConnectionUrl.setFrom(
        variant,
        buildTypeExtension,
        productFlavorExtensions,
        "postgresConnectionUrl",
        DataConnectEmulatorDslExtension::postgresConnectionUrl
      )
    }

    /** @see DataConnectEmulatorDslExtension.schemaExtensionsOutputEnabled */
    abstract val schemaExtensionsOutputEnabled: Property<Boolean>
    init {
      schemaExtensionsOutputEnabled.setFrom(
        variant,
        buildTypeExtension,
        productFlavorExtensions,
        "schemaExtensionsOutputEnabled",
        DataConnectEmulatorDslExtension::schemaExtensionsOutputEnabled
      )
    }
  }

  /** Values to use formatting code with ktfmt. */
  abstract class DataConnectKtfmtVariantDslExtension
  @Inject
  constructor(
    variant: Variant,
    buildTypeExtension: DataConnectKtfmtDslExtension,
    productFlavorExtensions: List<DataConnectKtfmtDslExtension>,
  ) {
    /** @see DataConnectKtfmtDslExtension.jarFile */
    abstract val jarFile: Property<File>
    init {
      jarFile.setFrom(
        variant,
        buildTypeExtension,
        productFlavorExtensions,
        "jarFile",
        DataConnectKtfmtDslExtension::jarFile
      )
    }
  }

  private companion object {

    fun <PropertyType : Any, ExtensionType : Any> Property<PropertyType>.setFrom(
      variant: Variant,
      buildTypeExtension: ExtensionType,
      productFlavorExtensions: List<ExtensionType>,
      name: String,
      getValue: (ExtensionType) -> PropertyType?,
    ) {
      val values = buildMap {
        getValue(buildTypeExtension)?.let { put("BuildType:${variant.buildType}", it) }
        val productFlavorNames = variant.productFlavors.map { "${it.first}=${it.second}" }
        productFlavorExtensions.forEachIndexed { i, productFlavorExtension ->
          getValue(productFlavorExtension)?.let {
            put("ProductFlavor:${productFlavorNames[i]}", it)
          }
        }
      }

      if (values.size == 1) {
        set(values.values.single())
      } else if (values.size > 1) {
        throw DataConnectGradleException(
          "z9hmj4bmgs",
          "$name is specified in ${values.size} places," +
            " but at most one is supported: " +
            values.keys.sorted().joinToString(", ")
        )
      }
    }
  }
}
