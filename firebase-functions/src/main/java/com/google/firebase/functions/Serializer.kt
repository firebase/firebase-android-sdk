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
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Converts raw Java types into JSON objects. */
internal class Serializer {
  private val dateFormat: DateFormat

  init {
    // Encode Dates as UTC ISO 8601 strings.
    dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    dateFormat.timeZone = TimeZone.getTimeZone("UTC")
  }

  public fun encode(obj: Any?): Any {
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
      for (k in obj.keys) {
        require(k is String) { "Object keys must be strings." }
        val value = encode(obj[k])
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
      val keys = obj.keys()
      while (keys.hasNext()) {
        val k = keys.next() ?: throw IllegalArgumentException("Object keys cannot be null.")
        val value = encode(obj.opt(k))
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
      for (i in 0 until obj.length()) {
        val o = obj.opt(i)
        result.put(encode(o))
      }
      return result
    }
    throw IllegalArgumentException("Object cannot be encoded in JSON: $obj")
  }

  public fun decode(obj: Any?): Any? {
    // TODO: Maybe this should throw a FirebaseFunctionsException instead?
    if (obj == null) return null
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

  internal companion object {
    @VisibleForTesting
    internal const val LONG_TYPE: String = "type.googleapis.com/google.protobuf.Int64Value"

    @VisibleForTesting
    internal const val UNSIGNED_LONG_TYPE: String =
      "type.googleapis.com/google.protobuf.UInt64Value"
  }
}
