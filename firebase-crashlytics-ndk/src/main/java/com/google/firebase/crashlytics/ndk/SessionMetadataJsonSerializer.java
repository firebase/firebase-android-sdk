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

package com.google.firebase.crashlytics.ndk;

import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;

class SessionMetadataJsonSerializer {
  private SessionMetadataJsonSerializer() {}

  static String serializeBeginSession(String sessionId, String generator, long startedAtSeconds) {
    final Map<String, Object> data = new HashMap<>();
    data.put("session_id", sessionId);
    data.put("generator", generator);
    data.put("started_at_seconds", startedAtSeconds);
    return new JSONObject(data).toString();
  }

  static String serializeSessionApp(
      String appIdentifier,
      String versionCode,
      String versionName,
      String installUuid,
      int deliveryMechanism,
      String unityVersion) {
    final Map<String, Object> data = new HashMap<>();
    data.put("app_identifier", appIdentifier);
    data.put("version_code", versionCode);
    data.put("version_name", versionName);
    data.put("install_uuid", installUuid);
    data.put("delivery_mechanism", deliveryMechanism);
    data.put("unity_version", unityVersion);
    return new JSONObject(data).toString();
  }

  static String serializeSessionOs(String osRelease, String osCodeName, boolean isRooted) {
    final Map<String, Object> data = new HashMap<>();
    data.put("version", osRelease);
    data.put("build_version", osCodeName);
    data.put("is_rooted", isRooted);
    return new JSONObject(data).toString();
  }

  static String serializeSessionDevice(
      int arch,
      String model,
      int availableProcessors,
      long totalRam,
      long diskSpace,
      boolean isEmulator,
      int state,
      String manufacturer,
      String modelClass) {
    final Map<String, Object> data = new HashMap<>();
    data.put("arch", arch);
    data.put("build_model", model);
    data.put("available_processors", availableProcessors);
    data.put("total_ram", totalRam);
    data.put("disk_space", diskSpace);
    data.put("is_emulator", isEmulator);
    data.put("state", state);
    data.put("build_manufacturer", manufacturer);
    data.put("build_product", modelClass);
    return new JSONObject(data).toString();
  }
}
