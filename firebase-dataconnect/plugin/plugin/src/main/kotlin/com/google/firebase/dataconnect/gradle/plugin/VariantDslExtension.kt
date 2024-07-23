/*
 * Copyright (C) 2023 The Android Open Source Project
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
import org.gradle.api.provider.Property

/**
 * This is the extension type for extending [com.android.build.api.variant.Variant].
 *
 * There will be an instance of this type for each variant and the
 * instance can be retrieved using the [com.android.build.api.variant.Variant.getExtension]
 * method.
 *
 * Variant objects and custom extensions can be passed to multiple plugins that have
 * registed a block with the `androidComponents.onVariants` method. Each plugin can reset
 * the variant's field values set by a predecessor in the invocation order (usually the registration order).
 * Therefore, all variant custom extension should use `org.gradle.api.provider.Property` for their
 * fields. These `org.gradle.api.provider.Property` can then be used as Task's Input and be sure to
 * obtain the last value set even if the value was reset after the Task was created.
 *
 * The instance is created by providing a configuration block to the
 * [com.android.build.api.variant.AndroidComponents.registerExtension] method.
 *
 * Since this type will be used as a Task input (see [VerifierTask]), make it extend
 * [java.io.Serializable]
 *
 * In this recipe, check [CustomSettings] for how this type is instantiated along extending
 * other DSL elements.
 */
abstract class VariantDslExtension @Inject constructor(
    // Do not keep a reference on the VariantExtensionConfig as it is not serializable,
    // use the constructor to extract the values for the BuildType/ProductFlavor extensions
    // that can be obtained from the VariantExtensionConfig.
    extensionConfig: VariantExtensionConfig<*>
): VariantExtension, java.io.Serializable {
    // It is important to declare all fields as properties so that any plugin can keep a reference
    // to the `org.gradle.api.provider.Property` as a Task input and be sure to get the final value.
    abstract val variantSettingOne: Property<String>
    abstract val variantSettingTwo: Property<Int>

    init {
        variantSettingOne.set(
            """${extensionConfig.buildTypeExtension(BuildTypeDslExtension::class.java).buildTypeSettingOne}_
            ${extensionConfig.productFlavorsExtensions(ProductFlavorDslExtension::class.java)
                .map(ProductFlavorDslExtension::productFlavorSettingOne)
                .joinToString(separator = "_")}"""
        )
        variantSettingTwo.set(0)
    }
}
