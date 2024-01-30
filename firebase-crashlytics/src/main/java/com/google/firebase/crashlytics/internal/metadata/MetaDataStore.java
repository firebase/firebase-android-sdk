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

package com.google.firebase.crashlytics.internal.metadata;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.crashlytics.internal.common.CommonUtils;
import com.google.firebase.crashlytics.internal.persistence.FileStore;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Handles persistence of metadata for cases when we need to reload it on app restart for submission
 * with a previously unclosed crash session.
 */
class MetaDataStore {

  private static final Charset UTF_8 = Charset.forName("UTF-8");

  private static final String KEY_USER_ID = "userId";

  private final FileStore fileStore;

  public MetaDataStore(FileStore fileStore) {
    this.fileStore = fileStore;
  }

  public void writeUserData(String sessionId, String userId) {
    final File f = getUserDataFileForSession(sessionId);
    Writer writer = null;
    try {
      final String userIdJson = userIdToJson(userId);
      writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), UTF_8));
      writer.write(userIdJson);
      writer.flush();
    } catch (Exception e) {
      Logger.getLogger().w("Error serializing user metadata.", e);
    } finally {
      CommonUtils.closeOrLog(writer, "Failed to close user metadata file.");
    }
  }

  @Nullable
  public String readUserId(String sessionId) {
    final File f = getUserDataFileForSession(sessionId);
    if (!f.exists() || f.length() == 0) {
      Logger.getLogger().d("No userId set for session " + sessionId);
      safeDeleteCorruptFile(f);
      return null;
    }

    InputStream is = null;
    try {
      is = new FileInputStream(f);
      String userId = jsonToUserId(CommonUtils.streamToString(is));
      Logger.getLogger().d("Loaded userId " + userId + " for session " + sessionId);
      return userId;
    } catch (Exception e) {
      Logger.getLogger().w("Error deserializing user metadata.", e);
      safeDeleteCorruptFile(f);
    } finally {
      CommonUtils.closeOrLog(is, "Failed to close user metadata file.");
    }
    return null;
  }

  public void writeKeyData(String sessionId, Map<String, String> keyData) {
    writeKeyData(sessionId, keyData, false);
  }

  public void writeKeyData(String sessionId, Map<String, String> keyData, boolean isInternal) {
    final File f =
        isInternal ? getInternalKeysFileForSession(sessionId) : getKeysFileForSession(sessionId);
    Writer writer = null;
    try {
      final String keyDataString = keysDataToJson(keyData);
      writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), UTF_8));
      writer.write(keyDataString);
      writer.flush();
    } catch (Exception e) {
      Logger.getLogger().w("Error serializing key/value metadata.", e);
      safeDeleteCorruptFile(f);
    } finally {
      CommonUtils.closeOrLog(writer, "Failed to close key/value metadata file.");
    }
  }

  public Map<String, String> readKeyData(String sessionId) {
    return readKeyData(sessionId, false);
  }

  Map<String, String> readKeyData(String sessionId, boolean isInternal) {
    final File f =
        isInternal ? getInternalKeysFileForSession(sessionId) : getKeysFileForSession(sessionId);
    if (!f.exists() || f.length() == 0) {
      safeDeleteCorruptFile(f);
      return Collections.emptyMap();
    }

    InputStream is = null;
    try {
      is = new FileInputStream(f);
      return jsonToKeysData(CommonUtils.streamToString(is));
    } catch (Exception e) {
      Logger.getLogger().w("Error deserializing user metadata.", e);
      safeDeleteCorruptFile(f);
    } finally {
      CommonUtils.closeOrLog(is, "Failed to close user metadata file.");
    }
    return Collections.emptyMap();
  }

  public List<RolloutAssignment> readRolloutsState(String sessionId) {
    final File f = getRolloutsStateForSession(sessionId);
    if (!f.exists() || f.length() == 0) {
      safeDeleteCorruptFile(f);
      return Collections.emptyList();
    }

    InputStream is = null;
    try {
      is = new FileInputStream(f);
      List<RolloutAssignment> rolloutsState = jsonToRolloutsState(CommonUtils.streamToString(is));
      Logger.getLogger()
          .d("Loaded rollouts state:\n" + rolloutsState + "\nfor session " + sessionId);
      return rolloutsState;
    } catch (Exception e) {
      Logger.getLogger().w("Error deserializing rollouts state.", e);
      safeDeleteCorruptFile(f);
    } finally {
      CommonUtils.closeOrLog(is, "Failed to close rollouts state file.");
    }
    return Collections.emptyList();
  }

  public void writeRolloutState(String sessionId, List<RolloutAssignment> rolloutsState) {
    final File f = getRolloutsStateForSession(sessionId);
    if (rolloutsState.isEmpty()) {
      safeDeleteCorruptFile(f);
      return;
    }

    Writer writer = null;
    try {
      final String rolloutsStateString = rolloutsStateToJson(rolloutsState);
      writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), UTF_8));
      writer.write(rolloutsStateString);
      writer.flush();
    } catch (Exception e) {
      Logger.getLogger().w("Error serializing rollouts state.", e);
      safeDeleteCorruptFile(f);
    } finally {
      CommonUtils.closeOrLog(writer, "Failed to close rollouts state file.");
    }
  }

  @NonNull
  public File getUserDataFileForSession(String sessionId) {
    return fileStore.getSessionFile(sessionId, UserMetadata.USERDATA_FILENAME);
  }

  @NonNull
  public File getKeysFileForSession(String sessionId) {
    return fileStore.getSessionFile(sessionId, UserMetadata.KEYDATA_FILENAME);
  }

  @NonNull
  public File getInternalKeysFileForSession(String sessionId) {
    return fileStore.getSessionFile(sessionId, UserMetadata.INTERNAL_KEYDATA_FILENAME);
  }

  @NonNull
  public File getRolloutsStateForSession(String sessionId) {
    return fileStore.getSessionFile(sessionId, UserMetadata.ROLLOUTS_STATE_FILENAME);
  }

  @Nullable
  private String jsonToUserId(String json) throws JSONException {
    final JSONObject dataObj = new JSONObject(json);
    return valueOrNull(dataObj, KEY_USER_ID);
  }

  private static String userIdToJson(String userId) throws JSONException {
    return new JSONObject() {
      {
        put(KEY_USER_ID, userId);
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

  private static String keysDataToJson(final Map<String, String> keyData) {
    return new JSONObject(keyData).toString();
  }

  private static List<RolloutAssignment> jsonToRolloutsState(String json) throws JSONException {
    JSONObject object = new JSONObject(json);
    JSONArray dataArray = object.getJSONArray(RolloutAssignmentList.ROLLOUTS_STATE);
    List<RolloutAssignment> rolloutsState = new ArrayList<RolloutAssignment>();

    for (int i = 0; i < dataArray.length(); i++) {
      String dataObjectString = dataArray.getString(i);

      try {
        final RolloutAssignment rolloutAssignment = RolloutAssignment.create(dataObjectString);
        rolloutsState.add(rolloutAssignment);
      } catch (Exception e) {
        Logger.getLogger().w("Failed de-serializing rollouts state. " + dataObjectString, e);
      }
    }
    return rolloutsState;
  }

  private static String rolloutsStateToJson(List<RolloutAssignment> rolloutsState) {
    HashMap<String, JSONArray> jsonObject = new HashMap<>();
    JSONArray rolloutsStateJsonArray = new JSONArray();
    for (int i = 0; i < rolloutsState.size(); i++) {
      String rolloutAssignmentJson =
          RolloutAssignment.ROLLOUT_ASSIGNMENT_JSON_ENCODER.encode(rolloutsState.get(i));
      try {
        rolloutsStateJsonArray.put(new JSONObject(rolloutAssignmentJson));
      } catch (JSONException e) {
        Logger.getLogger().w("Exception parsing rollout assignment!", e);
      }
    }
    jsonObject.put(RolloutAssignmentList.ROLLOUTS_STATE, rolloutsStateJsonArray);

    return new JSONObject(jsonObject).toString();
  }

  private static String valueOrNull(JSONObject json, String key) {
    return !json.isNull(key) ? json.optString(key, null) : null;
  }

  private static void safeDeleteCorruptFile(File file) {
    if (file.exists() && file.delete()) {
      Logger.getLogger().i("Deleted corrupt file: " + file.getAbsolutePath());
    }
  }
}
