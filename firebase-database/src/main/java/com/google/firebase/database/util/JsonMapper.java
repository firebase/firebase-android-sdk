// Copyright 2018 Google LLC
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

package com.google.firebase.database.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;
import org.json.JSONTokener;

/**
 * Helper class to convert from/to JSON strings. TODO: This class should ideally not live in
 * firebase-database-connection, but it's required by both firebase-database and
 * firebase-database-connection, so leave it here for now.
 */
public class JsonMapper {

  public static String serializeJson(Map<String, Object> object) throws IOException {
    return serializeJsonValue(object);
  }

  @SuppressWarnings("unchecked")
  public static String serializeJsonValue(Object object) throws IOException {
    if (object == null) {
      return "null";
    } else if (object instanceof String) {
      return JSONObject.quote((String) object);
    } else if (object instanceof Number) {
      try {
        return JSONObject.numberToString((Number) object);
      } catch (JSONException e) {
        throw new IOException("Could not serialize number", e);
      }
    } else if (object instanceof Boolean) {
      return ((Boolean) object) ? "true" : "false";
    } else {
      try {
        JSONStringer stringer = new JSONStringer();
        serializeJsonValue(object, stringer);
        return stringer.toString();
      } catch (JSONException e) {
        throw new IOException("Failed to serialize JSON", e);
      }
    }
  }

  private static void serializeJsonValue(Object object, JSONStringer stringer)
      throws IOException, JSONException {
    if (object instanceof Map) {
      stringer.object();
      @SuppressWarnings("unchecked")
      Map<String, Object> map = (Map<String, Object>) object;
      for (Map.Entry<String, Object> entry : map.entrySet()) {
        stringer.key(entry.getKey());
        serializeJsonValue(entry.getValue(), stringer);
      }
      stringer.endObject();
    } else if (object instanceof Collection) {
      Collection<?> collection = (Collection<?>) object;
      stringer.array();
      for (Object entry : collection) {
        serializeJsonValue(entry, stringer);
      }
      stringer.endArray();
    } else {
      stringer.value(object);
    }
  }

  public static Map<String, Object> parseJson(String json) throws IOException {
    try {
      return unwrapJsonObject(new JSONObject(json));
    } catch (JSONException e) {
      throw new IOException(e);
    }
  }

  public static Object parseJsonValue(String json) throws IOException {
    try {
      return unwrapJson(new JSONTokener(json).nextValue());
    } catch (JSONException e) {
      throw new IOException(e);
    }
  }

  private static Map<String, Object> unwrapJsonObject(JSONObject jsonObject) throws JSONException {
    Map<String, Object> map = new HashMap<String, Object>(jsonObject.length());
    Iterator<String> keys = jsonObject.keys();
    while (keys.hasNext()) {
      String key = keys.next();
      map.put(key, unwrapJson(jsonObject.get(key)));
    }
    return map;
  }

  private static List<Object> unwrapJsonArray(JSONArray jsonArray) throws JSONException {
    List<Object> list = new ArrayList<Object>(jsonArray.length());
    for (int i = 0; i < jsonArray.length(); i++) {
      list.add(unwrapJson(jsonArray.get(i)));
    }
    return list;
  }

  private static Object unwrapJson(Object o) throws JSONException {
    if (o instanceof JSONObject) {
      return unwrapJsonObject((JSONObject) o);
    } else if (o instanceof JSONArray) {
      return unwrapJsonArray((JSONArray) o);
    } else if (o.equals(JSONObject.NULL)) {
      return null;
    } else {
      return o;
    }
  }
}
