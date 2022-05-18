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

import com.google.firebase.remoteconfig.ConfigUpdateListener;
import com.google.firebase.remoteconfig.ConfigUpdateListenerRegistration;
import java.util.HashMap;
import java.util.Map;

/**
 * Client for receiving realtime config updates from the Firebase Remote Config server.
 *
 * @author Quan Pham
 */
public class ConfigRealtimeHttpClient {

  private final Map<Integer, ConfigUpdateListener> listeners;
  private int listenerCount;

  public ConfigRealtimeHttpClient() {
    listeners = new HashMap<>();
    listenerCount = 1;
  }

  // Kicks off Http stream listening and autofetch. Will implement in later PRs
  private void beginRealtimeStream() {}

  // Pauses Http stream listening
  private void pauseRealtimeStream() {}

  public ConfigUpdateListenerRegistration addRealtimeConfigUpdateListener(
      ConfigUpdateListener configUpdateListener) {
    listeners.put(listenerCount, configUpdateListener);
    beginRealtimeStream();
    return new ConfigUpdateListenerRegistrationInternal(this, listenerCount++);
  }

  public void removeRealtimeConfigUpdateListener(int listenerKey) {
    listeners.remove(listenerKey);
    if (listeners.isEmpty()) {
      pauseRealtimeStream();
    }
  }

  public static class ConfigUpdateListenerRegistrationInternal
      implements ConfigUpdateListenerRegistration {
    private final ConfigRealtimeHttpClient realtimeHttpClient;
    private final int listenerKey;

    public ConfigUpdateListenerRegistrationInternal(
        ConfigRealtimeHttpClient realtimeHttpClient, int listenerKey) {
      this.realtimeHttpClient = realtimeHttpClient;
      this.listenerKey = listenerKey;
    }

    public void remove() {
      this.realtimeHttpClient.removeRealtimeConfigUpdateListener(this.listenerKey);
    }
  }
}
