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

package com.google.firebase.decoders.json;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import androidx.annotation.NonNull;
import com.google.firebase.decoders.FieldRef;
import com.google.firebase.decoders.ObjectDecoder;
import com.google.firebase.decoders.ObjectDecoderContext;
import com.google.firebase.decoders.Safe;
import com.google.firebase.decoders.TypeCreator;
import com.google.firebase.decoders.TypeToken;
import com.google.firebase.encoders.FieldDescriptor;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class JsonDataDecoderBuilderContextMapDecodingTest {

  static class MapFoo {
    Map<String, String> map;

    MapFoo(Map<String, String> map) {
      this.map = map;
    }
  }

  static class MapFooObjectDecoder implements ObjectDecoder<MapFoo> {
    @NonNull
    @Override
    public TypeCreator<MapFoo> decode(@NonNull ObjectDecoderContext<MapFoo> ctx) {
      FieldRef.Boxed<HashMap<String, String>> mapField =
          ctx.decode(
              FieldDescriptor.of("map"), TypeToken.of(new Safe<HashMap<String, String>>() {}));
      return (creationCtx -> new MapFoo(creationCtx.get(mapField)));
    }
  }

  @Test
  public void nestedMap_shouldBeDecodedCorrectly() throws IOException {
    Map<Class<?>, ObjectDecoder<?>> objectDecoders = new HashMap<>();
    objectDecoders.put(MapFoo.class, new MapFooObjectDecoder());
    JsonDataDecoderBuilderContext jsonDataDecoderBuilderContext =
        new JsonDataDecoderBuilderContext(objectDecoders);

    String json = "{\"map\": {\"1\": \"1\", \"2\": \"2\"}}";
    InputStream input = new ByteArrayInputStream(json.getBytes(UTF_8));
    MapFoo mapFoo =
        jsonDataDecoderBuilderContext.decode(input, TypeToken.of(new Safe<MapFoo>() {}));

    assertThat(mapFoo.map.get("1")).isEqualTo("1");
    assertThat(mapFoo.map.get("2")).isEqualTo("2");
  }

  @Test
  public void map_shouldBeDecodedCorrectly() throws IOException {
    Map<Class<?>, ObjectDecoder<?>> objectDecoders = new HashMap<>();
    JsonDataDecoderBuilderContext jsonDataDecoderBuilderContext =
        new JsonDataDecoderBuilderContext(objectDecoders);

    String json = "{\"1.1\": \"1\", \"2.2\": \"2\"}";
    InputStream input = new ByteArrayInputStream(json.getBytes(UTF_8));
    Map<Double, String> map =
        jsonDataDecoderBuilderContext.decode(
            input, TypeToken.of(new Safe<HashMap<Double, String>>() {}));

    assertThat(map.get(1.1)).isEqualTo("1");
    assertThat(map.get(2.2)).isEqualTo("2");
  }

  static class SimpleFoo {
    int i;

    SimpleFoo(int i) {
      this.i = i;
    }
  }

  static class SimpleFooObjectDecoder implements ObjectDecoder<SimpleFoo> {
    @NonNull
    @Override
    public TypeCreator<SimpleFoo> decode(@NonNull ObjectDecoderContext<SimpleFoo> ctx) {
      FieldRef.Primitive<Integer> iField = ctx.decodeInteger(FieldDescriptor.of("i"));
      return (creationCtx -> new SimpleFoo(creationCtx.getInteger(iField)));
    }
  }

  @Test
  public void mapWithCustomizedObjectValue_shouldBeDecodedCorrectly() throws IOException {
    Map<Class<?>, ObjectDecoder<?>> objectDecoders = new HashMap<>();
    objectDecoders.put(SimpleFoo.class, new SimpleFooObjectDecoder());
    JsonDataDecoderBuilderContext jsonDataDecoderBuilderContext =
        new JsonDataDecoderBuilderContext(objectDecoders);

    String json = "{\"1\": {\"i\":1}}";
    InputStream input = new ByteArrayInputStream(json.getBytes(UTF_8));
    Map<String, SimpleFoo> map =
        jsonDataDecoderBuilderContext.decode(
            input, TypeToken.of(new Safe<HashMap<String, SimpleFoo>>() {}));

    assertThat(map.get("1").i).isEqualTo(1);
  }

  @Test
  public void mapWithCustomizedObjectKey_shouldThrowException() throws IOException {
    Map<Class<?>, ObjectDecoder<?>> objectDecoders = new HashMap<>();
    objectDecoders.put(SimpleFoo.class, new SimpleFooObjectDecoder());
    JsonDataDecoderBuilderContext jsonDataDecoderBuilderContext =
        new JsonDataDecoderBuilderContext(objectDecoders);

    String json = "{{\"i\":1}: \"1\"}";
    InputStream input = new ByteArrayInputStream(json.getBytes(UTF_8));

    assertThrows(
        "Customized Object cannot be used as Map key.",
        IllegalArgumentException.class,
        () -> {
          jsonDataDecoderBuilderContext.decode(
              input, TypeToken.of(new Safe<HashMap<SimpleFoo, String>>() {}));
        });
  }

  @Test
  public void mapContainsDuplicateKeys_shouldThrowException() throws IOException {
    Map<Class<?>, ObjectDecoder<?>> objectDecoders = new HashMap<>();
    JsonDataDecoderBuilderContext jsonDataDecoderBuilderContext =
        new JsonDataDecoderBuilderContext(objectDecoders);

    String json = "{\"1\": \"1\", \"1\": \"2\"}";
    InputStream input = new ByteArrayInputStream(json.getBytes(UTF_8));

    assertThrows(
        "duplicate key found",
        IllegalArgumentException.class,
        () -> {
          jsonDataDecoderBuilderContext.decode(
              input, TypeToken.of(new Safe<HashMap<String, String>>() {}));
        });
  }

  @Test
  public void map_shouldDecodedAsHashMapClassInDefault() throws IOException {
    Map<Class<?>, ObjectDecoder<?>> objectDecoders = new HashMap<>();
    JsonDataDecoderBuilderContext jsonDataDecoderBuilderContext =
        new JsonDataDecoderBuilderContext(objectDecoders);

    String json = "{\"1\": \"1\"}";
    InputStream input = new ByteArrayInputStream(json.getBytes(UTF_8));

    Map<String, String> map =
        jsonDataDecoderBuilderContext.decode(
            input, TypeToken.of(new Safe<Map<String, String>>() {}));
    assertThat(map instanceof HashMap).isTrue();
  }

  @Test
  public void sortedMap_shouldDecodedAsHashMapClassInDefault() throws IOException {
    Map<Class<?>, ObjectDecoder<?>> objectDecoders = new HashMap<>();
    JsonDataDecoderBuilderContext jsonDataDecoderBuilderContext =
        new JsonDataDecoderBuilderContext(objectDecoders);

    String json = "{\"1\": \"1\"}";
    InputStream input = new ByteArrayInputStream(json.getBytes(UTF_8));

    SortedMap<String, String> map =
        jsonDataDecoderBuilderContext.decode(
            input, TypeToken.of(new Safe<SortedMap<String, String>>() {}));
    assertThat(map instanceof TreeMap).isTrue();
  }
}
