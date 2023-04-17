// Copyright 2022 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.gradle.plugins

/** Replaces all matching substrings with an empty string (nothing) */
fun String.remove(regex: Regex) = replace(regex, "")

/** Replaces all matching substrings with an empty string (nothing) */
fun String.remove(str: String) = replace(str, "")

/**
 * Returns a sequence containing all elements.
 *
 * The operation is _terminal_.
 *
 * Syntax sugar for:
 * ```
 * take(count())
 * ```
 */
public fun <T> Sequence<T>.takeAll(): Sequence<T> = take(count())
