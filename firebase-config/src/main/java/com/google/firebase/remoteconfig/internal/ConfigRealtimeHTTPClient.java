package com.google.firebase.remoteconfig.internal;

import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.TAG;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;
import com.google.android.gms.common.util.AndroidUtilsLight;
import com.google.android.gms.common.util.Hex;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.installations.InstallationTokenResult;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.EventListener;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

public class ConfigRealtimeHTTPClient {
    private static final String API_KEY_HEADER = "X-Goog-Api-Key";
    private static final String X_ANDROID_PACKAGE_HEADER = "X-Android-Package";
    private static final String X_ANDROID_CERT_HEADER = "X-Android-Cert";
    private static final String X_GOOGLE_GFE_CAN_RETRY = "X-Google-GFE-Can-Retry";
    private static final String INSTALLATIONS_AUTH_TOKEN_HEADER =
            "X-Goog-Firebase-Installations-Auth";
    private static final String X_ACCEPT_RESPONSE_STREAMING = "X-Accept-Response-Streaming";

    private static final String REALTIME_URL_STRING = "http://10.0.2.2:8080";
    private static final Logger logger = Logger.getLogger("Real_Time_RC");

    private final ConfigFetchHandler configFetchHandler;
    private final FirebaseApp firebaseApp;
    private final FirebaseInstallationsApi firebaseInstallations;
    private final Executor executor;
    private final Context context;

    // Retry parameters
    private final Timer timer;
    private final int ORIGINAL_RETRIES = 7;
    private final long RETRY_TIME_SECONDS = 10000;
    private long RETRY_MULTIPLIER;
    private int RETRIES_REMAINING;
    private final Random random;

    // Realtime HTTP components
    private URL realtimeURL;
    private HttpURLConnection httpURLConnection;
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

        try {
            this.realtimeURL = new URL(this.REALTIME_URL_STRING);
        } catch (MalformedURLException ex) {
            logger.info("URL is malformed");
        }
        this.retryOnEveryNetworkConnection();
    }

    private void getInstallationAuthToken(HttpURLConnection httpURLConnection) {
        Task<InstallationTokenResult> installationAuthTokenTask =
                firebaseInstallations.getToken(false);
        installationAuthTokenTask.onSuccessTask(unusedToken -> {
            httpURLConnection.setRequestProperty(INSTALLATIONS_AUTH_TOKEN_HEADER, unusedToken.getToken());
            return Tasks.forResult(null);
        });
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

    private void setCommonRequestHeaders(HttpURLConnection httpURLConnection) {
        getInstallationAuthToken(httpURLConnection);
        httpURLConnection.setRequestProperty(API_KEY_HEADER, this.firebaseApp.getOptions().getApiKey());

        // Headers required for Android API Key Restrictions.
        httpURLConnection.setRequestProperty(X_ANDROID_PACKAGE_HEADER, context.getPackageName());
        httpURLConnection.setRequestProperty(X_ANDROID_CERT_HEADER, getFingerprintHashForPackage());

        // Header to denote request is retryable on the server.
        httpURLConnection.setRequestProperty(X_GOOGLE_GFE_CAN_RETRY, "yes");

        // Header to tell server that client expects stream response
        httpURLConnection.setRequestProperty(X_ACCEPT_RESPONSE_STREAMING, "true");

        // Headers to denote that the request body is a JSONObject.
        httpURLConnection.setRequestProperty("Content-Type", "application/json");
        httpURLConnection.setRequestProperty("Accept", "application/json");
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
                    this.setCommonRequestHeaders(this.httpURLConnection);
                    this.httpURLConnection.setRequestProperty("lastKnownVersionNumber", Long.toString(this.configFetchHandler.getTemplateVersionNumber()));
                }
                logger.info("Realtime connection started.");

                RealTimeEventListener retryCallback = new RealTimeEventListener() {
                    @Override
                    public void onEvent() {
                        retryHTTPConnection();
                    }
                };
                new ConfigAsyncAutoFetch(this.httpURLConnection, this.configFetchHandler, this.eventListeners, retryCallback).execute();
                this.retryHTTPConnection();
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
            this.RETRIES_REMAINING--;
            this.RETRY_MULTIPLIER++;
            this.pauseRealtimeConnection();
            logger.info("Retrying in " + (this.RETRY_TIME_SECONDS * this.RETRY_MULTIPLIER) + " seconds");
            this.timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    logger.info("Retrying Realtime connection.");
                    startRealtimeConnection();
                }
            }, (this.RETRY_TIME_SECONDS * this.RETRY_MULTIPLIER));
        } else {
            logger.info("No retries remaining. Restart app.");
        }
    }

    private void retryOnEveryNetworkConnection() {
        Timer retryTimer = new Timer();

        retryTimer.scheduleAtFixedRate(new TimerTask() {
            final ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            final NetworkInfo[] networkInfos = connectivityManager.getAllNetworkInfo();

            @Override
            public void run() {
                for (NetworkInfo networkInfo : networkInfos) {
                    String networkName = networkInfo.getTypeName();
                    if (networkName.equalsIgnoreCase("WIFI") || networkName.equalsIgnoreCase("MOBILE")) {
                        if (networkInfo.isAvailable()) {
                            startRealtimeConnection();
                        }
                    }
                }
                startRealtimeConnection();
            }
        }, 0, 5 * (60*1000));
    }

    // Event Listener interface to be used by developers.
    public interface RealTimeEventListener extends EventListener {
        // Call back for when Real Time.
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
