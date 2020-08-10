// Copyright 2018 Google LLC
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

package com.google.firebase.database;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Instances of DatabaseError are passed to callbacks when an operation failed. They contain a
 * description of the specific error that occurred.
 */
public class DatabaseError {

  /** <strong>Internal use</strong> */
  public static final int DATA_STALE = -1;
  /** The server indicated that this operation failed */
  public static final int OPERATION_FAILED = -2;
  /** This client does not have permission to perform this operation */
  public static final int PERMISSION_DENIED = -3;
  /** The operation had to be aborted due to a network disconnect */
  public static final int DISCONNECTED = -4;

  // Preempted was removed, this is for here for completeness and history
  // public static final int PREEMPTED = -5;

  /** The supplied auth token has expired */
  public static final int EXPIRED_TOKEN = -6;
  /**
   * The specified authentication token is invalid. This can occur when the token is malformed,
   * expired, or the secret that was used to generate it has been revoked.
   */
  public static final int INVALID_TOKEN = -7;
  /** The transaction had too many retries */
  public static final int MAX_RETRIES = -8;
  /** The transaction was overridden by a subsequent set */
  public static final int OVERRIDDEN_BY_SET = -9;
  /** The service is unavailable */
  public static final int UNAVAILABLE = -10;
  /** An exception occurred in user code */
  public static final int USER_CODE_EXCEPTION = -11;

  // client codes
  /** The operation could not be performed due to a network error. */
  public static final int NETWORK_ERROR = -24;

  /** The write was canceled locally */
  public static final int WRITE_CANCELED = -25;

  /**
   * An unknown error occurred. Please refer to the error message and error details for more
   * information.
   */
  public static final int UNKNOWN_ERROR = -999;

  private static final Map<Integer, String> errorReasons = new HashMap<Integer, String>();

  static {
    // Firebase Database error codes
    errorReasons.put(DATA_STALE, "The transaction needs to be run again with current data");
    errorReasons.put(OPERATION_FAILED, "The server indicated that this operation failed");
    errorReasons.put(
        PERMISSION_DENIED, "This client does not have permission to perform this operation");
    errorReasons.put(DISCONNECTED, "The operation had to be aborted due to a network disconnect");
    errorReasons.put(EXPIRED_TOKEN, "The supplied auth token has expired");
    errorReasons.put(INVALID_TOKEN, "The supplied auth token was invalid");
    errorReasons.put(MAX_RETRIES, "The transaction had too many retries");
    errorReasons.put(OVERRIDDEN_BY_SET, "The transaction was overridden by a subsequent set");
    errorReasons.put(UNAVAILABLE, "The service is unavailable");
    errorReasons.put(
        USER_CODE_EXCEPTION,
        "User code called from the Firebase Database runloop threw an exception:\n");

    // client codes
    errorReasons.put(NETWORK_ERROR, "The operation could not be performed due to a network error");
    errorReasons.put(WRITE_CANCELED, "The write was canceled by the user.");
    errorReasons.put(UNKNOWN_ERROR, "An unknown error occurred");
  }

  private static final Map<String, Integer> errorCodes = new HashMap<String, Integer>();

  static {

    // Firebase Database error codes
    errorCodes.put("datastale", DATA_STALE);
    errorCodes.put("failure", OPERATION_FAILED);
    errorCodes.put("permission_denied", PERMISSION_DENIED);
    errorCodes.put("disconnected", DISCONNECTED);
    errorCodes.put("expired_token", EXPIRED_TOKEN);
    errorCodes.put("invalid_token", INVALID_TOKEN);
    errorCodes.put("maxretries", MAX_RETRIES);
    errorCodes.put("overriddenbyset", OVERRIDDEN_BY_SET);
    errorCodes.put("unavailable", UNAVAILABLE);

    // client codes
    errorCodes.put("network_error", NETWORK_ERROR);
    errorCodes.put("write_canceled", WRITE_CANCELED);
  }

  /**
   * <strong>For internal use</strong>
   *
   * @hide
   * @param status The status string
   * @return An error corresponding the to the status
   */
  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  public static DatabaseError fromStatus(String status) {
    return fromStatus(status, null);
  }

  /**
   * <strong>For internal use</strong>
   *
   * @hide
   * @param status The status string
   * @param reason The reason for the error
   * @return An error corresponding the to the status
   */
  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  public static DatabaseError fromStatus(String status, String reason) {
    return fromStatus(status, reason, null);
  }

  /**
   * <strong>For internal use</strong>
   *
   * @hide
   * @param code The error code
   * @return An error corresponding the to the code
   */
  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  public static DatabaseError fromCode(int code) {
    if (!errorReasons.containsKey(code)) {
      throw new IllegalArgumentException("Invalid Firebase Database error code: " + code);
    }
    String message = errorReasons.get(code);
    return new DatabaseError(code, message, null);
  }

  /**
   * <strong>For internal use</strong>
   *
   * @hide
   * @param status The status string
   * @param reason The reason for the error
   * @param details Additional details or null
   * @return An error corresponding the to the status
   */
  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  public static DatabaseError fromStatus(String status, String reason, String details) {
    Integer code = errorCodes.get(status.toLowerCase(Locale.US));
    if (code == null) {
      code = UNKNOWN_ERROR;
    }

    String message = reason == null ? errorReasons.get(code) : reason;
    return new DatabaseError(code, message, details);
  }

  @NonNull
  public static DatabaseError fromException(@NonNull Throwable e) {
    StringWriter stringWriter = new StringWriter();
    PrintWriter printWriter = new PrintWriter(stringWriter);
    e.printStackTrace(printWriter);
    String reason = errorReasons.get(USER_CODE_EXCEPTION) + stringWriter.toString();
    return new DatabaseError(USER_CODE_EXCEPTION, reason);
  }

  private final int code;
  private final String message;
  private final String details;

  private DatabaseError(int code, String message) {
    this(code, message, null);
  }

  private DatabaseError(int code, String message, String details) {
    this.code = code;
    this.message = message;
    this.details = (details == null) ? "" : details;
  }

  /** @return One of the defined status codes, depending on the error */
  public int getCode() {
    return code;
  }

  /** @return A human-readable description of the error */
  @NonNull
  public String getMessage() {
    return message;
  }

  /** @return Human-readable details on the error and additional information, e.g. links to docs; */
  @NonNull
  public String getDetails() {
    return details;
  }

  @Override
  public String toString() {
    return "DatabaseError: " + message;
  }

  /**
   * Can be used if a third party needs an Exception from Firebase Database for integration
   * purposes.
   *
   * @return An exception wrapping this error, with an appropriate message and no stack trace.
   */
  @NonNull
  public DatabaseException toException() {
    return new DatabaseException("Firebase Database error: " + message);
  }
}
