/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.dataconnect.gradle.plugin

/**
 * This is the extension type for extending [com.android.build.api.dsl.CommonExtension].
 *
 * There will be single instance of this type instantiated for the `android` extension.
 *
 * This extension type is registered by calling
 * [com.android.build.api.variant.AndroidComponents.registerExtension] method.
 *
 */
interface ProjectDslExtension {
    var settingOne: String
    var settingTwo: Int
}
