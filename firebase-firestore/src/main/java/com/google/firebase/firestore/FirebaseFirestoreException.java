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

package com.google.firebase.firestore;

import static com.google.firebase.firestore.util.Preconditions.checkNotNull;

import android.util.SparseArray;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.FirebaseException;
import com.google.firebase.firestore.util.Assert;

/** A class of exceptions thrown by Cloud Firestore. */
public class FirebaseFirestoreException extends FirebaseException {
  /**
   * The set of Cloud Firestore status codes. The codes are the same at the ones exposed by gRPC
   * here: https://github.com/grpc/grpc/blob/master/doc/statuscodes.md
   */
  public enum Code {
    /**
     * The operation completed successfully. {@code FirebaseFirestoreException} will never have a
     * status of {@code OK}.
     */
    OK(0),

    /** The operation was cancelled (typically by the caller). */
    CANCELLED(1),

    /** Unknown error or an error from a different error domain. */
    UNKNOWN(2),

    /**
     * Client specified an invalid argument. Note that this differs from {@link
     * #FAILED_PRECONDITION}. {@code INVALID_ARGUMENT} indicates arguments that are problematic
     * regardless of the state of the system (e.g., an invalid field name).
     */
    INVALID_ARGUMENT(3),

    /**
     * Deadline expired before operation could complete. For operations that change the state of the
     * system, this error may be returned even if the operation has completed successfully. For
     * example, a successful response from a server could have been delayed long enough for the
     * deadline to expire.
     */
    DEADLINE_EXCEEDED(4),

    /** Some requested document was not found. */
    NOT_FOUND(5),

    /** Some document that we attempted to create already exists. */
    ALREADY_EXISTS(6),

    /** The caller does not have permission to execute the specified operation. */
    PERMISSION_DENIED(7),

    /**
     * Some resource has been exhausted, perhaps a per-user quota, or perhaps the entire file system
     * is out of space.
     */
    RESOURCE_EXHAUSTED(8),

    /**
     * Operation was rejected because the system is not in a state required for the operation's
     * execution.
     */
    FAILED_PRECONDITION(9),

    /**
     * The operation was aborted, typically due to a concurrency issue like transaction aborts, etc.
     */
    ABORTED(10),

    /** Operation was attempted past the valid range. */
    OUT_OF_RANGE(11),

    /** Operation is not implemented or not supported/enabled. */
    UNIMPLEMENTED(12),

    /**
     * Internal errors. Means some invariants expected by underlying system has been broken. If you
     * see one of these errors, something is very broken.
     */
    INTERNAL(13),

    /**
     * The service is currently unavailable. This is a most likely a transient condition and may be
     * corrected by retrying with a backoff.
     */
    UNAVAILABLE(14),

    /** Unrecoverable data loss or corruption. */
    DATA_LOSS(15),

    /** The request does not have valid authentication credentials for the operation. */
    UNAUTHENTICATED(16);

    private final int value;

    Code(int value) {
      this.value = value;
    }

    /** The numerical value of the code. */
    public int value() {
      return value;
    }

    // Create the canonical list of Status instances indexed by their code values.
    private static final SparseArray<Code> STATUS_LIST = buildStatusList();

    private static SparseArray<Code> buildStatusList() {
      SparseArray<Code> codes = new SparseArray<>();
      for (Code c : Code.values()) {
        Code existingValue = codes.get(c.value());
        if (existingValue != null) {
          throw new IllegalStateException(
              "Code value duplication between " + existingValue + "&" + c.name());
        }
        codes.put(c.value(), c);
      }
      return codes;
    }

    @NonNull
    public static Code fromValue(int value) {
      return STATUS_LIST.get(value, Code.UNKNOWN);
    }
  }

  @NonNull private final Code code;

  public FirebaseFirestoreException(@NonNull String detailMessage, @NonNull Code code) {
    super(detailMessage);
    checkNotNull(detailMessage, "Provided message must not be null.");
    Assert.hardAssert(
        code != Code.OK, "A FirebaseFirestoreException should never be thrown for OK");
    this.code = checkNotNull(code, "Provided code must not be null.");
  }

  public FirebaseFirestoreException(
      @NonNull String detailMessage, @NonNull Code code, @Nullable Throwable cause) {
    super(detailMessage, cause);
    checkNotNull(detailMessage, "Provided message must not be null.");
    Assert.hardAssert(
        code != Code.OK, "A FirebaseFirestoreException should never be thrown for OK");
    this.code = checkNotNull(code, "Provided code must not be null.");
  }

  /**
   * Gets the error code for the Cloud Firestore operation that failed.
   *
   * @return the code for the {@code FirebaseFirestoreException}.
   */
  @NonNull
  public Code getCode() {
    return code;
  }
}
