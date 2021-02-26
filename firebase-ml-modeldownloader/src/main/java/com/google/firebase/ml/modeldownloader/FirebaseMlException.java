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

package com.google.firebase.ml.modeldownloader;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import com.google.android.gms.common.internal.Preconditions;
import com.google.firebase.FirebaseException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Represents an Exception resulting from an operation on a {@link FirebaseModelDownloader}. Error
 * mappings should remain consistent with the original firebase_ml_sdk whenever possible.
 */
public class FirebaseMlException extends FirebaseException {
  /** The operation was cancelled (typically by the caller). */
  public static final int CANCELLED = 1;

  /** Unknown error or an error from a different error domain. */
  public static final int UNKNOWN = 2;

  /**
   * Client specified an invalid argument. Note that this differs from <code>FAILED_PRECONDITION
   * </code>. <code>INVALID_ARGUMENT</code> indicates arguments that are problematic regardless of
   * the state of the system (for example, an invalid field name).
   */
  public static final int INVALID_ARGUMENT = 3;

  /**
   * Deadline expired before operation could complete. For operations that change the state of the
   * system, this error may be returned even if the operation has completed successfully. For
   * example, a successful response from a server could have been delayed long enough for the
   * deadline to expire.
   */
  public static final int DEADLINE_EXCEEDED = 4;

  /** Some requested resource was not found. */
  public static final int NOT_FOUND = 5;

  /** Some resource that we attempted to create already exists. */
  public static final int ALREADY_EXISTS = 6;

  /** The caller does not have permission to execute the specified operation. */
  public static final int PERMISSION_DENIED = 7;

  /**
   * Some resource has been exhausted, perhaps a per-user quota, or perhaps the entire file system
   * is out of space.
   */
  public static final int RESOURCE_EXHAUSTED = 8;

  /**
   * Operation was rejected because the system is not in a state required for the operation's
   * execution.
   */
  public static final int FAILED_PRECONDITION = 9;

  /**
   * The operation was aborted, typically due to a concurrency issue like transaction aborts, etc.
   */
  public static final int ABORTED = 10;

  /** Operation was attempted past the valid range. */
  public static final int OUT_OF_RANGE = 11;

  /** Operation is not implemented or not supported/enabled. */
  public static final int UNIMPLEMENTED = 12;

  /**
   * Internal errors. Means some invariant expected by underlying system has been broken. If you see
   * one of these errors, something is very broken.
   */
  public static final int INTERNAL = 13;

  /**
   * The service is currently unavailable. This is a most likely a transient condition and may be
   * corrected by retrying with a backoff.
   *
   * <p>In ML Model Downloader, this error is mostly about the models being not available yet.
   */
  public static final int UNAVAILABLE = 14;

  /** The request does not have valid authentication credentials for the operation. */
  public static final int UNAUTHENTICATED = 16;

  /** There is no network connection. */
  public static final int NO_NETWORK_CONNECTION = 17;

  // ===============================================================================================
  // Error codes: 100 to 149 reserved for errors during model downloading/loading.
  /** There is not enough space left on the device. */
  public static final int NOT_ENOUGH_SPACE = 101;

  /** The downloaded model's hash doesn't match the expected value. */
  public static final int MODEL_HASH_MISMATCH = 102;

  /**
   * The download URL expired before download could complete. Usually, multiple download attempts
   * will be performed before this is returned.
   */
  public static final int DOWNLOAD_URL_EXPIRED = 121;

  /**
   * The set of Firebase ML status codes. The codes are based on <a
   * href="https://github.com/googleapis/googleapis/blob/master/google/rpc/code.proto">Canonical
   * error codes for Google APIs</a>
   */
  @IntDef({
    CANCELLED,
    UNKNOWN,
    INVALID_ARGUMENT,
    DEADLINE_EXCEEDED,
    NOT_FOUND,
    ALREADY_EXISTS,
    PERMISSION_DENIED,
    RESOURCE_EXHAUSTED,
    FAILED_PRECONDITION,
    ABORTED,
    OUT_OF_RANGE,
    UNIMPLEMENTED,
    INTERNAL,
    UNAVAILABLE,
    UNAUTHENTICATED,
    NO_NETWORK_CONNECTION,
    NOT_ENOUGH_SPACE,
    MODEL_HASH_MISMATCH,
    DOWNLOAD_URL_EXPIRED
  })
  @Retention(RetentionPolicy.CLASS)
  public @interface Code {}

  @Code private final int code;

  /** @hide */
  public FirebaseMlException(@NonNull String detailMessage, @Code int code) {
    super(Preconditions.checkNotEmpty(detailMessage, "Provided message must not be empty."));
    this.code = code;
  }

  /** Gets the error code for the Firebase ML operation that failed. */
  @Code
  public int getCode() {
    return code;
  }
}
