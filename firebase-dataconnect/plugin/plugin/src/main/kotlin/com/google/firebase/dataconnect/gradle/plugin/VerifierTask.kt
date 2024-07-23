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

/*
 * Copyright 2022 The Android Open Source Project
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class VerifierTask: DefaultTask() {

    // In order of the task to be up-to-date when the data has not changed,
    // the task must declare an output, even if it's not used. Tasks with no
    // output are always run regardless of whether the inputs changed or not
    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @get:Input
    abstract val projectExtension: Property<ProjectDslExtension>

    @get:Input
    abstract val buildTypeExtension: Property<BuildTypeDslExtension>

    @get:Input
    abstract val productFlavorExtension: Property<ProductFlavorDslExtension>

    @get:Input
    abstract val variantExtension: Property<VariantDslExtension>

    @TaskAction
    fun taskAction() {
        println("<---- Project Extension ----->")
        println("settingOne : ${projectExtension.get().settingOne}")
        println("settingTwo : ${projectExtension.get().settingTwo}")
        println("<---- BuildType Extension ----->")
        println("buildTypeSettingOne: ${buildTypeExtension.get().buildTypeSettingOne}")
        println("buildTypeSettingTwo: ${buildTypeExtension.get().buildTypeSettingTwo}")
        println("<---- Product Flavor Extension ----->")
        println("productFlavorSettingOne: ${productFlavorExtension.get().productFlavorSettingOne}")
        println("productFlavorSettingTwo: ${productFlavorExtension.get().productFlavorSettingTwo}")
        println("<---- Variant Extension ----->")
        println("variantSettingOne: ${variantExtension.get().variantSettingOne.get()}")
        println("variantSettingTwo: ${variantExtension.get().variantSettingTwo.get()}")
    }
}
