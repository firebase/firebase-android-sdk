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

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.DslExtension
import com.android.build.api.variant.VariantExtensionConfig
import com.android.build.gradle.AppPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.register

/**
 * This custom plugin creates extension objects that can extend the project
 * definition, the build type and product flavors definitions.
 *
 * A variant extension is also added when the Variants have been initialized.
 *
 */
class DataConnectGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.logger.lifecycle("zzyzx Applying DataConnectGradlePlugin")

        // Registers a callback on the application of the Android Application plugin.
        // This allows the CustomPlugin to work whether it's applied before or after
        // the Android Application plugin.
        project.plugins.withType(AppPlugin::class.java) { _ ->

            // Look up the generic android component, we don't need anything specific
            // to a module type like application or library.
            val androidComponents =
                project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java)

            // Register the DSL extensions.
            // In this recipe, we provide all possible types of extensions : to the
            // Project, the BuildType, the ProductFlavor and finally the Variant.
            // Specific use cases might require extensing only some elements.
            //
            // the `dslExtension` is the DSL name that should be used by your extension
            // users to access the extension.
            // You can also call this API separately for each extension type, to give
            // them different public names.
            androidComponents.registerExtension(
                DslExtension.Builder("dslExtension")
                    .extendProjectWith(ProjectDslExtension::class.java)
                    .extendBuildTypeWith(BuildTypeDslExtension::class.java)
                    .extendProductFlavorWith(ProductFlavorDslExtension::class.java)
                    .build()
            ) { config: VariantExtensionConfig<*> ->

                // this block will be called after each Variant creation in order
                // to instantiate the Variant extension. The `config` parameter will
                // provide access to the Variant instance as well as other DSL
                // extensions that might be necessary to properly initialize the
                // VariantDslExtension instance.
                project.objects.newInstance(
                    VariantDslExtension::class.java,
                    config
                )
            }

            val android = project.extensions.getByType(CommonExtension::class.java)

            // Registers a callback to be called, when a variant is configured
            androidComponents.onVariants { variant ->

                // create task that will consumme all possible DSL and Variant
                // extension and display them.
                project.tasks.register<VerifierTask>(
                    "${variant.name}DumpAllExtensions"
                ) {
                    // Get the Variant Extension.
                    // In general, this is the only extension instance that should
                    // be set as an Input to the Task. The variant extension should
                    // provide access to values in the Project, BuildType and ProductFlavor
                    // extensions fields (possibly merged and/or combined).
                    variantExtension.set(
                        variant.getExtension(VariantDslExtension::class.java)
                    )

                    // In this recipe, we also inject the other extension objects.
                    // THIS IS DONE FOR VERIFICATION PURPOSES: you should probably
                    // never do this, instead rely on the Variant extension to provide
                    // access to all you task's input.

                    // Get the DSL project extension and set it as an input on the task.
                    projectExtension.set((android as ExtensionAware)
                        .extensions.getByType(ProjectDslExtension::class.java)
                    )
                    // Ge the build extension and set it as an input to the task.
                    buildTypeExtension.set(
                        android.buildTypes.getByName(variant.buildType!!).extensions
                            .getByType(BuildTypeDslExtension::class.java)
                    )
                    // Get the product flavor extension and set it as an input to the task
                    productFlavorExtension.set(
                        android.productFlavors.getByName(variant.flavorName!!).extensions
                            .getByType(ProductFlavorDslExtension::class.java)
                    )

                    output.set(project.layout.buildDirectory.dir("${variant.name}DumpAllExtensions"))
                }
            }
        }
    }
}
