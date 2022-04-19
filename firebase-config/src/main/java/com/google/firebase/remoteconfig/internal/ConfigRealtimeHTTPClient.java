package com.google.firebase.remoteconfig.internal;

import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.TAG;
import static com.google.firebase.remoteconfig.RemoteConfigConstants.REALTIME_REGEX_URL;

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
import com.google.firebase.remoteconfig.FirebaseRemoteConfigClientException;

import org.json.JSONObject;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConfigRealtimeHTTPClient {
    private static final String API_KEY_HEADER = "X-Goog-Api-Key";
    private static final String X_ANDROID_PACKAGE_HEADER = "X-Android-Package";
    private static final String X_ANDROID_CERT_HEADER = "X-Android-Cert";
    private static final String X_GOOGLE_GFE_CAN_RETRY = "X-Google-GFE-Can-Retry";
    private static final String INSTALLATIONS_AUTH_TOKEN_HEADER =
            "X-Goog-Firebase-Installations-Auth";
    private static final String X_ACCEPT_RESPONSE_STREAMING = "X-Accept-Response-Streaming";
    
    private static final String TEST_REALTIME_URL_STRING = "http://10.0.2.2:8080";
    private static final Logger logger = Logger.getLogger("Real_Time_RC");

    private final ConfigFetchHandler configFetchHandler;
    private final FirebaseApp firebaseApp;
    private final FirebaseInstallationsApi firebaseInstallations;
    private final Context context;
    private final String namespace;

    // Retry parameters
    private final Timer timer;
    private final int ORIGINAL_RETRIES = 7;
    private final long RETRY_TIME_SECONDS;
    private long RETRY_MULTIPLIER;
    private int RETRIES_REMAINING;
    private final Random random;

    // Realtime HTTP components
    private URL realtimeURL;
    private HttpURLConnection httpURLConnection;
    private EventListener eventListener;
    private boolean firstConnection = true;
    private boolean isInBackground = false;

    public ConfigRealtimeHTTPClient(FirebaseApp firebaseApp,
                                    FirebaseInstallationsApi firebaseInstallations,
                                    ConfigFetchHandler configFetchHandler,
                                    Context context,
                                    String namespace) {
        this.firebaseApp = firebaseApp;
        this.configFetchHandler = configFetchHandler;
        this.firebaseInstallations = firebaseInstallations;
        this.context = context;
        this.namespace = namespace;

        // Retry parameters
        this.random = new Random();
        this.timer = new Timer();
        this.RETRY_MULTIPLIER = this.random.nextInt(10) + 1;
        this.RETRY_TIME_SECONDS = this.random.nextInt(10000) + 10000;
        this.RETRIES_REMAINING = this.ORIGINAL_RETRIES;

        try {
            this.realtimeURL = new URL(this.TEST_REALTIME_URL_STRING);
        } catch (MalformedURLException ex) {
            logger.info("URL is malformed");
        }
    }

    private String getRealtimeURL(String namespace) {
        return String.format(REALTIME_REGEX_URL,
                extractProjectNumberFromAppId(firebaseApp.getOptions().getApplicationId()),
                namespace);
    }

    /**
     * A regular expression for the GMP App Id format. The first group (index 1) is the project
     * number.
     */
    private static final Pattern GMP_APP_ID_PATTERN =
            Pattern.compile("^[^:]+:([0-9]+):(android|ios|web):([0-9a-f]+)");

    private static String extractProjectNumberFromAppId(String gmpAppId) {
        Matcher matcher = GMP_APP_ID_PATTERN.matcher(gmpAppId);
        return matcher.matches() ? matcher.group(1) : null;
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
        // Get Installation Token
        getInstallationAuthToken(httpURLConnection);

        // API Key
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

    private JSONObject createRequestBody() {
        Map<String, String> body = new HashMap<>();
//        body.put("project",
//                extractProjectNumberFromAppId(this.firebaseApp.getOptions().getApplicationId()));
         body.put("project", "299394317711");
        body.put("namespace", this.namespace);
        body.put("lastKnownVersionNumber",
                Long.toString(this.configFetchHandler.getTemplateVersionNumber()));
        return new JSONObject(body);
    }

    private void setRequestParams(HttpURLConnection httpURLConnection) throws IOException {
        httpURLConnection.setRequestMethod("POST");
        byte[] body = createRequestBody().toString().getBytes("utf-8");
        OutputStream outputStream = new BufferedOutputStream(httpURLConnection.getOutputStream());
        outputStream.write(body);
        outputStream.flush();
        outputStream.close();
    }

    private boolean canMakeConnection() {
        return !isInBackground && eventListener != null && this.httpURLConnection == null;
    }

    // Try to reopen HTTP connection after a random amount of time
    private void retryHTTPConnection() {
        if (this.canMakeConnection() && this.RETRIES_REMAINING > 0) {
            this.RETRIES_REMAINING--;
            this.RETRY_MULTIPLIER++;
            long backOffTime = this.RETRY_TIME_SECONDS * this.RETRY_MULTIPLIER;
            logger.info(String.format("Retrying in %d seconds", backOffTime));
            this.timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    logger.info("Retrying Realtime connection.");
                    startRealtimeConnection();
                }
            }, backOffTime);
        } else {
            logger.info("No retries remaining. Restart app.");
            if (this.RETRIES_REMAINING == 0) {
                this.eventListener.onError(
                        new FirebaseRemoteConfigClientException("Can't open the connection")
                );
            }
        }
    }

    private void retryOnEveryNetworkConnection(ConfigRealtimeHTTPClient realtimeHTTPClient) {
        Timer retryTimer = new Timer();
        retryTimer.scheduleAtFixedRate(new TimerTask() {
            final ConnectivityManager connectivityManager
                    = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            final ConfigRealtimeHTTPClient realtimeClient = realtimeHTTPClient;

            @Override
            public void run() {
                NetworkInfo[] networkInfos = connectivityManager.getAllNetworkInfo();
                for (NetworkInfo networkInfo : networkInfos) {
                    String networkName = networkInfo.getTypeName();
                    if (networkName.equalsIgnoreCase("WIFI")
                            || networkName.equalsIgnoreCase("MOBILE")) {
                        if (networkInfo.isAvailable()) {
                            this.realtimeClient.startRealtimeConnection();
                        }
                    }
                }
            }
        }, 5000, 5 * (60*1000));
    }
    
    private void startAsyncAutofetch() {
        EventListener retryCallback = new EventListener() {
            @Override
            public void onEvent() {
                pauseRealtimeConnection();
                retryHTTPConnection();
            }

            @Override
            public void onError(Exception error) {
                eventListener.onError(error);
            }
        };
        logger.info("Starting autofetch...");
        new ConfigAsyncAutoFetch(
                this.httpURLConnection, this.configFetchHandler, this.eventListener, retryCallback
        ).execute();
    }
    
    public void setBackgroundFlag(boolean isInBackground) {
        this.isInBackground = isInBackground;
    }

    // Open HTTP connection and listen for messages asyncly
    public void startRealtimeConnection() {
        if (firstConnection) {
            this.retryOnEveryNetworkConnection(this);
            this.firstConnection = false;
        }

        if (canMakeConnection()) {
            logger.info("Realtime connecting...");
            try {
                this.httpURLConnection = (HttpURLConnection) this.realtimeURL.openConnection();
                this.setCommonRequestHeaders(this.httpURLConnection);
                this.setRequestParams(this.httpURLConnection);

                this.RETRIES_REMAINING = this.ORIGINAL_RETRIES;
                this.RETRY_MULTIPLIER = this.random.nextInt(10) + 1;
                startAsyncAutofetch();
            } catch (IOException ex) {
                logger.info(String.format("Can't start http connection due to %s", ex.toString()));
                pauseRealtimeConnection();
                this.retryHTTPConnection();
            }
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

    // Add Event listener.
    public ListenerRegistration setRealtimeEventListener(EventListener eventListener) {
        this.eventListener = eventListener;
        return new ListenerRegistration(this);
    }

    public void removeRealtimeEventListener() {
        this.eventListener = null;
    }

    public static class ListenerRegistration {
        private final ConfigRealtimeHTTPClient client;

        public ListenerRegistration (
                ConfigRealtimeHTTPClient client) {
            this.client = client;
        }

        public void remove() {
            this.client.removeRealtimeEventListener();
        }
    }

    // Event Listener interface to be used by developers.
    public interface EventListener {
        // Call back for when Realtime fetches.
        void onEvent();

        void onError(Exception error);
    }
}
