// Copyright 2020 Google LLC
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
package com.google.firebase.messaging;

import java.util.Locale;

/**
 * Firebase message send exception.
 *
 * <p>This will be passed to {@link FirebaseMessagingService#onSendError} on errors that prevented a
 * message from being sent via {@link FirebaseMessaging#send}
 */
public final class SendException extends Exception {

  /** Unknown error. */
  public static final int ERROR_UNKNOWN = 0;

  /** Message was sent with invalid parameters. */
  public static final int ERROR_INVALID_PARAMETERS = 1;

  /** Message exceeded the maximum payload size. */
  public static final int ERROR_SIZE = 2;

  /** Message time to live (TTL) was exceeded before the message could be sent. */
  public static final int ERROR_TTL_EXCEEDED = 3;

  /** App has too many pending messages so this one was dropped. */
  public static final int ERROR_TOO_MANY_MESSAGES = 4;

  private final int errorCode;

  SendException(String error) {
    super(error);
    errorCode = parseErrorCode(error);
  }

  public int getErrorCode() {
    return errorCode;
  }

  private int parseErrorCode(String error) {
    if (error == null) {
      return ERROR_UNKNOWN;
    }
    switch (error.toLowerCase(Locale.US)) {
      case "invalid_parameters":
      case "missing_to":
        return ERROR_INVALID_PARAMETERS;
      case "messagetoobig":
        return ERROR_SIZE;
      case "service_not_available":
        return ERROR_TTL_EXCEEDED;
      case "toomanymessages":
        return ERROR_TOO_MANY_MESSAGES;
      default:
        return ERROR_UNKNOWN;
    }
  }
}
