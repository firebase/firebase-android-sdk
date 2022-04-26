
package com.google.firebase.remoteconfig.internal;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

// Class extended from AsyncTask that will allow for async monitoring of Realtime RC HTTP/1.1 chunked stream.
public class ConfigAsyncAutoFetch {
    private static final Logger logger = Logger.getLogger("Real_Time_RC");
    private static final int FETCH_RETRY = 3;

    private final HttpURLConnection httpURLConnection;
    private final ConfigFetchHandler configFetchHandler;
    private final ConfigRealtimeHTTPClient.EventListener eventListener;
    private final ConfigRealtimeHTTPClient.EventListener retryCallback;
    private final ScheduledExecutorService scheduledExecutorService;
    private final Random random;
    private final Executor executor;

    public ConfigAsyncAutoFetch(HttpURLConnection httpURLConnection,
                                ConfigFetchHandler configFetchHandler,
                                ConfigRealtimeHTTPClient.EventListener eventListener,
                                ConfigRealtimeHTTPClient.EventListener retryCallback,
                                Executor executor,
                                ScheduledExecutorService scheduledExecutorService) {
        this.httpURLConnection = httpURLConnection;
        this.configFetchHandler = configFetchHandler;
        this.eventListener = eventListener;
        this.retryCallback = retryCallback;
        this.scheduledExecutorService = scheduledExecutorService;
        this.random = new Random();
        this.executor = executor;
    }

    public void beginAutoFetch() {
        this.executor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        listenForNotifications();
                    }
                }
        );
    }

    // Check connection and establish InputStream
    private void listenForNotifications() {
        if (this.httpURLConnection != null) {
            try {
                logger.info(httpURLConnection.toString());
                int responseCode = httpURLConnection.getResponseCode();
                logger.info(responseCode + "");
                if (responseCode == 200) {
                    InputStream inputStream = httpURLConnection.getInputStream();
                    handleNotifications(inputStream);
                    inputStream.close();
                } else {
                    logger.info("Can't open Realtime stream");
                }
            } catch (IOException ex) {
                logger.info("Error handling messages with exception: " + ex.toString());
            }
        }
        this.retryCallback.onEvent();
    }

    // Auto-fetch new config and execute callbacks on each new message
    private void handleNotifications(InputStream inputStream) throws IOException {
        BufferedReader reader = new BufferedReader((new InputStreamReader(inputStream)));
        String message;
        while ((message = reader.readLine()) != null) {
            logger.info(message);
            long currentTemplateVersion = this.configFetchHandler.getTemplateVersionNumber();
            logger.info("Template version is " + currentTemplateVersion);
            this.autoFetch(FETCH_RETRY, currentTemplateVersion);
        }
        reader.close();
    }

    private void autoFetch(int remainingAttempts, long currentVersion) {
        if (remainingAttempts == 0) {
            if (this.eventListener != null) {
                this.eventListener.onError(new FirebaseRemoteConfigException(
                        "Unable to fetch latest version."));
            }
            return;
        }

        if (remainingAttempts == FETCH_RETRY) {
            fetchLatestConfig(remainingAttempts, currentVersion);
        } else {
            int timeTillFetch = this.random.nextInt(11000) + 2000;
            this.scheduledExecutorService.schedule(new Runnable() {
                @Override
                public void run() {
                    fetchLatestConfig(remainingAttempts, currentVersion);
                }
            }, timeTillFetch, TimeUnit.MILLISECONDS);
        }
    }

    private void fetchLatestConfig(int remainingAttempts, long currentVersion) {
        Task<ConfigFetchHandler.FetchResponse> fetchTask = configFetchHandler.fetch(0L);
        fetchTask.onSuccessTask((fetchResponse) ->
        {
            logger.info("Fetch done...");
            long newTemplateVersion = 0;
            if (fetchResponse.getFetchedConfigs() != null) {
                newTemplateVersion = fetchResponse.getFetchedConfigs().getTemplateVersionNumber();
            }

            if (newTemplateVersion > currentVersion) {
                if (this.eventListener != null) {
                    // Execute callbacks for listener.
                    this.eventListener.onEvent();
                }
            } else {
                logger.info("Fetched template version is the same as SDK's current version." +
                        " Retrying fetch.");
                // Continue fetching until template version number if greater then current.
                autoFetch(remainingAttempts - 1, currentVersion);
            }
            return Tasks.forResult(null);
        });
    }
}
