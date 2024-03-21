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

package com.google.firebase.vertex.type

import android.graphics.Bitmap

/**
 * Interface representing data sent to and received from requests.
 *
 * One of:
 * * [TextPart] representing text or string based data.
 * * [ImagePart] representing image data.
 * * [BlobPart] representing MIME typed binary data.
 */
interface Part

/** Represents text or string based data sent to and received from requests. */
class TextPart(val text: String) : Part

/**
 * Represents image data sent to and received from requests. When this is sent to the server it is
 * converted to jpeg encoding at 80% quality.
 */
class ImagePart(val image: Bitmap) : Part

/** Represents binary data with an associated MIME type sent to and received from requests. */
class BlobPart(val mimeType: String, val blob: ByteArray) : Part

/** @return The part as a [String] if it represents text, and null otherwise */
fun Part.asTextOrNull(): String? = (this as? TextPart)?.text

/** @return The part as a [Bitmap] if it represents an image, and null otherwise */
fun Part.asImageOrNull(): Bitmap? = (this as? ImagePart)?.image

/** @return The part as a [BlobPart] if it represents a blob, and null otherwise */
fun Part.asBlobPartOrNull(): BlobPart? = this as? BlobPart
