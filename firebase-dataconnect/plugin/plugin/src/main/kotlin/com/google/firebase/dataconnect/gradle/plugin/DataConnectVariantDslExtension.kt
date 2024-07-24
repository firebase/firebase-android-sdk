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

import com.android.build.api.variant.VariantExtension
import com.android.build.api.variant.VariantExtensionConfig
import javax.inject.Inject
import org.gradle.api.GradleException
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty

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
 * [com.android.build.api.variant.AndroidComponents.registerExtension] method.
 *
 * Since this type will be used as a Task input (see [DataConnectGenerateCodeTask]), make it extend
 * [java.io.Serializable]
 */
@Suppress("UnstableApiUsage")
abstract class DataConnectVariantDslExtension
@Inject
constructor(
  // Do not keep a reference on the VariantExtensionConfig as it is not serializable.
  extensionConfig: VariantExtensionConfig<*>,
  projectLayout: ProjectLayout,
) : VariantExtension, java.io.Serializable {
  abstract val connectors: ListProperty<String>
  abstract val dataConnectCliExecutable: RegularFileProperty

  init {
    connectors.convention(emptyList())

    dataConnectCliExecutable.convention(
      projectLayout.projectDirectory.file(
        "/google/src/cloud/dconeybe/codegen/google3/blaze-bin/third_party/firebase/dataconnect/emulator/cli/cli"
      )
    )

    valueFromExtensions(extensionConfig, "connectors", DataConnectDslExtension::connectors)?.let {
      connectors.set(it)
    }

    valueFromExtensions(
        extensionConfig,
        "dataConnectCliExecutable",
        DataConnectDslExtension::dataConnectCliExecutable
      )
      ?.let { dataConnectCliExecutable.set(it) }
  }

  private companion object {
    fun <T> valueFromExtensions(
      extensionConfig: VariantExtensionConfig<*>,
      name: String,
      getter: (DataConnectDslExtension) -> T?
    ): T? {
      val buildTypeExt = extensionConfig.buildTypeExtension(DataConnectDslExtension::class.java)
      val productFlavorExts =
        extensionConfig.productFlavorsExtensions(DataConnectDslExtension::class.java)
      val productFlavorNames =
        extensionConfig.variant.productFlavors.map { "${it.first}=${it.second}" }

      val valueBySource =
        buildMap<String, T> {
          getter(buildTypeExt)?.let { put("buildType:${extensionConfig.variant.buildType}", it) }
          productFlavorExts.forEachIndexed { index, productFlavorExt ->
            getter(productFlavorExt)?.let { put("productFlavor:${productFlavorNames[index]}", it) }
          }
        }

      return if (valueBySource.isEmpty()) {
        null
      } else if (valueBySource.size == 1) {
        valueBySource.values.single()
      } else {
        throw ConflictingSettingsException(
          "Setting $name has conflicting values set in: " +
            valueBySource.keys.sorted().joinToString { ", " }
        )
      }
    }
  }

  class ConflictingSettingsException(message: String) : GradleException(message)
}
