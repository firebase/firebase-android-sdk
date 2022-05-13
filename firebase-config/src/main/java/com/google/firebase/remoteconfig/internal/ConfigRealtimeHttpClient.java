package com.google.firebase.remoteconfig.internal;

import java.util.HashMap;
import java.util.Map;

public class ConfigRealtimeHttpClient {
    
    private final Map<Integer, ConfigUpdateListener> listeners;
    private int listenerCount;
    
    public ConfigRealtimeHttpClient() {
        listeners = new HashMap<>();
        listenerCount = 0;
    }

    // Kicks off Http stream listening and autofetch
    private void beginRealtime() {
    }

    // Pauses Http stream listening
    private void pauseRealtime() {
    }
    
    public ConfigUpdateListenerRegistration addRealtimeConfigUpdateListener(ConfigUpdateListener configUpdateListener) {
        listeners.put(listenerCount, configUpdateListener);
        beginRealtime();
        return new ConfigUpdateListenerRegistration(this, listenerCount++);
    }
    
    public void removeRealtimeConfigUpdateListener(int listenerKey) {
        listeners.remove(listenerKey);
        if (listeners.isEmpty()) {
            pauseRealtime();
        }
    }

    public static class ConfigUpdateListenerRegistration {
        private final ConfigRealtimeHttpClient client;
        private final int listenerKey;

        public ConfigUpdateListenerRegistration (
                ConfigRealtimeHttpClient client, int listenerKey) {
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
