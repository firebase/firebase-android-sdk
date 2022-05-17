package com.google.firebase.remoteconfig.internal;

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

  public static class ConfigUpdateListenerRegistration {
    private final ConfigRealtimeHttpClient client;
    private final int listenerKey;

    public ConfigUpdateListenerRegistration(ConfigRealtimeHttpClient client, int listenerKey) {
      this.client = client;
      this.listenerKey = listenerKey;
    }

    public void remove() {
      client.removeRealtimeConfigUpdateListener(listenerKey);
    }
  }

  // Event Listener interface to be used by developers.
  public interface ConfigUpdateListener {
    // Call back for when Realtime fetches.
    void onEvent();

    void onError(Exception error);
  }
}
