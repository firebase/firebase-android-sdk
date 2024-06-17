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

package com.google.firebase.vertexai.type

import com.google.firebase.vertexai.FirebaseVertexAI
import com.google.firebase.vertexai.internal.util.toPublic
import kotlinx.coroutines.TimeoutCancellationException

/** Parent class for any errors that occur from the [FirebaseVertexAI] SDK. */
sealed class FirebaseVertexAIException(message: String, cause: Throwable? = null) :
  RuntimeException(message, cause) {

  companion object {

    /**
     * Converts a [Throwable] to a [FirebaseVertexAIException].
     *
     * Will populate default messages as expected, and propagate the provided [cause] through the
     * resulting exception.
     */
    fun from(cause: Throwable): FirebaseVertexAIException =
      when (cause) {
        is FirebaseVertexAIException -> cause
        is com.google.ai.client.generativeai.common.GoogleGenerativeAIException ->
          when (cause) {
            is com.google.ai.client.generativeai.common.SerializationException ->
              SerializationException(cause.message ?: "", cause.cause)
            is com.google.ai.client.generativeai.common.ServerException ->
              ServerException(cause.message ?: "", cause.cause)
            is com.google.ai.client.generativeai.common.InvalidAPIKeyException ->
              InvalidAPIKeyException(cause.message ?: "")
            is com.google.ai.client.generativeai.common.PromptBlockedException ->
              PromptBlockedException(cause.response.toPublic(), cause.cause)
            is com.google.ai.client.generativeai.common.UnsupportedUserLocationException ->
              UnsupportedUserLocationException(cause.cause)
            is com.google.ai.client.generativeai.common.InvalidStateException ->
              InvalidStateException(cause.message ?: "", cause)
            is com.google.ai.client.generativeai.common.ResponseStoppedException ->
              ResponseStoppedException(cause.response.toPublic(), cause.cause)
            is com.google.ai.client.generativeai.common.RequestTimeoutException ->
              RequestTimeoutException(cause.message ?: "", cause.cause)
            is com.google.ai.client.generativeai.common.ServiceDisabledException ->
              ServiceDisabledException(cause.message ?: "", cause.cause)
            is com.google.ai.client.generativeai.common.UnknownException ->
              UnknownException(cause.message ?: "", cause.cause)
            else -> UnknownException(cause.message ?: "", cause)
          }
        is TimeoutCancellationException ->
          RequestTimeoutException("The request failed to complete in the allotted time.")
        else -> UnknownException("Something unexpected happened.", cause)
      }
  }
}

/** Something went wrong while trying to deserialize a response from the server. */
class SerializationException(message: String, cause: Throwable? = null) :
  FirebaseVertexAIException(message, cause)

/** The server responded with a non 200 response code. */
class ServerException(message: String, cause: Throwable? = null) :
  FirebaseVertexAIException(message, cause)

/** The server responded that the API Key is not valid. */
class InvalidAPIKeyException(message: String, cause: Throwable? = null) :
  FirebaseVertexAIException(message, cause)

/**
 * A request was blocked for some reason.
 *
 * See the [response's][response] `promptFeedback.blockReason` for more information.
 *
 * @property response the full server response for the request.
 */
// TODO(rlazo): Add secondary constructor to pass through the message?
class PromptBlockedException(val response: GenerateContentResponse, cause: Throwable? = null) :
  FirebaseVertexAIException(
    "Prompt was blocked: ${response.promptFeedback?.blockReason?.name}",
    cause,
  )

/**
 * The user's location (region) is not supported by the API.
 *
 * See the documentation for a
 * [list of regions](https://firebase.google.com/docs/vertex-ai/locations?platform=android#available-locations)
 * (countries and territories) where the API is available.
 */
// TODO(rlazo): Add secondary constructor to pass through the message?
class UnsupportedUserLocationException(cause: Throwable? = null) :
  FirebaseVertexAIException("User location is not supported for the API use.", cause)

/**
 * Some form of state occurred that shouldn't have.
 *
 * Usually indicative of consumer error.
 */
class InvalidStateException(message: String, cause: Throwable? = null) :
  FirebaseVertexAIException(message, cause)

/**
 * A request was stopped during generation for some reason.
 *
 * @property response the full server response for the request
 */
class ResponseStoppedException(val response: GenerateContentResponse, cause: Throwable? = null) :
  FirebaseVertexAIException(
    "Content generation stopped. Reason: ${response.candidates.first().finishReason?.name}",
    cause,
  )

/**
 * A request took too long to complete.
 *
 * Usually occurs due to a user specified [timeout][RequestOptions.timeout].
 */
class RequestTimeoutException(message: String, cause: Throwable? = null) :
  FirebaseVertexAIException(message, cause)

/**
 * The specified Vertex AI location is invalid.
 *
 * For a list of valid locations, see
 * [Vertex AI locations](https://cloud.google.com/vertex-ai/generative-ai/docs/learn/locations#available-regions)
 */
class InvalidLocationException(location: String, cause: Throwable? = null) :
  FirebaseVertexAIException("Invalid location \"${location}\"", cause)

/** The service is not enabled for this project. Visit the Firebase Console to enable it. */
class ServiceDisabledException(message: String, cause: Throwable? = null) :
  FirebaseVertexAIException(message, cause)

/** Catch all case for exceptions not explicitly expected. */
class UnknownException(message: String, cause: Throwable? = null) :
  FirebaseVertexAIException(message, cause)
