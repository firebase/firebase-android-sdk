// Copyright 2022 Google LLC
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

import androidx.annotation.GuardedBy;
import com.google.firebase.remoteconfig.ConfigUpdateListener;
import com.google.firebase.remoteconfig.ConfigUpdateListenerRegistration;
import java.util.LinkedHashSet;
import java.util.Set;

public class ConfigRealtimeHttpClient {

  @GuardedBy("this")
  private final Set<ConfigUpdateListener> listeners;

  public ConfigRealtimeHttpClient() {
    listeners = new LinkedHashSet<ConfigUpdateListener>();
  }

  // Kicks off Http stream listening and autofetch
  private void beginRealtime() {}

  // Pauses Http stream listening
  private void pauseRealtime() {}

  public synchronized ConfigUpdateListenerRegistration addRealtimeConfigUpdateListener(
      ConfigUpdateListener configUpdateListener) {
    listeners.add(configUpdateListener);
    beginRealtime();
    return new ConfigUpdateListenerRegistrationInternal(configUpdateListener);
  }

  private synchronized void removeRealtimeConfigUpdateListener(ConfigUpdateListener listener) {
    listeners.remove(listener);
    if (listeners.isEmpty()) {
      pauseRealtime();
    }
  }

  public class ConfigUpdateListenerRegistrationInternal
      implements ConfigUpdateListenerRegistration {
    private final ConfigUpdateListener listener;

    public ConfigUpdateListenerRegistrationInternal(ConfigUpdateListener listener) {
      this.listener = listener;
    }

    public void remove() {
      removeRealtimeConfigUpdateListener(listener);
    }
  }
}
