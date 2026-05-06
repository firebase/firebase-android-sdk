/*
 * Copyright 2026 Google LLC
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

@file:SharedWithAndroidTest

package com.google.firebase.dataconnect.testutil

/**
 * Annotation used to mark files that should be shared with the androidTest source set.
 *
 * This annotation is processed by the CopySharedWithAndroidTestFiles Gradle task. For performance
 * and simplicity, the task performs a rudimentary string-based check on the file's contents. To be
 * recognized, a line in the source file must, when trimmed, exactly equal either:
 * - `@file:SharedWithAndroidTest`
 * - `@file:com.google.firebase.dataconnect.testutil.SharedWithAndroidTest`
 *
 * Notably, "grouped syntax" like `@file:[JvmName("MyFile") SharedWithAndroidTest]` is NOT supported
 * and will not be recognized by the task.
 */
@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.SOURCE)
internal annotation class SharedWithAndroidTest
