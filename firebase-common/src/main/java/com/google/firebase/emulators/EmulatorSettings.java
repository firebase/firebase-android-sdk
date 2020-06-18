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
import androidx.annotation.Nullable;
import com.google.firebase.components.Preconditions;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Settings that control which Firebase services should access a local emulator, rather than
 * production.
 *
 * <p>TODO(samstern): Un-hide this once Firestore, Database, and Functions are implemented
 *
 * @see com.google.firebase.FirebaseApp#enableEmulators(EmulatorSettings)
 * @hide
 */
public class EmulatorSettings {

  /** Empty emulator settings to be used as an internal default * */
  public static final EmulatorSettings DEFAULT = new EmulatorSettings.Builder().build();

  public static final class Builder {

    private final Map<FirebaseEmulator, EmulatedServiceSettings> settingsMap = new HashMap<>();

    /** Constructs an empty builder. */
    public Builder() {}

    /**
     * Specify the emulator settings for a single service.
     *
     * @param emulator the emulated service.
     * @param settings the emulator settings.
     * @return the builder, for chaining.
     */
    @NonNull
    public Builder addEmulatedService(
        @NonNull FirebaseEmulator emulator, @NonNull EmulatedServiceSettings settings) {
      Preconditions.checkState(
          !settingsMap.containsKey(emulator),
          "Cannot call addEmulatedService twice for " + emulator.toString());
      this.settingsMap.put(emulator, settings);
      return this;
    }

    @NonNull
    public EmulatorSettings build() {
      return new EmulatorSettings(settingsMap);
    }
  }

  private final AtomicBoolean accessed = new AtomicBoolean(false);
  private final Map<FirebaseEmulator, EmulatedServiceSettings> settingsMap;

  private EmulatorSettings(@NonNull Map<FirebaseEmulator, EmulatedServiceSettings> settingsMap) {
    this.settingsMap = Collections.unmodifiableMap(settingsMap);
  }

  /**
   * Determine if any Firebase SDK has already accessed the emulator settings. When true, attempting
   * to change the settings should throw an error.
   *
   * @hide
   */
  public boolean isAccessed() {
    return accessed.get();
  }

  /**
   * Fetch the emulation settings for a single Firebase service. Once this method has been called
   * {@link #isAccessed()} will return true.
   *
   * @hide
   */
  @Nullable
  public EmulatedServiceSettings getServiceSettings(@NonNull FirebaseEmulator emulator) {
    accessed.set(true);

    if (settingsMap.containsKey(emulator)) {
      return settingsMap.get(emulator);
    }

    return null;
  }
}
