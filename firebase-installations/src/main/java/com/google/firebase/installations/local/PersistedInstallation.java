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

import androidx.annotation.NonNull;
import com.google.firebase.FirebaseApp;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A layer that locally persists a few Firebase Installation attributes on top the Firebase
 * Installation API.
 *
 * @hide
 */
public class PersistedInstallation {
  private final File dataFile;
  @NonNull private final FirebaseApp firebaseApp;

  // Registration Status of each persisted fid entry
  // NOTE: never change the ordinal of the enum values because the enum values are written to
  // local storage as their ordinal numbers.
  public enum RegistrationStatus {
    /**
     * {@link PersistedInstallationEntry} legacy registration status. Next state: UNREGISTERED - A
     * new FID is created and persisted locally before registering with FIS servers.
     */
    ATTEMPT_MIGRATION,
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

  private static final String SETTINGS_FILE_NAME_PREFIX = "PersistedInstallation";
  private static final String FIREBASE_INSTALLATION_ID_KEY = "Fid";
  private static final String AUTH_TOKEN_KEY = "AuthToken";
  private static final String REFRESH_TOKEN_KEY = "RefreshToken";
  private static final String TOKEN_CREATION_TIME_IN_SECONDS_KEY = "TokenCreationEpochInSecs";
  private static final String EXPIRES_IN_SECONDS_KEY = "ExpiresInSecs";
  private static final String PERSISTED_STATUS_KEY = "Status";
  private static final String FIS_ERROR_KEY = "FisError";

  public PersistedInstallation(@NonNull FirebaseApp firebaseApp) {
    // Different FirebaseApp in the same Android application should have the same application
    // context and same dir path
    dataFile =
        new File(
            firebaseApp.getApplicationContext().getFilesDir(),
            SETTINGS_FILE_NAME_PREFIX + "." + firebaseApp.getPersistenceKey() + ".json");
    this.firebaseApp = firebaseApp;
  }

  @NonNull
  public PersistedInstallationEntry readPersistedInstallationEntryValue() {
    JSONObject json = readJSONFromFile();

    String fid = json.optString(FIREBASE_INSTALLATION_ID_KEY, null);
    int status = json.optInt(PERSISTED_STATUS_KEY, RegistrationStatus.ATTEMPT_MIGRATION.ordinal());
    String authToken = json.optString(AUTH_TOKEN_KEY, null);
    String refreshToken = json.optString(REFRESH_TOKEN_KEY, null);
    long tokenCreationTime = json.optLong(TOKEN_CREATION_TIME_IN_SECONDS_KEY, 0);
    long expiresIn = json.optLong(EXPIRES_IN_SECONDS_KEY, 0);
    String fisError = json.optString(FIS_ERROR_KEY, null);

    PersistedInstallationEntry prefs =
        PersistedInstallationEntry.builder()
            .setFirebaseInstallationId(fid)
            .setRegistrationStatus(RegistrationStatus.values()[status])
            .setAuthToken(authToken)
            .setRefreshToken(refreshToken)
            .setTokenCreationEpochInSecs(tokenCreationTime)
            .setExpiresInSecs(expiresIn)
            .setFisError(fisError)
            .build();
    return prefs;
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

  /**
   * Write the prefs to a JSON object, serialize them into a JSON string and write the bytes to a
   * temp file. After writing and closing the temp file, rename it over to the actual
   * SETTINGS_FILE_NAME.
   */
  @NonNull
  public PersistedInstallationEntry insertOrUpdatePersistedInstallationEntry(
      @NonNull PersistedInstallationEntry prefs) {
    try {
      // Write the prefs into a JSON object
      JSONObject json = new JSONObject();
      json.put(FIREBASE_INSTALLATION_ID_KEY, prefs.getFirebaseInstallationId());
      json.put(PERSISTED_STATUS_KEY, prefs.getRegistrationStatus().ordinal());
      json.put(AUTH_TOKEN_KEY, prefs.getAuthToken());
      json.put(REFRESH_TOKEN_KEY, prefs.getRefreshToken());
      json.put(TOKEN_CREATION_TIME_IN_SECONDS_KEY, prefs.getTokenCreationEpochInSecs());
      json.put(EXPIRES_IN_SECONDS_KEY, prefs.getExpiresInSecs());
      json.put(FIS_ERROR_KEY, prefs.getFisError());
      File tmpFile =
          File.createTempFile(
              SETTINGS_FILE_NAME_PREFIX, "tmp", firebaseApp.getApplicationContext().getFilesDir());

      // Werialize the JSON object into a string and write the bytes to a temp file
      FileOutputStream fos = new FileOutputStream(tmpFile);
      fos.write(json.toString().getBytes("UTF-8"));
      fos.close();

      // Snapshot the temp file to the actual file
      if (!tmpFile.renameTo(dataFile)) {
        throw new IOException("unable to rename the tmpfile to " + SETTINGS_FILE_NAME_PREFIX);
      }
    } catch (JSONException | IOException e) {
      // This should only happen when the storage is full or the system is corrupted.
      // There isn't a lot we can do when this happens, other than crash the process. It is a
      // bit nicer to eat the error and hope that the user clears some storage space on their
      // device.
    }

    // Return the prefs that were written to make it easy for the caller to use them in a
    // future step (e.g. for chaining calls).
    return prefs;
  }

  /** Sets the state to ATTEMPT_MIGRATION. */
  public void clearForTesting() {
    dataFile.delete();
  }
}
