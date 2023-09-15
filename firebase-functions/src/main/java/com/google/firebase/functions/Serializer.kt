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
package com.google.firebase.functions

import androidx.annotation.VisibleForTesting
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Converts raw Java types into JSON objects.  */
class Serializer {
  private val dateFormat: DateFormat

  init {
    // Encode Dates as UTC ISO 8601 strings.
    dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    dateFormat.timeZone = TimeZone.getTimeZone("UTC")
  }

  fun encode(obj: Any?): Any {
    if (obj == null || obj === JSONObject.NULL) {
      return JSONObject.NULL
    }
    if (obj is Long) {
      // JavaScript can't handle the full range of a long, so we use a wrapped string.
      val wrapped = JSONObject()
      try {
        wrapped.put("@type", LONG_TYPE)
        wrapped.put("value", obj.toString())
      } catch (e: JSONException) {
        // This should never happen.
        throw RuntimeException("Error encoding Long.", e)
      }
      return wrapped
    }
    if (obj is Number) {
      return obj
    }
    if (obj is String) {
      return obj
    }
    if (obj is Boolean) {
      return obj
    }
    if (obj is JSONObject) {
      return obj
    }
    if (obj is JSONArray) {
      return obj
    }
    if (obj is Map<*, *>) {
      val result = JSONObject()
      val m = obj
      for (k in m.keys) {
        require(k is String) { "Object keys must be strings." }
        val value = encode(m[k])
        try {
          result.put(k, value)
        } catch (e: JSONException) {
          // This should never happen.
          throw RuntimeException(e)
        }
      }
      return result
    }
    if (obj is List<*>) {
      val result = JSONArray()
      for (o in obj) {
        result.put(encode(o))
      }
      return result
    }
    if (obj is JSONObject) {
      val result = JSONObject()
      val m = obj
      val keys = m.keys()
      while (keys.hasNext()) {
        val k = keys.next()
                ?: throw IllegalArgumentException("Object keys cannot be null.")
        val value = encode(m.opt(k))
        try {
          result.put(k, value)
        } catch (e: JSONException) {
          // This should never happen.
          throw RuntimeException(e)
        }
      }
      return result
    }
    if (obj is JSONArray) {
      val result = JSONArray()
      val l = obj
      for (i in 0 until l.length()) {
        val o = l.opt(i)
        result.put(encode(o))
      }
      return result
    }
    throw IllegalArgumentException("Object cannot be encoded in JSON: $obj")
  }

  // TODO: Maybe this should throw a FirebaseFunctionsException instead?
  fun decode(obj: Any): Any? {
    if (obj is Number) {
      return obj
    }
    if (obj is String) {
      return obj
    }
    if (obj is Boolean) {
      return obj
    }
    if (obj is JSONObject) {
      if (obj.has("@type")) {
        val type = obj.optString("@type")
        val value = obj.optString("value")
        if (type == LONG_TYPE) {
          // Decode the value as a Long.
          return try {
            value.toLong()
          } catch (e: NumberFormatException) {
            throw IllegalArgumentException("Invalid Long format:$value")
          }
        } else if (type == UNSIGNED_LONG_TYPE) {
          // Decode the value as a Long.
          // This will fail for numbers outside the normal range for a Long.
          // TODO: Once min API version is >26, should switch to Long.parseUnsignedLong.
          return try {
            value.toLong()
          } catch (e: NumberFormatException) {
            throw IllegalArgumentException("Invalid Long format:$value")
          }
        }
        // If the type is not a known type, just decode it as a map.
      }
      val result: MutableMap<String, Any?> = HashMap()
      val keys = obj.keys()
      while (keys.hasNext()) {
        val key = keys.next()
        val value = decode(obj.opt(key))
        result[key] = value
      }
      return result
    }
    if (obj is JSONArray) {
      val result: MutableList<Any?> = ArrayList()
      for (i in 0 until obj.length()) {
        val value = decode(obj.opt(i))
        result.add(value)
      }
      return result
    }
    if (obj === JSONObject.NULL) {
      return null
    }
    throw IllegalArgumentException("Object cannot be decoded from JSON: $obj")
  }

  companion object {
    @VisibleForTesting
    const val LONG_TYPE = "type.googleapis.com/google.protobuf.Int64Value"

    @VisibleForTesting
    const val UNSIGNED_LONG_TYPE = "type.googleapis.com/google.protobuf.UInt64Value"
  }
}