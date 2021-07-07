package com.google.firebase.appdistribution;

import android.util.Log;
import androidx.annotation.NonNull;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONException;
import org.json.JSONObject;

public class FirebaseAppDistributionTesterApiClient {

  private final String fid;
  private final String appId;
  private final String apiKey;

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
  public static final int DEFAULT_BUFFER_SIZE = 8192;

  FirebaseAppDistributionTesterApiClient(
      @NonNull String fid, @NonNull String appId, @NonNull String apiKey) {
    this.fid = fid;
    this.appId = appId;
    this.apiKey = apiKey;
  }

  public AppDistributionRelease fetchLatestRelease(@NonNull String authToken)
      throws FirebaseAppDistributionException, ProtocolException {

    AppDistributionRelease latestRelease = null;
    HttpsURLConnection conn = openHttpsUrlConnection();
    conn.setRequestMethod(REQUEST_METHOD);
    conn.setRequestProperty(API_KEY_HEADER, this.apiKey);
    conn.setRequestProperty(INSTALLATION_AUTH_HEADER, authToken);

    try {
      Log.v(TAG, String.valueOf(conn.getResponseCode()));
      JSONObject latestReleaseJson = readFetchReleaseInputStream(conn.getInputStream());
      Log.v("Json release", latestReleaseJson.toString());

      long latestBuildVersion = Long.parseLong(latestReleaseJson.getString(BUILD_VERSION_JSON_KEY));
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

    } catch (IOException | JSONException e) {
      throw new FirebaseAppDistributionException(FirebaseAppDistributionException.Status.UNKNOWN);
    } finally {
      conn.disconnect();
    }

    return latestRelease;
  }

  private JSONObject readFetchReleaseInputStream(InputStream in)
      throws FirebaseAppDistributionException, IOException {
    JSONObject latestRelease = null;
    InputStream jsonIn = new BufferedInputStream(in);
    String result = convertInputStreamToString(jsonIn);
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

  private static String convertInputStreamToString(InputStream is) throws IOException {
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
    int length;
    while ((length = is.read(buffer)) != -1) {
      result.write(buffer, 0, length);
    }
    return result.toString();
  }
}
