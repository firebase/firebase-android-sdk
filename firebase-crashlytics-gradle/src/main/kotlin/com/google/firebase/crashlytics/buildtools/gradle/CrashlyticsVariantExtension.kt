/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.crashlytics.buildtools.gradle

import com.android.build.api.variant.VariantExtension
import com.android.build.api.variant.VariantExtensionConfig
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsPlugin.Companion.UPGRADE_MSG
import java.io.Serializable
import javax.inject.Inject
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.slf4j.LoggerFactory

@Suppress("UnstableApiUsage", "LeakingThis") // The AGP extension api.
internal abstract class CrashlyticsVariantExtension
@Inject
constructor(config: VariantExtensionConfig<*>) : VariantExtension, Serializable {
  private val logger = LoggerFactory.getLogger(CrashlyticsVariantExtension::class.java)

  abstract val mappingFileUploadEnabled: Property<Boolean>
  abstract val nativeSymbolUploadEnabled: Property<Boolean>
  abstract val unstrippedNativeLibsOverride: ConfigurableFileCollection
  abstract val symbolGeneratorType: Property<String>
  abstract val breakpadBinary: RegularFileProperty

  init {
    val buildTypeExtension = config.buildTypeExtension(CrashlyticsExtension::class.java)
    val productFlavorsExtensions = config.productFlavorsExtensions(CrashlyticsExtension::class.java)

    for (crashlyticsExtension in productFlavorsExtensions.reversed() + buildTypeExtension) {
      // Only set the properties for non-null values.
      crashlyticsExtension.mappingFileUploadEnabled?.let(mappingFileUploadEnabled::set)
      crashlyticsExtension.nativeSymbolUploadEnabled?.let(nativeSymbolUploadEnabled::set)
      crashlyticsExtension.unstrippedNativeLibsDir?.let { unstrippedNativeLibsOverride.from(it) }
      crashlyticsExtension.symbolGeneratorType?.let(symbolGeneratorType::set)
      crashlyticsExtension.breakpadBinary?.let(breakpadBinary::set)

      // Warnings
      crashlyticsExtension.symbolGenerator?.let {
        throw GradleException(
          "The symbolGenerator closure field is deprecated. Remove it and use fields " +
            "[symbolGeneratorType] and/or [breakpadBinary] instead. $UPGRADE_MSG"
        )
      }
    }

    printDebugProperties(config)
  }

  private fun printDebugProperties(config: VariantExtensionConfig<*>) {
    logger.debug("CrashlyticsVariantExtension for variant: ${config.variant.name}")
    logger.debug("  mappingFileUploadEnabled: ${mappingFileUploadEnabled.orNull}")
    logger.debug("  nativeSymbolUploadEnabled: ${nativeSymbolUploadEnabled.orNull}")
    logger.debug("  unstrippedNativeLibsOverride: ${unstrippedNativeLibsOverride.asPath}")
    logger.debug("  symbolGeneratorType: ${symbolGeneratorType.orNull}")
    logger.debug("  breakpadBinary: ${breakpadBinary.orNull?.asFile?.path}")
  }
}
