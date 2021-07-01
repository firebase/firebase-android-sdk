package com.google.firebase.appdistribution;

import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.stream.Collectors;

import javax.net.ssl.HttpsURLConnection;

public class FirebaseAppDistributionTesterApiClient {

    private final String fid;
    private final String appId;
    private final String apiKey;
    private final String authToken;

    private final String TAG = "FADTesterApiClient";

    private static final String RELEASE_ENDPOINT_URL_FORMAT = "https://firebaseapptesters.googleapis.com/v1alpha/devices/-/testerApps/%s/installations/%s/releases";
    private static final String REQUEST_METHOD = "GET";
    private static final String API_KEY_HEADER = "x-goog-api-key";
    private static final String INSTALLATION_AUTH_HEADER = "X-Goog-Firebase-Installations-Auth";
    //endpoint template @"https://firebaseapptesters.googleapis.com/v1alpha/devices/"
    //    @"-/testerApps/%@/installations/%@/releases";



    FirebaseAppDistributionTesterApiClient(@NonNull String fid, @NonNull String appId,
                                           @NonNull String apiKey, @NonNull String authToken) {
        this.fid = fid;
        this.appId = appId;
        this.apiKey = apiKey;
        this.authToken = authToken;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public AppDistributionRelease fetchLatestRelease() throws FirebaseAppDistributionException, ProtocolException {

        HttpsURLConnection conn = openHttpsUrlConnection();
        try {
            InputStream in = new BufferedInputStream(conn.getInputStream());
            int code = conn.getResponseCode();
            Log.v(TAG, String.valueOf(code));
            String result = new BufferedReader(new InputStreamReader(in))
                    .lines().collect(Collectors.joining("\n"));
            Log.v(TAG, result);
        } catch (IOException e) {
            e.printStackTrace();
            Log.v(TAG, "ioexception");
        } finally {
            conn.disconnect();
        }
        return null;
    }


    private HttpsURLConnection openHttpsUrlConnection() throws FirebaseAppDistributionException, ProtocolException {
        HttpsURLConnection httpsURLConnection;
        URL url = getReleasesEndpointUrl();
        try {
            httpsURLConnection = (HttpsURLConnection) url.openConnection();
        } catch (IOException ignored) {
            throw new FirebaseAppDistributionException(FirebaseAppDistributionException.Status.UNKNOWN);
        }
        httpsURLConnection.setRequestMethod(REQUEST_METHOD);
        httpsURLConnection.setRequestProperty(API_KEY_HEADER, this.apiKey);
        httpsURLConnection.setRequestProperty(INSTALLATION_AUTH_HEADER, this.authToken);



        return httpsURLConnection;
    }

    private URL getReleasesEndpointUrl() throws FirebaseAppDistributionException {
        try {
            return new URL( String.format(RELEASE_ENDPOINT_URL_FORMAT, this.appId, this.fid));
        } catch (MalformedURLException e) {
            throw new FirebaseAppDistributionException(FirebaseAppDistributionException.Status.UNKNOWN);
        }
    }


}
