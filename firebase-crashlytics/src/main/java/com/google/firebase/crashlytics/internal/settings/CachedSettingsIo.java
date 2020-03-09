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

package com.google.firebase.crashlytics.internal.settings;

import android.content.Context;
import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.crashlytics.internal.common.CommonUtils;
import com.google.firebase.crashlytics.internal.persistence.FileStoreImpl;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import org.json.JSONObject;

/**
 * Internal class defining a read/write pair for cached settings that reads/writes from a specific
 * file in the Crashlytics directory on the device.
 */
public class CachedSettingsIo {
  private static final String SETTINGS_CACHE_FILENAME = "com.crashlytics.settings.json";

  private final Context context;

  public CachedSettingsIo(Context context) {
    this.context = context;
  }

  private File getSettingsFile() {
    return new File(new FileStoreImpl(context).getFilesDir(), SETTINGS_CACHE_FILENAME);
  }

  /**
   * @return {@link JSONObject} representing the cached settings data, or <code>null</code> if no
   *     cached data could be found, or an error occurred.
   */
  public JSONObject readCachedSettings() {
    Logger.getLogger().d("Reading cached settings...");

    FileInputStream fis = null;
    JSONObject toReturn = null;

    try {
      final File settingsFile = getSettingsFile();

      if (settingsFile.exists()) {
        fis = new FileInputStream(settingsFile);
        final String settingsStr = CommonUtils.streamToString(fis);

        toReturn = new JSONObject(settingsStr);
      } else {
        Logger.getLogger().d("No cached settings found.");
      }
    } catch (Exception e) {
      Logger.getLogger().e("Failed to fetch cached settings", e);
    } finally {
      CommonUtils.closeOrLog(fis, "Error while closing settings cache file.");
    }

    return toReturn;
  }

  /**
   * Writes the provided {@link JSONObject} to the cache with a new field containing the expiration
   * time. Does nothing if the provided value is <code>null</code>.
   *
   * @param expiresAtMillis The expiration time in milliseconds to write into the JSON data.
   * @param settingsJson JSON data to write to the cache
   */
  public void writeCachedSettings(long expiresAtMillis, JSONObject settingsJson) {
    Logger.getLogger().d("Writing settings to cache file...");

    if (settingsJson != null) {
      FileWriter writer = null;

      try {
        settingsJson.put(SettingsJsonConstants.EXPIRES_AT_KEY, expiresAtMillis);

        writer = new FileWriter(getSettingsFile());
        writer.write(settingsJson.toString());
        writer.flush();
      } catch (Exception e) {
        Logger.getLogger().e("Failed to cache settings", e);
      } finally {
        CommonUtils.closeOrLog(writer, "Failed to close settings writer.");
      }
    }
  }
}
