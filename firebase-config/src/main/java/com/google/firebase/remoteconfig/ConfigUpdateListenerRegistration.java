package com.google.firebase.remoteconfig;

import com.google.firebase.remoteconfig.internal.ConfigRealtimeHttpClient;

/**
 * Represents a listener that can be removed by calling remove. This is returned when calling
 * addOnConfigUpdateListener and should be used when you no longer want to listen for new config
 * updates. If this is the last listener it will close the Realtime stream.
 */
public class ConfigUpdateListenerRegistration {
  private final ConfigRealtimeHttpClient client;
  private final int listenerKey;

  public ConfigUpdateListenerRegistration(ConfigRealtimeHttpClient client, int listenerKey) {
    this.client = client;
    this.listenerKey = listenerKey;
  }

  /**
   * Removes the listener being tracked by this 'ConfigUpdateListenerRegistration`. After the
   * initial call, subsequent calls have no effect.
   */
  public void remove() {
    client.removeRealtimeConfigUpdateListener(listenerKey);
  }
}
