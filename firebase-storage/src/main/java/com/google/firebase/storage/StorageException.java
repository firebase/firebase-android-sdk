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

package com.google.firebase.storage;

import android.util.Log;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.common.internal.Preconditions;
import com.google.firebase.FirebaseException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Represents an Exception resulting from an operation on a {@link StorageReference}. */
public class StorageException extends FirebaseException {
  private static final String TAG = "StorageException";

  public static final int ERROR_UNKNOWN = -13000;
  public static final int ERROR_OBJECT_NOT_FOUND = -13010;
  public static final int ERROR_BUCKET_NOT_FOUND = -13011;
  public static final int ERROR_PROJECT_NOT_FOUND = -13012;
  public static final int ERROR_QUOTA_EXCEEDED = -13013;
  public static final int ERROR_NOT_AUTHENTICATED = -13020;
  public static final int ERROR_NOT_AUTHORIZED = -13021;
  public static final int ERROR_RETRY_LIMIT_EXCEEDED = -13030;
  public static final int ERROR_INVALID_CHECKSUM = -13031;
  public static final int ERROR_CANCELED = -13040;
  private static final int NETWORK_UNAVAILABLE = -2;

  private final int errorCode;
  private final int httpResultCode;
  private Throwable cause;

  StorageException(@ErrorCode int errorCode, Throwable inner, int httpResultCode) {
    super(getErrorMessageForCode(errorCode));

    this.cause = inner;
    this.errorCode = errorCode;
    this.httpResultCode = httpResultCode;
    Log.e(
        TAG,
        "StorageException has occurred.\n"
            + getErrorMessageForCode(errorCode)
            + "\n Code: "
            + this.errorCode
            + " HttpResult: "
            + this.httpResultCode);
    if (cause != null) {
      Log.e(TAG, cause.getMessage(), cause);
    }
  }

  private static int calculateErrorCode(Status status) {
    if (status.isCanceled()) {
      return ERROR_CANCELED;
    }
    if (status.equals(Status.RESULT_TIMEOUT)) {
      return ERROR_RETRY_LIMIT_EXCEEDED;
    }
    return ERROR_UNKNOWN;
  }

  private static int calculateErrorCode(@Nullable Throwable inner, int httpResultCode) {
    if (inner instanceof CancelException) {
      return ERROR_CANCELED;
    }
    switch (httpResultCode) {
      case NETWORK_UNAVAILABLE:
        return ERROR_RETRY_LIMIT_EXCEEDED;
      case 401:
        return ERROR_NOT_AUTHENTICATED;
      case 403:
        return ERROR_NOT_AUTHORIZED;
      case 404:
        return ERROR_OBJECT_NOT_FOUND;
      case 409:
        return ERROR_INVALID_CHECKSUM;
      default:
        return ERROR_UNKNOWN;
    }
  }

  @NonNull
  public static StorageException fromErrorStatus(@NonNull Status status) {
    Preconditions.checkNotNull(status);
    Preconditions.checkArgument(!status.isSuccess());
    return new StorageException(calculateErrorCode(status), null, 0);
  }

  @Nullable
  public static StorageException fromExceptionAndHttpCode(
      @Nullable Throwable exception, int httpResultCode) {
    if (exception instanceof StorageException) {
      return (StorageException) exception;
    }
    if (isResultSuccess(httpResultCode) && exception == null) {
      return null;
    }
    return new StorageException(
        calculateErrorCode(exception, httpResultCode), exception, httpResultCode);
  }

  @NonNull
  public static StorageException fromException(@NonNull Throwable exception) {
    StorageException se = fromExceptionAndHttpCode(exception, 0);
    assert se != null;
    return se;
  }

  private static boolean isResultSuccess(int resultCode) {
    return (resultCode == 0 || (resultCode >= 200 && resultCode < 300));
  }

  static String getErrorMessageForCode(int errorCode) {
    switch (errorCode) {
      case ERROR_UNKNOWN:
        return "An unknown error occurred, please check the HTTP result code and inner "
            + "exception for server response.";
      case ERROR_OBJECT_NOT_FOUND:
        return "Object does not exist at location.";
      case ERROR_BUCKET_NOT_FOUND:
        return "Bucket does not exist.";
      case ERROR_PROJECT_NOT_FOUND:
        return "Project does not exist.";
      case ERROR_QUOTA_EXCEEDED:
        return "Quota for bucket exceeded, please view quota on www.firebase.google"
            + ".com/storage.";
      case ERROR_NOT_AUTHENTICATED:
        return "User is not authenticated, please authenticate using Firebase "
            + "Authentication and try again.";
      case ERROR_NOT_AUTHORIZED:
        return "User does not have permission to access this object.";
      case ERROR_RETRY_LIMIT_EXCEEDED:
        return "The operation retry limit has been exceeded.";
      case ERROR_INVALID_CHECKSUM:
        return "Object has a checksum which does not match. Please retry the operation.";
      case ERROR_CANCELED:
        return "The operation was cancelled.";
      default:
        return "An unknown error occurred, please check the HTTP result code and inner "
            + "exception for server response.";
    }
  }

  /** Returns the cause of this {@code Throwable}, or {@code null} if there is no cause. */
  @Nullable
  @Override
  public synchronized Throwable getCause() {
    if (cause == this) {
      return null;
    }
    return cause;
  }

  @ErrorCode
  public int getErrorCode() {
    return errorCode;
  }

  /** @return the Http result code (if one exists) from a network operation. */
  @SuppressWarnings("unused")
  public int getHttpResultCode() {
    return httpResultCode;
  }

  /**
   * @return True if this request failed due to a network condition that may be resolved in a future
   *     attempt.
   */
  @SuppressWarnings("unused")
  public boolean getIsRecoverableException() {
    return getErrorCode() == ERROR_RETRY_LIMIT_EXCEEDED;
  }

  /** An {@link ErrorCode} indicates the source of a failed StorageTask or operation. */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    ERROR_UNKNOWN,
    ERROR_OBJECT_NOT_FOUND,
    ERROR_BUCKET_NOT_FOUND,
    ERROR_PROJECT_NOT_FOUND,
    ERROR_QUOTA_EXCEEDED,
    ERROR_NOT_AUTHENTICATED,
    ERROR_NOT_AUTHORIZED,
    ERROR_RETRY_LIMIT_EXCEEDED,
    ERROR_INVALID_CHECKSUM,
    ERROR_CANCELED
  })
  public @interface ErrorCode {}
}
