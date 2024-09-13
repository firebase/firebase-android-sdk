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

@file:Suppress("UnstableApiUsage")

package com.google.firebase.dataconnect.gradle.plugin

import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantExtensionConfig

inline fun <reified T : Any> Variant.getExtension(): T =
  getExtensionOrNull<T>()
    ?: throw IllegalStateException(
      "no extension ${T::class.qualifiedName} registered with variant $name"
    )

inline fun <reified T : Any> Variant.getExtensionOrNull(): T? = getExtension(T::class.java)

inline fun <reified T : Any> VariantExtensionConfig<*>.buildTypeExtension(): T =
  buildTypeExtension(T::class.java)

inline fun <reified T : Any> VariantExtensionConfig<*>.productFlavorsExtensions(): List<T> =
  productFlavorsExtensions(T::class.java)
