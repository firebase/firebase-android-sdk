package com.google.firebase.remoteconfig.internal;

import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.TAG;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.common.util.AndroidUtilsLight;
import com.google.android.gms.common.util.Hex;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.installations.InstallationTokenResult;
import java.util.Collections;
import java.util.EventListener;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;

public class ConfigRealtimeHTTPClient {
    // Headers
    private static final String API_KEY_HEADER = "X-Goog-Api-Key";
    private static final String X_ANDROID_PACKAGE_HEADER = "X-Android-Package";
    private static final String X_ANDROID_CERT_HEADER = "X-Android-Cert";
    private static final String X_GOOGLE_GFE_CAN_RETRY = "X-Google-GFE-Can-Retry";
    private static final String INSTALLATIONS_AUTH_TOKEN_HEADER =
            "X-Goog-Firebase-Installations-Auth";
    
    private static final String REALTIME_URL_STRING = "http://10.0.2.2:5000/sse";
    private static final Logger logger = Logger.getLogger("Real_Time_RC");

    private final ConfigFetchHandler configFetchHandler;
    private final FirebaseApp firebaseApp;
    private final FirebaseInstallationsApi firebaseInstallations;
    private final Executor executor;
    private final Context context;

    // Retry parameters
    private final Timer timer;
    private final int ORIGINAL_RETRIES = 7;
    private final long RETRY_TIME = 10000;
    private long RETRY_MULTIPLIER;
    private int RETRIES_REMAINING;
    private final Random random;

    // HTTP/2 client and SSE EventSource
    private final OkHttpClient okHttpClient;
    private EventSource eventSource;

    // Map of callbacks
    private final Map<String, RealTimeEventListener> eventListeners;

    public ConfigRealtimeHTTPClient(FirebaseApp firebaseApp,
                                    FirebaseInstallationsApi firebaseInstallations,
                                    ConfigFetchHandler configFetchHandler,
                                    Context context,
                                    Executor executor) {
        this.firebaseApp = firebaseApp;
        this.configFetchHandler = configFetchHandler;
        this.firebaseInstallations = firebaseInstallations;
        this.context = context;
        this.executor = executor;
        this.eventListeners = new HashMap<>();

        // Retry parameters
        this.random = new Random();
        this.timer = new Timer();

        ConnectionPool connectionPool = new ConnectionPool();
        this.okHttpClient = new OkHttpClient.Builder()
                .readTimeout(2, TimeUnit.HOURS)
                .writeTimeout(2, TimeUnit.HOURS)
                .connectTimeout(2, TimeUnit.HOURS)
                .followRedirects(false)
                .protocols(Collections.singletonList(Protocol.H2_PRIOR_KNOWLEDGE))
                .retryOnConnectionFailure(true)
                .connectionPool(connectionPool)
                .build();
    }

    // Open HTTP connection and listen for messages asyncly
    public void startRealtimeConnection() {
        logger.info("Realtime connecting...");
        this.RETRY_MULTIPLIER = this.random.nextInt(10) + 1;
        this.RETRIES_REMAINING = this.ORIGINAL_RETRIES;

        if (!this.eventListeners.isEmpty()) {
            if (this.eventSource == null) {
                Task<InstallationTokenResult> installationAuthTokenTask =
                        firebaseInstallations.getToken(false);
                installationAuthTokenTask.onSuccessTask(executor, (token) ->
                        {
                            EventSource.Factory eventSourceFactory = EventSources.createFactory(this.okHttpClient);
                            EventSourceListener eventSourceListener = createEventSourceListener();

                            Request.Builder request = new Request.Builder()
                                    .url(this.REALTIME_URL_STRING)
                                    .addHeader(INSTALLATIONS_AUTH_TOKEN_HEADER, token.getToken())
                                    .get();
                            setCommonRequestHeaders(request);
                            this.eventSource = eventSourceFactory.newEventSource(request.build(), eventSourceListener);

                            return Tasks.forResult(null);
                        }
                );

            }
            logger.info("Realtime started");
        } else {
            logger.info("Add a listener before starting Realtime!");
        }
    }

    private EventSourceListener createEventSourceListener() {
        return new EventSourceListener() {
            @Override
            public void onClosed(@NonNull EventSource eventSource) {
                logger.info("Connection closing");
                pauseRealtimeConnection();
                retryHTTPConnection();
            }

            @Override
            public void onEvent(@NonNull EventSource eventSource, @Nullable String id, @Nullable String type, @NonNull String data) {
                logger.info("Received invalidation notification.");
                Task<ConfigFetchHandler.FetchResponse> fetchTask = configFetchHandler.fetch(0L);
                fetchTask.onSuccessTask((unusedFetchResponse) ->
                        {
                            logger.info("Finished Fetching new updates.");
                            // Execute callbacks for listeners.
                            for (ConfigRealtimeHTTPClient.RealTimeEventListener listener : eventListeners.values()) {
                                listener.onEvent();
                            }
                            return Tasks.forResult(null);
                        }
                );
            }

            @Override
            public void onFailure(@NonNull EventSource eventSource, @Nullable Throwable t, @Nullable Response response) {
                logger.info("Connection failed with this error: " + t.toString());
                pauseRealtimeConnection();
                retryHTTPConnection();
            }

            @Override
            public void onOpen(@NonNull EventSource eventSource, @NonNull Response response) {
                super.onOpen(eventSource, response);
            }
        };
    }

    private void setCommonRequestHeaders(Request.Builder request) {
        request.addHeader(API_KEY_HEADER, this.firebaseApp.getOptions().getApiKey());

        // Headers required for Android API Key Restrictions.
        request.addHeader(X_ANDROID_PACKAGE_HEADER, context.getPackageName());
        request.addHeader(X_ANDROID_CERT_HEADER, getFingerprintHashForPackage());

        // Header to denote request is retryable on the server.
        request.addHeader(X_GOOGLE_GFE_CAN_RETRY, "yes");

        // Headers to denote that the request body is an SSE request
        request.addHeader("Content-Type", "text/eventstream");
    }

    /** Gets the Android package's SHA-1 fingerprint. */
    private String getFingerprintHashForPackage() {
        byte[] hash;

        try {
            hash = AndroidUtilsLight.getPackageCertificateHashBytes(this.context, this.context.getPackageName());

            if (hash == null) {
                Log.e(TAG, "Could not get fingerprint hash for package: " + this.context.getPackageName());
                return null;
            } else {
                return Hex.bytesToStringUppercase(hash, /* zeroTerminated= */ false);
            }
        } catch (PackageManager.NameNotFoundException e) {
            logger.info("No such package: " + this.context.getPackageName());
            return null;
        }
    }

    // Close HTTP connection.
    public void pauseRealtimeConnection() {
        if (this.eventSource != null) {
            this.eventSource.cancel();
            this.eventSource = null;
        }
        logger.info("Realtime connection stopped.");
    }

    // Try to reopen HTTP connection after a random amount of time
    private void retryHTTPConnection() {
        if (this.RETRIES_REMAINING > 0) {
            RETRIES_REMAINING--;
            this.pauseRealtimeConnection();
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
