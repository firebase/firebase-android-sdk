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

package com.google.firebase.crashlytics.internal.ndk;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.crashlytics.internal.Logger;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

class BinaryImagesConverter {
  private static final String DATA_DIR = "/data";

  interface FileIdStrategy {
    String createId(File file) throws IOException;
  }

  private final Context context;
  private final FileIdStrategy fileIdStrategy;

  BinaryImagesConverter(Context context, FileIdStrategy fileIdStrategy) {
    this.context = context;
    this.fileIdStrategy = fileIdStrategy;
  }

  @NonNull
  byte[] convert(String raw) throws IOException {
    final JSONArray binaryImagesJson = parseProcMapsJsonFromString(raw);
    return generateBinaryImagesJsonString(binaryImagesJson);
  }

  @NonNull
  byte[] convert(BufferedReader reader) throws IOException {
    final JSONArray binaryImagesJson = parseProcMapsJsonFromStream(reader);
    return generateBinaryImagesJsonString(binaryImagesJson);
  }

  @NonNull
  private JSONArray parseProcMapsJsonFromStream(BufferedReader reader) throws IOException {
    final JSONArray binaryImagesJson = new JSONArray();

    String mapEntryString;
    while ((mapEntryString = reader.readLine()) != null) {
      final JSONObject mapJson = jsonFromMapEntryString(mapEntryString);
      if (mapJson != null) {
        binaryImagesJson.put(mapJson);
      }
    }

    return binaryImagesJson;
  }

  @NonNull
  private JSONArray parseProcMapsJsonFromString(String rawProcMapsString) {
    final JSONArray binaryImagesJson = new JSONArray();

    String mapsString;
    try {
      final JSONObject rawObj = new JSONObject(rawProcMapsString);
      final JSONArray maps = rawObj.getJSONArray("maps");
      mapsString = joinMapsEntries(maps);
    } catch (JSONException e) {
      Logger.getLogger().w("Unable to parse proc maps string", e);
      return binaryImagesJson;
    }

    final String[] mapsEntries = mapsString.split("\\|");

    for (int i = 0; i < mapsEntries.length; ++i) {
      final String mapEntryString = mapsEntries[i];
      final JSONObject mapJson = jsonFromMapEntryString(mapEntryString);
      if (mapJson != null) {
        binaryImagesJson.put(mapJson);
      }
    }

    return binaryImagesJson;
  }

  @Nullable
  private JSONObject jsonFromMapEntryString(String mapEntryString) {
    final ProcMapEntry mapInfo = ProcMapEntryParser.parse(mapEntryString);

    if (mapInfo == null || !isRelevant(mapInfo)) {
      return null;
    }

    final String path = mapInfo.path;
    final File binFile = getLibraryFile(path);

    String uuid;
    try {
      uuid = fileIdStrategy.createId(binFile);
    } catch (IOException e) {
      Logger.getLogger().d("Could not generate ID for file " + mapInfo.path, e);
      return null;
    }

    try {
      return createBinaryImageJson(uuid, mapInfo);
    } catch (JSONException e) {
      Logger.getLogger().d("Could not create a binary image json string", e);
    }

    return null;
  }

  @NonNull
  private File getLibraryFile(String path) {
    File libFile = new File(path);
    if (!libFile.exists()) {
      libFile = correctDataPath(libFile);
    }
    return libFile;
  }

  @NonNull
  private File correctDataPath(File missingFile) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD) {
      return missingFile;
    }

    if (missingFile.getAbsolutePath().startsWith(DATA_DIR)) {
      try {
        final ApplicationInfo ai =
            context.getPackageManager().getApplicationInfo(context.getPackageName(), 0);
        missingFile = new File(ai.nativeLibraryDir, missingFile.getName());
      } catch (PackageManager.NameNotFoundException e) {
        Logger.getLogger().e("Error getting ApplicationInfo", e);
      }
    }
    return missingFile;
  }

  @NonNull
  private static byte[] generateBinaryImagesJsonString(JSONArray binaryImages) {
    final JSONObject binaryImagesObject = new JSONObject();
    try {
      binaryImagesObject.put("binary_images", binaryImages);
    } catch (JSONException e) {
      Logger.getLogger().w("Binary images string is null", e);
      return new byte[] {};
    }
    return binaryImagesObject.toString().getBytes(Charset.forName("UTF-8"));
  }

  @NonNull
  private static JSONObject createBinaryImageJson(String uuid, ProcMapEntry mapEntry)
      throws JSONException {
    final JSONObject binaryImage = new JSONObject();
    binaryImage.put("base_address", mapEntry.address);
    binaryImage.put("size", mapEntry.size);
    binaryImage.put("name", mapEntry.path);
    binaryImage.put("uuid", uuid);
    return binaryImage;
  }

  @NonNull
  private static String joinMapsEntries(JSONArray array) throws JSONException {
    final StringBuilder sb = new StringBuilder();
    for (int i = 0; i < array.length(); ++i) {
      sb.append(array.getString(i));
    }
    return sb.toString();
  }

  private static boolean isRelevant(ProcMapEntry mapEntry) {
    return mapEntry.perms.indexOf('x') != -1 && mapEntry.path.indexOf('/') != -1;
  }
}
