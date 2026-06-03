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

package com.google.firebase.dataconnect.util

/** A class that wraps a reference and associates a tag with it. */
internal data class TaggedReference<out Tag, out T>(val tag: Tag, val ref: T)

/**
 * Returns a new [TaggedReference] with the same [tag], but with its [ref] transformed by applying
 * the given [block] function to the current [ref].
 */
internal inline fun <Tag, T, U> TaggedReference<Tag, T>.map(
  block: (T) -> U
): TaggedReference<Tag, U> = TaggedReference(tag, block(ref))
