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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.runner.AndroidJUnit4;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SerializerTest {
  @Test
  public void testEncodeNull() {
    Serializer serializer = new Serializer();
    assertEquals(JSONObject.NULL, serializer.encode(null));
  }

  @Test
  public void testEncodeJSONNull() {
    Serializer serializer = new Serializer();
    assertEquals(JSONObject.NULL, serializer.encode(JSONObject.NULL));
  }

  @Test
  public void testEncodeInt() {
    Serializer serializer = new Serializer();
    assertEquals(1, serializer.encode(1));
  }

  @Test
  public void testDecodeInt() {
    Serializer serializer = new Serializer();
    assertEquals(1, serializer.decode(1));
  }

  @Test
  public void testEncodeLong() {
    Serializer serializer = new Serializer();
    Object encoded = serializer.encode(-9223372036854775000L);
    assertTrue(encoded instanceof JSONObject);
    JSONObject actual = (JSONObject) encoded;
    assertEquals(Serializer.LONG_TYPE, actual.optString("@type"));
    assertEquals("-9223372036854775000", actual.optString("value"));
  }

  @Test
  public void testDecodeLong() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("@type", Serializer.LONG_TYPE);
    input.put("value", "-9223372036854775000");
    Serializer serializer = new Serializer();
    assertEquals(-9223372036854775000L, serializer.decode(input));
  }

  @Test
  public void testDecodeUnsignedLong() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("@type", Serializer.UNSIGNED_LONG_TYPE);
    input.put("value", "9223372036854775000");
    Serializer serializer = new Serializer();
    // When the min API version we support is >26, we should use a higher value here.
    assertEquals(9223372036854775000L, serializer.decode(input));
  }

  @Test
  public void testEncodeDouble() {
    Serializer serializer = new Serializer();
    assertEquals(1.2, serializer.encode(1.2));
  }

  @Test
  public void testDecodeDouble() {
    Serializer serializer = new Serializer();
    assertEquals(1.2, serializer.decode(1.2));
  }

  @Test
  public void testEncodeString() {
    Serializer serializer = new Serializer();
    assertEquals("hello", serializer.encode("hello"));
  }

  @Test
  public void testDecodeString() {
    Serializer serializer = new Serializer();
    assertEquals("hello", serializer.decode("hello"));
  }

  @Test
  public void testEncodeArray() throws JSONException {
    List<Object> input = Arrays.asList(1, "two", Arrays.asList(3, 4));

    Serializer serializer = new Serializer();
    Object result = serializer.encode(input);
    assertTrue(result instanceof JSONArray);
    JSONArray actual = (JSONArray) result;

    assertEquals(3, actual.length());
    assertEquals(1, actual.opt(0));
    assertEquals("two", actual.opt(1));
    assertTrue(actual.opt(2) instanceof JSONArray);
    JSONArray third = actual.optJSONArray(2);
    assertEquals(2, third.length());
    assertEquals(3, third.opt(0));
    assertEquals(4, third.opt(1));
  }

  @Test
  public void testDecodeArray() throws JSONException {
    JSONArray third = new JSONArray();
    third.put(3);
    third.put(4);
    JSONArray input = new JSONArray();
    input.put(1);
    input.put("two");
    input.put(third);

    List<Object> expected = Arrays.asList(1, "two", Arrays.asList(3, 4));

    Serializer serializer = new Serializer();
    Object actual = serializer.decode(input);

    assertEquals(expected, actual);
  }

  @Test
  public void testEncodeMap() throws JSONException {
    Map<String, Object> input = new HashMap<>();
    input.put("foo", 1);
    input.put("bar", "hello");
    input.put("baz", Arrays.asList(1, 2, 3));

    Serializer serializer = new Serializer();
    Object result = serializer.encode(input);
    assertTrue(result instanceof JSONObject);
    JSONObject actual = (JSONObject) result;

    assertEquals(1, actual.opt("foo"));
    assertEquals("hello", actual.opt("bar"));
    assertTrue(actual.opt("baz") instanceof JSONArray);
    JSONArray baz = actual.optJSONArray("baz");
    assertEquals(3, baz.length());
    assertEquals(1, baz.opt(0));
    assertEquals(2, baz.opt(1));
    assertEquals(3, baz.opt(2));
  }

  @Test
  public void testEncodeJSONObject() throws JSONException {
    JSONObject input = new JSONObject("{\"foo\":1,\"bar\":\"hello\",\"baz\":[1,2,3]}");

    Serializer serializer = new Serializer();
    Object result = serializer.encode(input);
    assertTrue(result instanceof JSONObject);
    JSONObject actual = (JSONObject) result;

    assertEquals(1, actual.opt("foo"));
    assertEquals("hello", actual.opt("bar"));
    assertTrue(actual.opt("baz") instanceof JSONArray);
    JSONArray baz = actual.optJSONArray("baz");
    assertEquals(3, baz.length());
    assertEquals(1, baz.opt(0));
    assertEquals(2, baz.opt(1));
    assertEquals(3, baz.opt(2));
  }

  @Test
  public void testDecodeMap() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("foo", 1);
    input.put("bar", "hello");
    JSONArray baz = new JSONArray();
    baz.put(1);
    baz.put(2);
    baz.put(3);
    input.put("baz", baz);

    Map<String, Object> expected = new HashMap<>();
    expected.put("foo", 1);
    expected.put("bar", "hello");
    expected.put("baz", Arrays.asList(1, 2, 3));

    Serializer serializer = new Serializer();
    Object actual = serializer.decode(input);

    assertEquals(expected, actual);
  }

  @Test
  public void testDecodeUnknownType() throws JSONException {
    JSONObject input = new JSONObject();
    input.put("@type", "unknown");
    input.put("value", "hello");

    Map<String, Object> expected = new HashMap<>();
    expected.put("@type", "unknown");
    expected.put("value", "hello");

    Serializer serializer = new Serializer();
    Object actual = serializer.decode(input);

    assertEquals(expected, actual);
  }
}
