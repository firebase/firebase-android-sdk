// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.remoteconfig.internal;

import static com.google.firebase.remoteconfig.internal.Code.ABORTED;
import static com.google.firebase.remoteconfig.internal.Code.ALREADY_EXISTS;
import static com.google.firebase.remoteconfig.internal.Code.CANCELLED;
import static com.google.firebase.remoteconfig.internal.Code.DATA_LOSS;
import static com.google.firebase.remoteconfig.internal.Code.DEADLINE_EXCEEDED;
import static com.google.firebase.remoteconfig.internal.Code.FAILED_PRECONDITION;
import static com.google.firebase.remoteconfig.internal.Code.INTERNAL;
import static com.google.firebase.remoteconfig.internal.Code.INVALID_ARGUMENT;
import static com.google.firebase.remoteconfig.internal.Code.NOT_FOUND;
import static com.google.firebase.remoteconfig.internal.Code.OK;
import static com.google.firebase.remoteconfig.internal.Code.OUT_OF_RANGE;
import static com.google.firebase.remoteconfig.internal.Code.PERMISSION_DENIED;
import static com.google.firebase.remoteconfig.internal.Code.RESOURCE_EXHAUSTED;
import static com.google.firebase.remoteconfig.internal.Code.UNAUTHENTICATED;
import static com.google.firebase.remoteconfig.internal.Code.UNAVAILABLE;
import static com.google.firebase.remoteconfig.internal.Code.UNIMPLEMENTED;
import static com.google.firebase.remoteconfig.internal.Code.UNKNOWN;

import androidx.annotation.IntDef;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * The set of Firebase Remote Config status codes. The codes are based on <a
 * href="https://github.com/googleapis/googleapis/blob/master/google/rpc/code.proto">Canonical error
 * codes for Google APIs</a>.
 *
 * @author Miraziz Yusupov
 */
@IntDef({
  OK,
  CANCELLED,
  UNKNOWN,
  INVALID_ARGUMENT,
  DEADLINE_EXCEEDED,
  NOT_FOUND,
  ALREADY_EXISTS,
  PERMISSION_DENIED,
  UNAUTHENTICATED,
  RESOURCE_EXHAUSTED,
  FAILED_PRECONDITION,
  ABORTED,
  OUT_OF_RANGE,
  UNIMPLEMENTED,
  INTERNAL,
  UNAVAILABLE,
  DATA_LOSS
})
@Retention(RetentionPolicy.SOURCE)
public @interface Code {
  /**
   * The operation completed successfully. FirebaseRemoteConfigServerException will never have a
   * status of OK.
   */
  int OK = 0;

  /** The operation was cancelled (typically by the caller). */
  int CANCELLED = 1;

  /** Unknown error or an error from a different error domain. */
  int UNKNOWN = 2;

  /**
   * Client specified an invalid argument. Note that this differs from FAILED_PRECONDITION.
   * INVALID_ARGUMENT indicates arguments that are problematic regardless of the state of the system
   * (e.g., an invalid field name).
   */
  int INVALID_ARGUMENT = 3;

  /**
   * Deadline expired before operation could complete. For operations that change the state of the
   * system, this error may be returned even if the operation has completed successfully. For
   * example, a successful response from a server could have been delayed long enough for the
   * deadline to expire.
   */
  int DEADLINE_EXCEEDED = 4;

  /** Some requested resource was not found. */
  int NOT_FOUND = 5;

  /** Some resource that we attempted to create already exists. */
  int ALREADY_EXISTS = 6;

  /** The caller does not have permission to execute the specified operation. */
  int PERMISSION_DENIED = 7;

  /** The request does not have valid authentication credentials for the operation. */
  int UNAUTHENTICATED = 16;

  /**
   * Some resource has been exhausted, perhaps a per-user quota, or perhaps the entire file system
   * is out of space.
   */
  int RESOURCE_EXHAUSTED = 8;

  /**
   * Operation was rejected because the system is not in a state required for the operation's
   * execution.
   */
  int FAILED_PRECONDITION = 9;

  /**
   * The operation was aborted, typically due to a concurrency issue like transaction aborts, etc.
   */
  int ABORTED = 10;

  /** Operation was attempted past the valid range. */
  int OUT_OF_RANGE = 11;

  /** Operation is not implemented or not supported/enabled. */
  int UNIMPLEMENTED = 12;

  /**
   * Internal errors. Means some invariants expected by underlying system has been broken. If you
   * see one of these errors, something is very broken.
   */
  int INTERNAL = 13;

  /**
   * The service is currently unavailable. This is a most likely a transient condition and may be
   * corrected by retrying with a backoff.
   */
  int UNAVAILABLE = 14;

  /** Unrecoverable data loss or corruption. */
  int DATA_LOSS = 15;
}
