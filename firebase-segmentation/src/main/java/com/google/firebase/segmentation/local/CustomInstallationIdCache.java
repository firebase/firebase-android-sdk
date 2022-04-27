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

package com.google.firebase.segmentation.local;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.FirebaseApp;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A layer that locally caches a few Firebase Segmentation attributes on top the Segmentation
 * backend API.
 *
 * @hide
 */
public class CustomInstallationIdCache {

  // Status of each cache entry
  // NOTE: never change the ordinal of the enum values because the enum values are stored in cache
  // as their ordinal numbers.
  public enum CacheStatus {
    // Cache entry is synced to Firebase backend
    SYNCED,
    // Cache entry is waiting for Firebase backend response or internal network retry (for update
    // operation).
    PENDING_UPDATE,
    // Cache entry is waiting for Firebase backend response or internal network retry (for clear
    // operation).
    PENDING_CLEAR
  }

  private static final String DATA_FILE_NAME_PREFIX = "PersistedCustomInstallationId";

  private static final String CUSTOM_INSTALLATION_ID_KEY = "Cid";
  private static final String INSTANCE_ID_KEY = "Iid";
  private static final String CACHE_STATUS_KEY = "Status";

  private final File dataFile;
  private final FirebaseApp firebaseApp;

  public CustomInstallationIdCache(@NonNull FirebaseApp firebaseApp) {
    this.firebaseApp = firebaseApp;
    // Store custom installation id in different file for different FirebaseApp.
    dataFile =
        new File(
            firebaseApp.getApplicationContext().getFilesDir(),
            String.format("%s.%s.json", DATA_FILE_NAME_PREFIX, firebaseApp.getPersistenceKey()));
  }

  @Nullable
  public synchronized CustomInstallationIdCacheEntryValue readCacheEntryValue() {
    JSONObject cidInfo = readCidInfoFromFile();
    String cid = cidInfo.optString(CUSTOM_INSTALLATION_ID_KEY, null);
    String iid = cidInfo.optString(INSTANCE_ID_KEY, null);
    int status = cidInfo.optInt(CACHE_STATUS_KEY, -1);

    if (cid == null || iid == null || status == -1) {
      return null;
    }
    return CustomInstallationIdCacheEntryValue.create(cid, iid, CacheStatus.values()[status]);
  }

  private JSONObject readCidInfoFromFile() {
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
   * DATA_FILE_NAME.
   */
  @NonNull
  public synchronized boolean insertOrUpdateCacheEntry(
      @NonNull CustomInstallationIdCacheEntryValue entryValue) {
    try {
      // Write the prefs into a JSON object
      JSONObject json = new JSONObject();
      json.put(CUSTOM_INSTALLATION_ID_KEY, entryValue.getCustomInstallationId());
      json.put(INSTANCE_ID_KEY, entryValue.getFirebaseInstanceId());
      json.put(CACHE_STATUS_KEY, entryValue.getCacheStatus().ordinal());
      File tmpFile =
          File.createTempFile(
              String.format("%s.%s", DATA_FILE_NAME_PREFIX, firebaseApp.getPersistenceKey()),
              "tmp",
              firebaseApp.getApplicationContext().getFilesDir());

      // Serialize the JSON object into a string and write the bytes to a temp file
      FileOutputStream fos = new FileOutputStream(tmpFile);
      fos.write(json.toString().getBytes("UTF-8"));
      fos.close();

      // Snapshot the temp file to the actual file
      if (!tmpFile.renameTo(dataFile)) {
        throw new IOException("unable to rename the tmpfile to " + dataFile.getPath());
      }
    } catch (JSONException | IOException e) {
      return false;
    }
    return true;
  }

  @NonNull
  public synchronized boolean clear() {
    if (!dataFile.exists()) {
      return true;
    }
    return dataFile.delete();
  }
}
