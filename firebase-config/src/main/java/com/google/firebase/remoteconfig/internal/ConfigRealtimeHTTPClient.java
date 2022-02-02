package com.google.firebase.remoteconfig.internal;

import android.os.AsyncTask;
import android.os.Build;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.EventListener;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
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
        this.httpURLConnection = null;
        this.configFetchHandler = configFetchHandler;
        this.eventListeners = new HashMap<>();

        // Retry parameter initialization
        this.random = new Random();
        this.timer = new Timer();
    }

    public void startRealtimeConnection() {
        logger.info("Realtime connecting...");
        this.RETRY_MULTIPLIER = this.random.nextInt(10) + 1;
        this.RETRIES_REMAINING = this.ORIGINAL_RETRIES;

        try {
            if (this.httpURLConnection == null) {
                this.httpURLConnection = (HttpURLConnection) this.realtimeURL.openConnection();
            }
            logger.info("Realtime connection started.");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                CompletableFuture.supplyAsync(this::listenForNotifications);
            }
        } catch (Exception ex) {
            logger.info("Can't start http connection");
            this.retryHTTPConnection();
        }
    }

    public void pauseRealtimeConnection() {
        if (this.httpURLConnection != null) {
            this.httpURLConnection.disconnect();
            this.httpURLConnection = null;
            logger.info("Realtime connection stopped.");
        }
    }

    private void retryHTTPConnection() {
        if (this.RETRIES_REMAINING > 0) {
            RETRIES_REMAINING--;
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

    private Object listenForNotifications() {
        if (this.httpURLConnection != null) {
            try {
                int responseCode = httpURLConnection.getResponseCode();
                if (responseCode == 200) {
                    InputStream inputStream = httpURLConnection.getInputStream();
                    handleNotifications(inputStream);
                } else {
                    logger.info("Can't open Realtime stream");
                    this.pauseRealtimeConnection();
                    retryHTTPConnection();
                }

            } catch (IOException ex) {
                logger.info("Error handling messages.");
            }
        }
        logger.info("No more messages to receive.");
        return new Object();
    }

    private void handleNotifications(InputStream inputStream) throws IOException {
        BufferedReader reader = new BufferedReader((new InputStreamReader(inputStream)));
        while (reader.readLine() != null) {
            Task<ConfigFetchHandler.FetchResponse> fetchTask = this.configFetchHandler.fetch(0L);
            fetchTask.onSuccessTask((unusedFetchResponse) ->
                    {
                        logger.info("Finished Fetching new updates.");
                        // Execute callbacks for listeners.
                        for (RealTimeEventListener listener : eventListeners.values()) {
                            listener.onEvent();
                        }
                        return Tasks.forResult(null);
                    }
            );
        }
        reader.close();
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
