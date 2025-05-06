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

package com.google.firebase.ai.common

import com.google.firebase.ai.type.GenerateContentResponse
import io.ktor.serialization.JsonConvertException
import kotlinx.coroutines.TimeoutCancellationException

/** Parent class for any errors that occur. */
internal sealed class FirebaseCommonAIException(message: String, cause: Throwable? = null) :
  RuntimeException(message, cause) {
  companion object {

    /**
     * Converts a [Throwable] to a [FirebaseCommonAIException].
     *
     * Will populate default messages as expected, and propagate the provided [cause] through the
     * resulting exception.
     */
    fun from(cause: Throwable): FirebaseCommonAIException =
      when (cause) {
        is FirebaseCommonAIException -> cause
        is JsonConvertException,
        is kotlinx.serialization.SerializationException ->
          SerializationException(
            "Something went wrong while trying to deserialize a response from the server.",
            cause,
          )
        is TimeoutCancellationException ->
          RequestTimeoutException("The request failed to complete in the allotted time.")
        else -> UnknownException("Something unexpected happened.", cause)
      }
  }
}

/** Something went wrong while trying to deserialize a response from the server. */
internal class SerializationException(message: String, cause: Throwable? = null) :
  FirebaseCommonAIException(message, cause)

/** The server responded with a non 200 response code. */
internal class ServerException(message: String, cause: Throwable? = null) :
  FirebaseCommonAIException(message, cause)

/** The server responded that the API Key is no valid. */
internal class InvalidAPIKeyException(message: String, cause: Throwable? = null) :
  FirebaseCommonAIException(message, cause)

/**
 * A request was blocked for some reason.
 *
 * See the [response's][response] `promptFeedback.blockReason` for more information.
 *
 * @property response the full server response for the request.
 */
internal class PromptBlockedException
internal constructor(
  val response: GenerateContentResponse.Internal?,
  cause: Throwable? = null,
  message: String? = null,
) :
  FirebaseCommonAIException(
    "Prompt was blocked: ${response?.promptFeedback?.blockReason?.name?: message}",
    cause,
  ) {
  internal constructor(message: String, cause: Throwable? = null) : this(null, cause, message)
}

/**
 * The user's location (region) is not supported by the API.
 *
 * See the Google documentation for a
 * [list of regions](https://ai.google.dev/available_regions#available_regions) (countries and
 * territories) where the API is available.
 */
internal class UnsupportedUserLocationException(cause: Throwable? = null) :
  FirebaseCommonAIException("User location is not supported for the API use.", cause)

/**
 * Some form of state occurred that shouldn't have.
 *
 * Usually indicative of consumer error.
 */
internal class InvalidStateException(message: String, cause: Throwable? = null) :
  FirebaseCommonAIException(message, cause)

/**
 * A request was stopped during generation for some reason.
 *
 * @property response the full server response for the request
 */
internal class ResponseStoppedException(
  val response: GenerateContentResponse.Internal,
  cause: Throwable? = null
) :
  FirebaseCommonAIException(
    "Content generation stopped. Reason: ${response.candidates?.first()?.finishReason?.name}",
    cause,
  )

/**
 * A request took too long to complete.
 *
 * Usually occurs due to a user specified [timeout][RequestOptions.timeout].
 */
internal class RequestTimeoutException(message: String, cause: Throwable? = null) :
  FirebaseCommonAIException(message, cause)

/** The quota for this API key is depleted, retry this request at a later time. */
internal class QuotaExceededException(message: String, cause: Throwable? = null) :
  FirebaseCommonAIException(message, cause)

/** The service is not enabled for this project. Visit the Firebase Console to enable it. */
internal class ServiceDisabledException(message: String, cause: Throwable? = null) :
  FirebaseCommonAIException(message, cause)

/** Catch all case for exceptions not explicitly expected. */
internal class UnknownException(message: String, cause: Throwable? = null) :
  FirebaseCommonAIException(message, cause)

internal class ContentBlockedException(message: String, cause: Throwable? = null) :
  FirebaseCommonAIException(message, cause)

internal fun makeMissingCaseException(
  source: String,
  ordinal: Int
): com.google.firebase.ai.type.SerializationException {
  return com.google.firebase.ai.type.SerializationException(
    """
    |Missing case for a $source: $ordinal
    |This error indicates that one of the `toInternal` conversions needs updating.
    |If you're a developer seeing this exception, please file an issue on our GitHub repo:
    |https://github.com/firebase/firebase-android-sdk
  """
      .trimMargin()
  )
}
