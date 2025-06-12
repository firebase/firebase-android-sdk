/*
 * Copyright 2025 Google LLC
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

package com.google.firebase.gradle.plugins.services

import com.google.firebase.gradle.plugins.writeStream
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document

/**
 * Wrapper around [Documents][Document].
 *
 * Abstracts some common download functionality when dealing with documents, and also allows the
 * behavior to be more easily mocked and tested.
 */
class DocumentService {
  /**
   * Opens an [InputStream] at the specified [url].
   *
   * It's the caller's responsibility to _close_ the stream when done.
   */
  fun openStream(url: String): InputStream = URL(url).openStream()

  /**
   * Downloads the [Document] from the specified [url], and saves it to a [file].
   *
   * @return The same [file] instance when the document is downloaded, or null if the document
   *   wasn't found.
   */
  fun downloadToFile(url: String, file: File): File? =
    try {
      openStream(url).use { file.writeStream(it) }
    } catch (e: FileNotFoundException) {
      null
    }

  /**
   * Downloads the [Document] from the specified [url].
   *
   * @return The downloaded [Document] instance, or null if the document wasn't found.
   */
  fun downloadDocument(url: String): Document? =
    try {
      DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(openStream(url))
    } catch (e: FileNotFoundException) {
      null
    }
}
