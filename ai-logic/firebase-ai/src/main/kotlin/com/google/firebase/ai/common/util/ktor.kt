/*
 * Copyright 2024 Google LLC
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

@file:Suppress("DEPRECATION") // a replacement for our purposes has not been published yet

package com.google.firebase.ai.common.util

import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Suspends and processes each line read from the [ByteReadChannel] until the channel is closed for
 * read.
 *
 * This extension function facilitates processing the stream of lines in a manner that takes into
 * account EOF/empty strings- and avoids calling [block] as such.
 *
 * Example usage:
 * ```
 * val channel: ByteReadChannel = ByteReadChannel("Hello, World!")
 * channel.onEachLine {
 *     println("Received line: $it")
 * }
 * ```
 *
 * @param block A suspending function to process each line.
 */
internal suspend fun ByteReadChannel.onEachLine(block: suspend (String) -> Unit) {
  while (!isClosedForRead) {
    awaitContent()
    val line = readUTF8Line()?.takeUnless { it.isEmpty() } ?: continue
    block(line)
  }
}

/**
 * Decodes a stream of JSON elements from the given [ByteReadChannel] into a [Flow] of objects of
 * type [T].
 *
 * This function takes in a stream of events, each with a set of named parts. Parts are separated by
 * an HTTP \r\n newline, events are separated by a double HTTP \r\n\r\n newline. This function
 * assumes every event will only contain a named "data" part with a JSON object. Each data JSON is
 * decoded into an instance of [T] and emitted as it is read from the channel.
 *
 * Example usage:
 * ```
 * val json = Json { ignoreUnknownKeys = true } // Create a Json instance with any configurations
 * val channel: ByteReadChannel = ByteReadChannel("data: {\"name\":\"Alice\"}\r\n\r\ndata: {\"name\":\"Bob\"}]")
 *
 * json.decodeToFlow<Person>(channel).collect { person ->
 *   println(person.name)
 * }
 * ```
 *
 * @param T The type of objects to decode from the JSON stream.
 * @param channel The [ByteReadChannel] from which the JSON stream will be read.
 * @return A [Flow] of objects of type [T].
 * @throws SerializationException in case of any decoding-specific error
 * @throws IllegalArgumentException if the decoded input is not a valid instance of [T]
 */
internal inline fun <reified T> Json.decodeToFlow(channel: ByteReadChannel): Flow<T> = channelFlow {
  channel.onEachLine {
    val data = it.removePrefix("data:")
    send(decodeFromString(data))
  }
}

/**
 * Writes the provided [bytes] to the channel and closes it.
 *
 * Just a wrapper around [writeFully] that closes the channel after writing is complete.
 *
 * @param bytes the data to send through the channel
 */
internal suspend fun ByteChannel.send(bytes: ByteArray) {
  writeFully(bytes)
  close()
}

/** String separator used in SSE communication to signal the end of a message. */
internal const val SSE_SEPARATOR = "\r\n\r\n"
