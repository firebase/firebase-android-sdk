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

package com.google.firebase.functions;

import androidx.annotation.VisibleForTesting;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Converts raw Java types into JSON objects. */
class Serializer {
  @VisibleForTesting
  static final String LONG_TYPE = "type.googleapis.com/google.protobuf.Int64Value";

  @VisibleForTesting
  static final String UNSIGNED_LONG_TYPE = "type.googleapis.com/google.protobuf.UInt64Value";

  private final DateFormat dateFormat;

  public Serializer() {
    // Encode Dates as UTC ISO 8601 strings.
    dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
  }

  public Object encode(Object obj) {
    if (obj == null || obj == JSONObject.NULL) {
      return JSONObject.NULL;
    }
    if (obj instanceof Long) {
      // JavaScript can't handle the full range of a long, so we use a wrapped string.
      JSONObject wrapped = new JSONObject();
      try {
        wrapped.put("@type", LONG_TYPE);
        wrapped.put("value", obj.toString());
      } catch (JSONException e) {
        // This should never happen.
        throw new RuntimeException("Error encoding Long.", e);
      }
      return wrapped;
    }
    if (obj instanceof Number) {
      return obj;
    }
    if (obj instanceof String) {
      return obj;
    }
    if (obj instanceof Boolean) {
      return obj;
    }
    if (obj instanceof JSONObject) {
      return obj;
    }
    if (obj instanceof JSONArray) {
      return obj;
    }
    if (obj instanceof Map) {
      JSONObject result = new JSONObject();
      Map<?, ?> m = (Map<?, ?>) obj;
      for (Object k : m.keySet()) {
        if (!(k instanceof String)) {
          throw new IllegalArgumentException("Object keys must be strings.");
        }
        String key = (String) k;
        Object value = encode(m.get(k));
        try {
          result.put(key, value);
        } catch (JSONException e) {
          // This should never happen.
          throw new RuntimeException(e);
        }
      }
      return result;
    }
    if (obj instanceof List) {
      JSONArray result = new JSONArray();
      List<?> l = (List<?>) obj;
      for (Object o : l) {
        result.put(encode(o));
      }
      return result;
    }
    if (obj instanceof JSONObject) {
      JSONObject result = new JSONObject();
      JSONObject m = (JSONObject) obj;
      Iterator<String> keys = m.keys();
      while (keys.hasNext()) {
        String k = keys.next();
        if (k == null) {
          throw new IllegalArgumentException("Object keys cannot be null.");
        }
        String key = (String) k;
        Object value = encode(m.opt(k));
        try {
          result.put(key, value);
        } catch (JSONException e) {
          // This should never happen.
          throw new RuntimeException(e);
        }
      }
      return result;
    }
    if (obj instanceof JSONArray) {
      JSONArray result = new JSONArray();
      JSONArray l = (JSONArray) obj;
      for (int i = 0; i < l.length(); i++) {
        Object o = l.opt(i);
        result.put(encode(o));
      }
      return result;
    }
    throw new IllegalArgumentException("Object cannot be encoded in JSON: " + obj);
  }

  // TODO: Maybe this should throw a FirebaseFunctionsException instead?
  public Object decode(Object obj) {
    if (obj instanceof Number) {
      return obj;
    }
    if (obj instanceof String) {
      return obj;
    }
    if (obj instanceof Boolean) {
      return obj;
    }
    if (obj instanceof JSONObject) {
      if (((JSONObject) obj).has("@type")) {
        String type = (((JSONObject) obj).optString("@type"));
        String value = (((JSONObject) obj).optString("value"));
        if (type.equals(LONG_TYPE)) {
          // Decode the value as a Long.
          try {
            return Long.parseLong(value);
          } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid Long format:" + value);
          }
        } else if (type.equals(UNSIGNED_LONG_TYPE)) {
          // Decode the value as a Long.
          // This will fail for numbers outside the normal range for a Long.
          // TODO: Once min API version is >26, should switch to Long.parseUnsignedLong.
          try {
            return Long.parseLong(value);
          } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid Long format:" + value);
          }
        }
        // If the type is not a known type, just decode it as a map.
      }
      Map<String, Object> result = new HashMap<String, Object>();
      Iterator<String> keys = ((JSONObject) obj).keys();
      while (keys.hasNext()) {
        String key = keys.next();
        Object value = decode(((JSONObject) obj).opt(key));
        result.put(key, value);
      }
      return result;
    }
    if (obj instanceof JSONArray) {
      List<Object> result = new ArrayList<Object>();
      for (int i = 0; i < ((JSONArray) obj).length(); i++) {
        Object value = decode(((JSONArray) obj).opt(i));
        result.add(value);
      }
      return result;
    }
    if (obj == JSONObject.NULL) {
      return null;
    }
    throw new IllegalArgumentException("Object cannot be decoded from JSON: " + obj);
  }
}
