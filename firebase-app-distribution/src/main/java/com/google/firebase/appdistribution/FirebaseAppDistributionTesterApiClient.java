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
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.stream.Collectors;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONException;
import org.json.JSONObject;

public class FirebaseAppDistributionTesterApiClient {

  private final String fid;
  private final String appId;
  private final String apiKey;
  private final String authToken;
  private final long versionCode;

  private final String TAG = "FADTesterApiClient";

  private static final String RELEASE_ENDPOINT_URL_FORMAT =
      "https://firebaseapptesters.googleapis.com/v1alpha/devices/-/testerApps/%s/installations/%s/releases";
  private static final String REQUEST_METHOD = "GET";
  private static final String API_KEY_HEADER = "x-goog-api-key";
  private static final String INSTALLATION_AUTH_HEADER = "X-Goog-Firebase-Installations-Auth";
  private static final String BUILD_VERSION_JSON_KEY = "buildVersion";
  private static final String DISPLAY_VERSION_JSON_KEY = "displayVersion";
  private static final String RELEASE_NOTES_JSON_KEY = "releaseNotes";
  private static final String BINARY_TYPE_JSON_KEY = "binaryType";

  FirebaseAppDistributionTesterApiClient(
      @NonNull String fid,
      @NonNull String appId,
      @NonNull String apiKey,
      @NonNull String authToken,
      long versionCode) {
    this.fid = fid;
    this.appId = appId;
    this.apiKey = apiKey;
    this.authToken = authToken;
    this.versionCode = versionCode;
  }

  @RequiresApi(api = Build.VERSION_CODES.N)
  public AppDistributionRelease fetchLatestRelease()
      throws FirebaseAppDistributionException, ProtocolException {

    AppDistributionRelease latestRelease = null;
    HttpsURLConnection conn = openHttpsUrlConnection();
    conn.setRequestMethod(REQUEST_METHOD);
    conn.setRequestProperty(API_KEY_HEADER, this.apiKey);
    conn.setRequestProperty(INSTALLATION_AUTH_HEADER, this.authToken);

    try {
      Log.v(TAG, String.valueOf(conn.getResponseCode()));
      JSONObject latestReleaseJson = readFetchReleaseInputStream(conn.getInputStream());
      Log.v("Json release", latestReleaseJson.toString());
      long latestBuildVersion = Long.parseLong(latestReleaseJson.getString(BUILD_VERSION_JSON_KEY));
      if (this.versionCode < latestBuildVersion) {
        // new release available
        final String displayVersion = latestReleaseJson.getString(DISPLAY_VERSION_JSON_KEY);
        final String buildVersion = latestReleaseJson.getString(BUILD_VERSION_JSON_KEY);
        String releaseNotes;
        try {
          releaseNotes = latestReleaseJson.getString(RELEASE_NOTES_JSON_KEY);
        } catch (JSONException e) {
          releaseNotes = "";
        }
        final BinaryType binaryType =
            latestReleaseJson.getString(BINARY_TYPE_JSON_KEY).equals("APK")
                ? BinaryType.APK
                : BinaryType.AAB;
        latestRelease =
            new AppDistributionRelease(displayVersion, buildVersion, releaseNotes, binaryType);
      }

    } catch (IOException | JSONException e) {
      throw new FirebaseAppDistributionException(FirebaseAppDistributionException.Status.UNKNOWN);
    } finally {
      conn.disconnect();
    }

    return latestRelease;
  }

  @RequiresApi(api = Build.VERSION_CODES.N)
  private JSONObject readFetchReleaseInputStream(InputStream in)
      throws FirebaseAppDistributionException {
    JSONObject latestRelease = null;
    InputStream jsonIn = new BufferedInputStream(in);
    String result =
        new BufferedReader(new InputStreamReader(jsonIn)).lines().collect(Collectors.joining("\n"));
    try {
      JSONObject json = new JSONObject(result);
      Log.v("all releases", json.getJSONArray("releases").toString());
      latestRelease = json.getJSONArray("releases").getJSONObject(0);
    } catch (JSONException e) {
      throw new FirebaseAppDistributionException(FirebaseAppDistributionException.Status.UNKNOWN);
    }

    return latestRelease;
  }

  private HttpsURLConnection openHttpsUrlConnection()
      throws FirebaseAppDistributionException, ProtocolException {
    HttpsURLConnection httpsURLConnection;
    URL url = getReleasesEndpointUrl();
    try {
      httpsURLConnection = (HttpsURLConnection) url.openConnection();
    } catch (IOException ignored) {
      throw new FirebaseAppDistributionException(FirebaseAppDistributionException.Status.UNKNOWN);
    }
    return httpsURLConnection;
  }

  private URL getReleasesEndpointUrl() throws FirebaseAppDistributionException {
    try {
      return new URL(String.format(RELEASE_ENDPOINT_URL_FORMAT, this.appId, this.fid));
    } catch (MalformedURLException e) {
      throw new FirebaseAppDistributionException(FirebaseAppDistributionException.Status.UNKNOWN);
    }
  }
}
