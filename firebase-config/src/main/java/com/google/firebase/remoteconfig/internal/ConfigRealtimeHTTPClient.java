package com.google.firebase.remoteconfig.internal;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.EventListener;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;

public class ConfigRealtimeHTTPClient {
    private static final String REALTIME_URL_STRING = "http://10.0.2.2:8080";
    private final Map<String, RealTimeEventListener> eventListeners;
    private URL realtimeURL;
    private HttpURLConnection httpURLConnection;

    // Retry parameters
    private final Timer timer;
    private final int ORIGINAL_RETRIES = 7;
    private final long RETRY_TIME = 10000;
    private long RETRY_MULTIPLIER;
    private int RETRIES_REMAINING;
    private final Random random;

    private static final Logger logger = Logger.getLogger("Real_Time_RC");
    private final ConfigFetchHandler configFetchHandler;

    public ConfigRealtimeHTTPClient(ConfigFetchHandler configFetchHandler) {
        try {
            this.realtimeURL = new URL(this.REALTIME_URL_STRING);
        } catch (MalformedURLException ex) {
            logger.info("This is not a valid URL");
        }
        this.configFetchHandler = configFetchHandler;
        this.eventListeners = new HashMap<>();

        // Retry parameters
        this.random = new Random();
        this.timer = new Timer();
    }

    // Open HTTP connection and listen for messages asyncly
    public void startRealtimeConnection() {
        logger.info("Realtime connecting...");
        this.RETRY_MULTIPLIER = this.random.nextInt(10) + 1;
        this.RETRIES_REMAINING = this.ORIGINAL_RETRIES;

        if (!this.eventListeners.isEmpty()) {
            try {
                if (this.httpURLConnection == null) {
                    this.httpURLConnection = (HttpURLConnection) this.realtimeURL.openConnection();
                }
                logger.info("Realtime connection started.");

                RealTimeEventListener retryCallback = new RealTimeEventListener() {
                    @Override
                    public void onEvent() {
                        retryHTTPConnection();
                    }
                };
                new ConfigAsyncAutoFetch(this.httpURLConnection, this.configFetchHandler, this.eventListeners, retryCallback).execute();
            } catch (Exception ex) {
                logger.info("Can't start http connection");
                this.retryHTTPConnection();
            }
        } else {
            logger.info("Add a listener before starting Realtime!");
        }
    }

    // Close HTTP connection.
    public void pauseRealtimeConnection() {
        if (this.httpURLConnection != null) {
            this.httpURLConnection.disconnect();
            this.httpURLConnection = null;
            logger.info("Realtime connection stopped.");
        }
    }

    // Try to reopen HTTP connection after a random amount of time
    private void retryHTTPConnection() {
        if (this.RETRIES_REMAINING > 0) {
            RETRIES_REMAINING--;
            this.httpURLConnection = null;
            logger.info("Retrying in " + (this.RETRY_TIME * this.RETRY_MULTIPLIER) + " seconds");
            this.timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    logger.info("Retrying Realtime connection.");
                    startRealtimeConnection();
                }
            }, (this.RETRY_TIME * this.RETRY_MULTIPLIER));
        } else {
            logger.info("No retries remaining. Restart app.");
        }
    }

    // Event Listener interface to be used by developers.
    public interface RealTimeEventListener extends EventListener {
        // Call back for when Real Time signal occurs.
        void onEvent();
    }

    // Add Event listener.
    public void putRealTimeEventListener(String listenerName, RealTimeEventListener realTimeEventListener) {
        this.eventListeners.put(listenerName, realTimeEventListener);
    }

    // Remove Event listener.
    public void removeRealTimeEventListener(String listenerName) {
        this.eventListeners.remove(listenerName);
    }
}
