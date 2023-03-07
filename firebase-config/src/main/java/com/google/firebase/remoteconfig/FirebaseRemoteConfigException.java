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

package com.google.firebase.remoteconfig;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.FirebaseException;

/** Base class for {@link FirebaseRemoteConfig} exceptions. */
public class FirebaseRemoteConfigException extends FirebaseException {
  /** Code that specifies the type of exception. */
  private final Code code;

  /** Creates a Firebase Remote Config exception with the given message. */
  public FirebaseRemoteConfigException(@NonNull String detailMessage) {
    super(detailMessage);
    this.code = Code.UNKNOWN;
  }

  /** Creates a Firebase Remote Config exception with the given message and cause. */
  public FirebaseRemoteConfigException(@NonNull String detailMessage, @Nullable Throwable cause) {
    super(detailMessage, cause);
    this.code = Code.UNKNOWN;
  }

  /** Creates a Firebase Remote Config exception with the given message and Code. */
  public FirebaseRemoteConfigException(@NonNull String detailMessage, @NonNull Code code) {
    super(detailMessage);
    this.code = code;
  }

  /** Creates a Firebase Remote Config exception with the given message, cause, and Code. */
  public FirebaseRemoteConfigException(
      @NonNull String detailMessage, @Nullable Throwable cause, @NonNull Code code) {
    super(detailMessage, cause);
    this.code = code;
  }

  public enum Code {
    /** Unable to make a connection to the Realtime RC backend. */
    CONFIG_UPDATE_STREAM_ERROR(0),

    /** A config update stream message from the Realtime RC backend is unparsable. */
    CONFIG_UPDATE_MESSAGE_INVALID(1),

    /** Unable to fetch the latest config. */
    CONFIG_UPDATE_NOT_FETCHED(2),

    /** The Realtime RC backend is unavailable. */
    CONFIG_UPDATE_UNAVAILABLE(3),

    /** Unknown code value. */
    UNKNOWN(4);

    private final int value;

    Code(int value) {
      this.value = value;
    }

    public int value() {
      return value;
    }
  }

  @NonNull
  public Code getCode() {
    return code;
  }
}
