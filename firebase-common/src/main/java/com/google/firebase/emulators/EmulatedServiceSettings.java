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

package com.google.firebase.emulators;

import androidx.annotation.NonNull;

import com.google.android.gms.common.annotation.KeepForSdk;

/**
 * Settings to connect a single Firebase service to a local emulator.
 *
 * <p>TODO(samstern): Un-hide this once Firestore, Database, and Functions are implemented
 *
 * @see EmulatorSettings
 * @hide
 */
@KeepForSdk
public class EmulatedServiceSettings {

  public static final class Builder {

    private final String host;
    private final int port;

    /**
     * Create a new EmulatedServiceSettings builder.
     *
     * @param host the host where the local emulator is running. If you want to access 'localhost'
     *     from an Android Emulator use '10.0.2.2' instead.
     * @param port the port where the local emulator is running.
     */
    public Builder(@NonNull String host, int port) {
      this.host = host;
      this.port = port;
    }

    @NonNull
    public EmulatedServiceSettings build() {
      return new EmulatedServiceSettings(this.host, this.port);
    }
  }

  public final String host;
  public final int port;

  private EmulatedServiceSettings(@NonNull String host, int port) {
    this.host = host;
    this.port = port;
  }
}
