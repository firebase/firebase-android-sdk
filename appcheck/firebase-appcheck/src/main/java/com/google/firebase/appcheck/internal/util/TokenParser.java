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

package com.google.firebase.appcheck.internal.util;

import static com.google.android.gms.common.internal.Preconditions.checkNotEmpty;

import android.text.TextUtils;
import android.util.Base64;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.ArrayMap;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class TokenParser {

  @NonNull
  public static Map<String, Object> parseTokenClaims(@NonNull String token) {
    checkNotEmpty(token);
    String[] tokenSubSections = token.split("\\.", -1);
    if (tokenSubSections.length < 2) {
      Logger.getLogger().e("Invalid token (too few subsections):\n" + token);
      return Collections.emptyMap();
    }
    String encodedToken = tokenSubSections[1];
    try {
      String decodedToken =
          new String(
              Base64.decode(encodedToken, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING),
              "UTF-8");
      Map<String, Object> map = parseJsonIntoMap(decodedToken);
      return map == null ? Collections.emptyMap() : map;
    } catch (UnsupportedEncodingException e) {
      Logger.getLogger().e("Unable to decode token (charset unknown):\n" + e);
      return Collections.emptyMap();
    }
  }

  @Nullable
  private static Map<String, Object> parseJsonIntoMap(String rawJson) {
    if (TextUtils.isEmpty(rawJson)) {
      return null;
    }
    try {
      JSONObject jsonObject = new JSONObject(rawJson);
      if (jsonObject != JSONObject.NULL) {
        return toMap(jsonObject);
      } else {
        return null;
      }
    } catch (Exception e) {
      Logger.getLogger().d("Failed to parse JSONObject into Map:\n" + e);
      return Collections.emptyMap();
    }
  }

  private static Map<String, Object> toMap(JSONObject json) throws JSONException {
    Map<String, Object> map = new ArrayMap<>();
    Iterator<String> keyItr = json.keys();
    while (keyItr.hasNext()) {
      String key = keyItr.next();
      Object value = json.get(key);
      if (value instanceof JSONArray) {
        value = toList((JSONArray) value);
      } else if (value instanceof JSONObject) {
        value = toMap((JSONObject) value);
      } else if (value.equals(JSONObject.NULL)) {
        value = null;
      }
      map.put(key, value);
    }
    return map;
  }

  private static List<Object> toList(JSONArray array) throws JSONException {
    List<Object> list = new ArrayList<>();
    for (int i = 0; i < array.length(); i++) {
      Object value = array.get(i);
      if (value instanceof JSONArray) {
        value = toList((JSONArray) value);
      } else if (value instanceof JSONObject) {
        value = toMap((JSONObject) value);
      }
      list.add(value);
    }
    return list;
  }
}
