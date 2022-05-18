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
    return new ConfigUpdateListenerRegistration(this, listenerCount++);
  }

  public void removeRealtimeConfigUpdateListener(int listenerKey) {
    listeners.remove(listenerKey);
    if (listeners.isEmpty()) {
      pauseRealtimeStream();
    }
  }
}
