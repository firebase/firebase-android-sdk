// Copyright 2020 Google LLC
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

package com.google.firebase.crashlytics.internal.common;

import androidx.annotation.NonNull;
import com.google.firebase.crashlytics.internal.Logger;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Handles persistence of metadata for cases when we need to reload it on app restart for submission
 * with a previously unclosed crash session.
 */
class MetaDataStore {

  private static final Charset UTF_8 = Charset.forName("UTF-8");

  private static final String USERDATA_SUFFIX = "user";
  private static final String KEYDATA_SUFFIX = "keys";
  private static final String METADATA_EXT = ".meta";

  private static final String KEY_USER_ID = "userId";

  private final File filesDir;

  public MetaDataStore(File filesDir) {
    this.filesDir = filesDir;
  }

  public void writeUserData(String sessionId, UserMetadata data) {
    final File f = getUserDataFileForSession(sessionId);
    Writer writer = null;
    try {
      final String userDataString = userDataToJson(data);
      writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), UTF_8));
      writer.write(userDataString);
      writer.flush();
    } catch (Exception e) {
      Logger.getLogger().e("Error serializing user metadata.", e);
    } finally {
      CommonUtils.closeOrLog(writer, "Failed to close user metadata file.");
    }
  }

  public UserMetadata readUserData(String sessionId) {
    final File f = getUserDataFileForSession(sessionId);
    if (!f.exists()) {
      return new UserMetadata();
    }

    InputStream is = null;
    try {
      is = new FileInputStream(f);
      return jsonToUserData(CommonUtils.streamToString(is));
    } catch (Exception e) {
      Logger.getLogger().e("Error deserializing user metadata.", e);
    } finally {
      CommonUtils.closeOrLog(is, "Failed to close user metadata file.");
    }
    return new UserMetadata();
  }

  public void writeKeyData(String sessionId, Map<String, String> keyData) {
    final File f = getKeysFileForSession(sessionId);
    Writer writer = null;
    try {
      final String keyDataString = keysDataToJson(keyData);
      writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), UTF_8));
      writer.write(keyDataString);
      writer.flush();
    } catch (Exception e) {
      Logger.getLogger().e("Error serializing key/value metadata.", e);
    } finally {
      CommonUtils.closeOrLog(writer, "Failed to close key/value metadata file.");
    }
  }

  public Map<String, String> readKeyData(String sessionId) {
    final File f = getKeysFileForSession(sessionId);
    if (!f.exists()) {
      return Collections.emptyMap();
    }

    InputStream is = null;
    try {
      is = new FileInputStream(f);
      return jsonToKeysData(CommonUtils.streamToString(is));
    } catch (Exception e) {
      Logger.getLogger().e("Error deserializing user metadata.", e);
    } finally {
      CommonUtils.closeOrLog(is, "Failed to close user metadata file.");
    }
    return Collections.emptyMap();
  }

  @NonNull
  public File getUserDataFileForSession(String sessionId) {
    return new File(filesDir, sessionId + USERDATA_SUFFIX + METADATA_EXT);
  }

  @NonNull
  public File getKeysFileForSession(String sessionId) {
    return new File(filesDir, sessionId + KEYDATA_SUFFIX + METADATA_EXT);
  }

  private static UserMetadata jsonToUserData(String json) throws JSONException {
    final JSONObject dataObj = new JSONObject(json);
    UserMetadata metadata = new UserMetadata();
    metadata.setUserId(valueOrNull(dataObj, KEY_USER_ID));
    return metadata;
  }

  private static String userDataToJson(final UserMetadata userData) throws JSONException {
    return new JSONObject() {
      {
        put(KEY_USER_ID, userData.getUserId());
      }
    }.toString();
  }

  private static Map<String, String> jsonToKeysData(String json) throws JSONException {
    final JSONObject dataObj = new JSONObject(json);
    final Map<String, String> keyData = new HashMap<>();
    final Iterator<String> keyIter = dataObj.keys();
    while (keyIter.hasNext()) {
      final String key = keyIter.next();
      keyData.put(key, valueOrNull(dataObj, key));
    }
    return keyData;
  }

  private static String keysDataToJson(final Map<String, String> keyData) throws JSONException {
    return new JSONObject(keyData).toString();
  }

  private static String valueOrNull(JSONObject json, String key) {
    return !json.isNull(key) ? json.optString(key, null) : null;
  }
}
