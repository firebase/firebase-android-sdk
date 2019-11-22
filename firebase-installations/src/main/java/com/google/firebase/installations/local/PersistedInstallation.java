// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.installations.local;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import com.google.firebase.FirebaseApp;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A layer that locally persists a few Firebase Installation attributes on top the Firebase
 * Installation API.
 */
public class PersistedInstallation {

  private final File dataFile;
  @NonNull
  private final FirebaseApp firebaseApp;

  // Registration Status of each persisted fid entry
  // NOTE: never change the ordinal of the enum values because the enum values are stored in shared
  // prefs as their ordinal numbers.
  public enum RegistrationStatus {
    /**
     * {@link PersistedInstallationEntry} default registration status. Next state: UNREGISTERED - A
     * new FID is created and persisted locally before registering with FIS servers.
     */
    NOT_GENERATED,
    /**
     * {@link PersistedInstallationEntry} is not synced with FIS servers. Next state: REGISTERED -
     * If FID registration is successful. REGISTER_ERROR - If FID registration or refresh auth token
     * failed.
     */
    UNREGISTERED,
    /**
     * {@link PersistedInstallationEntry} is synced to FIS servers. Next state: REGISTER_ERROR - If
     * FID registration or refresh auth token failed.
     */
    REGISTERED,
    /**
     * {@link PersistedInstallationEntry} is in error state when an exception is thrown while
     * syncing with FIS server. Next state: UNREGISTERED - A new FID is created and persisted
     * locally before registering with FIS servers.
     */
    REGISTER_ERROR,
  }

  private static final String SETTINGS_FILE_NAME = "PersistedInstallation";

  private static final String FIREBASE_INSTALLATION_ID_KEY = "Fid";
  private static final String AUTH_TOKEN_KEY = "AuthToken";
  private static final String REFRESH_TOKEN_KEY = "RefreshToken";
  private static final String TOKEN_CREATION_TIME_IN_SECONDS_KEY = "TokenCreationEpochInSecs";
  private static final String EXPIRES_IN_SECONDS_KEY = "ExpiresInSecs";
  private static final String PERSISTED_STATUS_KEY = "Status";
  private static final String FIS_ERROR_KEY = "FisError";

  private static final List<String> FID_PREF_KEYS =
      Arrays.asList(
          FIREBASE_INSTALLATION_ID_KEY,
          AUTH_TOKEN_KEY,
          REFRESH_TOKEN_KEY,
          TOKEN_CREATION_TIME_IN_SECONDS_KEY,
          EXPIRES_IN_SECONDS_KEY,
          PERSISTED_STATUS_KEY,
          FIS_ERROR_KEY);

  private final String persistenceKey;

  public PersistedInstallation(@NonNull FirebaseApp firebaseApp) {
    // Different FirebaseApp in the same Android application should have the same application
    // context and same dir path
    persistenceKey = firebaseApp.getPersistenceKey();
    dataFile = new File(firebaseApp.getApplicationContext().getFilesDir(),
        SETTINGS_FILE_NAME + "." + persistenceKey + ".json");
    this.firebaseApp = firebaseApp;
  }

  @NonNull
  public PersistedInstallationEntry readPersistedInstallationEntryValue() {
    JSONObject json = readJSONFromFile();

    String fid = json.optString(getSharedPreferencesKey(FIREBASE_INSTALLATION_ID_KEY), null);
    int status = json.optInt(getSharedPreferencesKey(PERSISTED_STATUS_KEY), -1);
    String authToken = json.optString(getSharedPreferencesKey(AUTH_TOKEN_KEY), null);
    String refreshToken = json.optString(getSharedPreferencesKey(REFRESH_TOKEN_KEY), null);
    long tokenCreationTime =
        json.optLong(getSharedPreferencesKey(TOKEN_CREATION_TIME_IN_SECONDS_KEY), 0);
    long expiresIn = json.optLong(getSharedPreferencesKey(EXPIRES_IN_SECONDS_KEY), 0);
    String fisError = json.optString(getSharedPreferencesKey(FIS_ERROR_KEY), null);

    if (fid == null || !(status >= 0 && status < RegistrationStatus.values().length)) {
      return PersistedInstallationEntry.builder().build();
    }
    return PersistedInstallationEntry.builder()
        .setFirebaseInstallationId(fid)
        .setRegistrationStatus(RegistrationStatus.values()[status])
        .setAuthToken(authToken)
        .setRefreshToken(refreshToken)
        .setTokenCreationEpochInSecs(tokenCreationTime)
        .setExpiresInSecs(expiresIn)
        .setFisError(fisError)
        .build();
  }

  private JSONObject readJSONFromFile() {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final byte[] tmpBuf = new byte[16 * 1024];
    try (FileInputStream fis = new FileInputStream(dataFile)) {
      while (true) {
        int numRead = fis.read(tmpBuf, 0, tmpBuf.length);
        if (numRead < 0) {
          break;
        }
        baos.write(tmpBuf, 0, numRead);
      }
      return new JSONObject(baos.toString());
    } catch (IOException | JSONException e) {
      return new JSONObject();
    }
  }

  private void writeJSONToFile(JSONObject prefs) throws IOException {
    File tmpFile = File.createTempFile(SETTINGS_FILE_NAME, "tmp",
        firebaseApp.getApplicationContext().getFilesDir());

    FileOutputStream fos = new FileOutputStream(tmpFile);
    fos.write(prefs.toString().getBytes());
    fos.close();
    tmpFile.renameTo(dataFile);
  }

  public void writePreferencesToDisk(@NonNull PersistedInstallationEntry entryValue) {
    try {
      JSONObject json = new JSONObject();
      json.put(
          getSharedPreferencesKey(FIREBASE_INSTALLATION_ID_KEY),
          entryValue.getFirebaseInstallationId());
      json.put(
          getSharedPreferencesKey(PERSISTED_STATUS_KEY),
          entryValue.getRegistrationStatus().ordinal());
      json.put(getSharedPreferencesKey(AUTH_TOKEN_KEY), entryValue.getAuthToken());
      json.put(getSharedPreferencesKey(REFRESH_TOKEN_KEY), entryValue.getRefreshToken());
      json.put(
          getSharedPreferencesKey(TOKEN_CREATION_TIME_IN_SECONDS_KEY),
          entryValue.getTokenCreationEpochInSecs());
      json.put(
          getSharedPreferencesKey(EXPIRES_IN_SECONDS_KEY), entryValue.getExpiresInSecs());
      json.put(getSharedPreferencesKey(FIS_ERROR_KEY), entryValue.getFisError());
      writeJSONToFile(json);
    } catch (JSONException | IOException e) {
      // ignore
    }
  }

  /**
   * Sets the state to NOT_GENERATED.
   */
  public void clear() {
    writePreferencesToDisk(
        PersistedInstallationEntry.builder()
            .setRegistrationStatus(RegistrationStatus.NOT_GENERATED)
            .build());
  }

  private String getSharedPreferencesKey(String key) {
    return String.format("%s|%s", persistenceKey, key);
  }
}
