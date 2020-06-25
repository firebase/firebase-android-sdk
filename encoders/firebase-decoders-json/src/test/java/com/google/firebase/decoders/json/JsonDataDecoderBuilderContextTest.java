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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class JsonDataDecoderBuilderContextTest {

  static class Foo<T> {
    int a;
    boolean b;
    T t;
    SubFoo<String> subFoo;

    Foo(int a, boolean b, T t, SubFoo<String> subFoo) {
      this.a = a;
      this.b = b;
      this.t = t;
      this.subFoo = subFoo;
    }
  }

  static class SubFoo<T> {
    int a;
    boolean b;
    T t;

    SubFoo(Integer a, Boolean b, T t) {
      this.a = a;
      this.b = b;
      this.t = t;
    }
  }

  static class FooObjectDecoder<T> implements ObjectDecoder<Foo<T>> {
    @NonNull
    @Override
    public TypeCreator<Foo<T>> decode(@NonNull ObjectDecoderContext<Foo<T>> ctx) {
      FieldRef.Primitive<Integer> aField = ctx.decodeInteger(FieldDescriptor.of("a"));
      FieldRef.Primitive<Boolean> bField = ctx.decodeBoolean(FieldDescriptor.of("b"));
      FieldRef.Boxed<T> tField = ctx.decode(FieldDescriptor.of("t"), ctx.getTypeArgument(0));
      FieldRef.Boxed<SubFoo<String>> subFooField =
          ctx.decode(FieldDescriptor.of("subFoo"), TypeToken.of(new Safe<SubFoo<String>>() {}));

      return (creationCtx ->
          new Foo<T>(
              creationCtx.getInteger(aField),
              creationCtx.getBoolean(bField),
              (T) creationCtx.get(tField),
              (SubFoo<String>) creationCtx.get(subFooField)));
    }
  }

  static class SubFooObjectDecoder<T> implements ObjectDecoder<SubFoo<T>> {

    @NonNull
    @Override
    public TypeCreator<SubFoo<T>> decode(@NonNull ObjectDecoderContext<SubFoo<T>> ctx) {
      FieldRef.Primitive<Integer> aField = ctx.decodeInteger(FieldDescriptor.of("a"));
      FieldRef.Primitive<Boolean> bField = ctx.decodeBoolean(FieldDescriptor.of("b"));
      FieldRef.Boxed<T> tField = ctx.decode(FieldDescriptor.of("t"), ctx.getTypeArgument(0));

      return (creationCtx ->
          new SubFoo<T>(
              creationCtx.getInteger(aField),
              creationCtx.getBoolean(bField),
              creationCtx.get(tField)));
    }
  }

  @Test
  public <T> void nestedGenericType_ShouldDecodeCorrectly() throws IOException {
    Map<Class<?>, ObjectDecoder<?>> objectDecoders = new HashMap<>();
    objectDecoders.put(Foo.class, new FooObjectDecoder<T>());
    objectDecoders.put(SubFoo.class, new SubFooObjectDecoder<T>());
    JsonDataDecoderBuilderContext jsonDataDecoderBuilderContext =
        new JsonDataDecoderBuilderContext(objectDecoders);
    String json =
        "{\"a\":1, \"b\":true, \"t\":\"str\", \"subFoo\": {\"a\":1, \"b\":true, \"t\":\"str\"}}";
    InputStream input = new ByteArrayInputStream(json.getBytes(UTF_8));
    Foo<String> foo2 =
        jsonDataDecoderBuilderContext.decode(input, TypeToken.of(new Safe<Foo<String>>() {}));
    input = new ByteArrayInputStream(json.getBytes(UTF_8));
    Foo<String> foo =
        jsonDataDecoderBuilderContext.decode(input, TypeToken.of(new Safe<Foo<String>>() {}));

    assertThat(foo.a).isEqualTo(1);
    assertThat(foo.b).isEqualTo(true);
    assertThat(foo.t).isEqualTo("str");
    assertThat(foo.subFoo.a).isEqualTo(1);
    assertThat(foo.subFoo.b).isEqualTo(true);
    assertThat(foo.subFoo.t).isEqualTo("str");
  }

  static class Node<T> {
    T t;
    Node<T> node;

    Node(T t, Node<T> node) {
      this.t = t;
      this.node = node;
    }
  }

  static class NodeObjectDecoder<T> implements ObjectDecoder<Node<T>> {
    @NonNull
    @Override
    public TypeCreator<Node<T>> decode(@NonNull ObjectDecoderContext<Node<T>> ctx) {
      FieldRef.Boxed<T> tField = ctx.decode(FieldDescriptor.of("t"), ctx.getTypeArgument(0));
      FieldRef.Boxed<Node<T>> nodeField =
          ctx.decode(FieldDescriptor.of("node"), ctx.getTypeToken());

      return (creationCtx -> new Node<T>(creationCtx.get(tField), creationCtx.get(nodeField)));
    }
  }

  @Test
  public void recursivelyDefinedTypes_ShouldDecodeCorrectly() throws IOException {
    Map<Class<?>, ObjectDecoder<?>> objectDecoders = new HashMap<>();
    objectDecoders.put(Node.class, new NodeObjectDecoder<>());
    JsonDataDecoderBuilderContext jsonDataDecoderBuilderContext =
        new JsonDataDecoderBuilderContext(objectDecoders);
    String json = "{\"t\":\"hello\", \"node\": {\"t\":\"world\", \"node\": null}}";
    InputStream input = new ByteArrayInputStream(json.getBytes(UTF_8));
    Node<String> node =
        jsonDataDecoderBuilderContext.decode(input, TypeToken.of(new Safe<Node<String>>() {}));

    assertThat(node.t).isEqualTo("hello");
    assertThat(node.node.t).isEqualTo("world");
    assertThat(node.node.node).isEqualTo(null);
  }

  @Test
  public void genericObjectDecoder_ShouldCorrectlyCaptureDifferentTypeParameters()
      throws IOException {
    Map<Class<?>, ObjectDecoder<?>> objectDecoders = new HashMap<>();
    objectDecoders.put(Node.class, new NodeObjectDecoder<>());
    JsonDataDecoderBuilderContext jsonDataDecoderBuilderContext =
        new JsonDataDecoderBuilderContext(objectDecoders);

    String json = "{\"t\":\"hello\", \"node\": {\"t\":\"world\", \"node\": null}}";
    InputStream input = new ByteArrayInputStream(json.getBytes(UTF_8));
    Node<String> strNode =
        jsonDataDecoderBuilderContext.decode(input, TypeToken.of(new Safe<Node<String>>() {}));

    assertThat(strNode.t).isEqualTo("hello");
    assertThat(strNode.node.t).isEqualTo("world");
    assertThat(strNode.node.node).isEqualTo(null);

    json = "{\"t\":true, \"node\": {\"t\":false, \"node\": null}}";
    input = new ByteArrayInputStream(json.getBytes(UTF_8));
    Node<Boolean> booleanNode =
        jsonDataDecoderBuilderContext.decode(input, TypeToken.of(new Safe<Node<Boolean>>() {}));
    assertThat(booleanNode.t).isEqualTo(true);
    assertThat(booleanNode.node.t).isEqualTo(false);
    assertThat(booleanNode.node.node).isEqualTo(null);
  }

  static class Primitives {
    int i;
    short s;
    long l;
    double d;
    float f;
    boolean b;
    char c;

    Primitives(int i, short s, long l, double d, float f, boolean b, char c) {
      this.i = i;
      this.s = s;
      this.l = l;
      this.d = d;
      this.f = f;
      this.b = b;
      this.c = c;
    }
  }

  static class PrimitivesObjectDecoder implements ObjectDecoder<Primitives> {

    @NonNull
    @Override
    public TypeCreator<Primitives> decode(@NonNull ObjectDecoderContext<Primitives> ctx) {
      FieldRef.Primitive<Integer> iField = ctx.decodeInteger(FieldDescriptor.of("i"));
      FieldRef.Primitive<Short> sField = ctx.decodeShort(FieldDescriptor.of("s"));
      FieldRef.Primitive<Long> lField = ctx.decodeLong(FieldDescriptor.of("l"));
      FieldRef.Primitive<Double> dField = ctx.decodeDouble(FieldDescriptor.of("d"));
      FieldRef.Primitive<Float> fField = ctx.decodeFloat(FieldDescriptor.of("f"));
      FieldRef.Primitive<Boolean> bField = ctx.decodeBoolean(FieldDescriptor.of("b"));
      FieldRef.Primitive<Character> cField = ctx.decodeChar(FieldDescriptor.of("c"));

      return (creationCtx ->
          new Primitives(
              creationCtx.getInteger(iField),
              creationCtx.getShort(sField),
              creationCtx.getLong(lField),
              creationCtx.getDouble(dField),
              creationCtx.getFloat(fField),
              creationCtx.getBoolean(bField),
              creationCtx.getChar(cField)));
    }
  }

  @Test
  public void primitives_areDecodeCorrectly() throws IOException {
    Map<Class<?>, ObjectDecoder<?>> objectDecoders = new HashMap<>();
    objectDecoders.put(Primitives.class, new PrimitivesObjectDecoder());
    JsonDataDecoderBuilderContext jsonDataDecoderBuilderContext =
        new JsonDataDecoderBuilderContext(objectDecoders);

    String json = "{\"i\":1, \"s\":1, \"l\":1, \"d\":1.1, \"f\":1.1, \"b\":true, \"c\":\"c\"}";
    InputStream input = new ByteArrayInputStream(json.getBytes(UTF_8));
    Primitives primitives =
        jsonDataDecoderBuilderContext.decode(input, TypeToken.of(Primitives.class));

    assertThat(primitives.i).isEqualTo(1);
    assertThat(primitives.s).isEqualTo(1);
    assertThat(primitives.l).isEqualTo(1);
    assertThat(primitives.d).isEqualTo(1.1);
    assertThat(primitives.f).isEqualTo((float) 1.1);
    assertThat(primitives.b).isEqualTo(true);
    assertThat(primitives.c).isEqualTo('c');
  }

  static class SingleValues {
    Integer i;
    Short s;
    Long l;
    Double d;
    Float f;
    Boolean b;
    Character c;
    String str;

    SingleValues(
        Integer i, Short s, Long l, Double d, Float f, Boolean b, Character c, String str) {
      this.i = i;
      this.s = s;
      this.l = l;
      this.d = d;
      this.f = f;
      this.b = b;
      this.c = c;
      this.str = str;
    }
  }

  static class SingleValuesObjectDecoder implements ObjectDecoder<SingleValues> {

    @NonNull
    @Override
    public TypeCreator<SingleValues> decode(@NonNull ObjectDecoderContext<SingleValues> ctx) {
      FieldRef.Boxed<Integer> iField =
          ctx.decode(FieldDescriptor.of("i"), TypeToken.of(Integer.class));
      FieldRef.Boxed<Short> sField = ctx.decode(FieldDescriptor.of("s"), TypeToken.of(Short.class));
      FieldRef.Boxed<Long> lField = ctx.decode(FieldDescriptor.of("l"), TypeToken.of(Long.class));
      FieldRef.Boxed<Double> dField =
          ctx.decode(FieldDescriptor.of("d"), TypeToken.of(Double.class));
      FieldRef.Boxed<Float> fField = ctx.decode(FieldDescriptor.of("f"), TypeToken.of(Float.class));
      FieldRef.Boxed<Boolean> bField =
          ctx.decode(FieldDescriptor.of("b"), TypeToken.of(Boolean.class));
      FieldRef.Boxed<Character> cField =
          ctx.decode(FieldDescriptor.of("c"), TypeToken.of(Character.class));
      FieldRef.Boxed<String> strField =
          ctx.decode(FieldDescriptor.of("str"), TypeToken.of(String.class));

      return (creationCtx ->
          new SingleValues(
              creationCtx.get(iField),
              creationCtx.get(sField),
              creationCtx.get(lField),
              creationCtx.get(dField),
              creationCtx.get(fField),
              creationCtx.get(bField),
              creationCtx.get(cField),
              creationCtx.get(strField)));
    }
  }

  @Test
  public void singleValues_areDecodeCorrectly() throws IOException {
    Map<Class<?>, ObjectDecoder<?>> objectDecoders = new HashMap<>();
    objectDecoders.put(SingleValues.class, new SingleValuesObjectDecoder());
    JsonDataDecoderBuilderContext jsonDataDecoderBuilderContext =
        new JsonDataDecoderBuilderContext(objectDecoders);

    String json =
        "{\"i\":1, \"s\":1, \"l\":1, \"d\":1.1, \"f\":1.1, \"b\":true, \"c\":\"c\", \"str\": \"str\"}";
    InputStream input = new ByteArrayInputStream(json.getBytes(UTF_8));
    SingleValues singleValues =
        jsonDataDecoderBuilderContext.decode(input, TypeToken.of(SingleValues.class));

    assertThat(singleValues.i).isEqualTo(1);
    assertThat(singleValues.s).isEqualTo(1);
    assertThat(singleValues.l).isEqualTo(1);
    assertThat(singleValues.d).isEqualTo(1.1);
    assertThat(singleValues.f).isEqualTo((float) 1.1);
    assertThat(singleValues.b).isEqualTo(true);
    assertThat(singleValues.c).isEqualTo('c');
    assertThat(singleValues.str).isEqualTo("str");
  }

  static class ArrFoo {
    int i;

    ArrFoo(int i) {
      this.i = i;
    }
  }

  static class ArrFooObjectDecoder implements ObjectDecoder<ArrFoo> {
    @NonNull
    @Override
    public TypeCreator<ArrFoo> decode(@NonNull ObjectDecoderContext<ArrFoo> ctx) {
      FieldRef.Primitive<Integer> iField = ctx.decodeInteger(FieldDescriptor.of("i"));
      return (creationCtx -> new ArrFoo(creationCtx.getInteger(iField)));
    }
  }

  @Test
  public void arrayOfObjects_shouldBeDecodedCorrectly() throws IOException {
    Map<Class<?>, ObjectDecoder<?>> objectDecoders = new HashMap<>();
    objectDecoders.put(ArrFoo.class, new ArrFooObjectDecoder());
    JsonDataDecoderBuilderContext jsonDataDecoderBuilderContext =
        new JsonDataDecoderBuilderContext(objectDecoders);

    String json = "[{\"i\":0}, {\"i\":1}]";
    InputStream input = new ByteArrayInputStream(json.getBytes(UTF_8));
    ArrFoo[] arrFoo =
        jsonDataDecoderBuilderContext.decode(input, TypeToken.of(new Safe<ArrFoo[]>() {}));
    assertThat(arrFoo[0].i).isEqualTo(0);
    assertThat(arrFoo[1].i).isEqualTo(1);
  }

  @Test
  public void arrayOfSingleValue_shouldBeDecodedCorrectly() throws IOException {
    Map<Class<?>, ObjectDecoder<?>> objectDecoders = new HashMap<>();
    JsonDataDecoderBuilderContext jsonDataDecoderBuilderContext =
        new JsonDataDecoderBuilderContext(objectDecoders);

    String json = "[\"a\",\"b\"]";
    InputStream input = new ByteArrayInputStream(json.getBytes(UTF_8));
    String[] arr =
        jsonDataDecoderBuilderContext.decode(input, TypeToken.of(new Safe<String[]>() {}));
    assertThat(arr).isEqualTo(new String[] {"a", "b"});
  }

  @Test
  public void arrayOfPrimitive_shouldBeDecodedCorrectly() throws IOException {
    Map<Class<?>, ObjectDecoder<?>> objectDecoders = new HashMap<>();
    JsonDataDecoderBuilderContext jsonDataDecoderBuilderContext =
        new JsonDataDecoderBuilderContext(objectDecoders);

    String json = "[0, 1]";
    InputStream input = new ByteArrayInputStream(json.getBytes(UTF_8));
    int[] arr = jsonDataDecoderBuilderContext.decode(input, TypeToken.of(new Safe<int[]>() {}));
    assertThat(arr).isEqualTo(new int[] {0, 1});
  }

  @Test
  public void arrayOfArray_shouldBeDecodedCorrectly() throws IOException {
    Map<Class<?>, ObjectDecoder<?>> objectDecoders = new HashMap<>();
    JsonDataDecoderBuilderContext jsonDataDecoderBuilderContext =
        new JsonDataDecoderBuilderContext(objectDecoders);

    String json = "[[0, 1], [0, 1]]";
    InputStream input = new ByteArrayInputStream(json.getBytes(UTF_8));
    int[][] arr = jsonDataDecoderBuilderContext.decode(input, TypeToken.of(new Safe<int[][]>() {}));
    assertThat(arr).isEqualTo(new int[][] {{0, 1}, {0, 1}});
  }

  @Test
  public void emptyArray_shouldBeDecodedWithEmptyArray() throws IOException {
    Map<Class<?>, ObjectDecoder<?>> objectDecoders = new HashMap<>();
    JsonDataDecoderBuilderContext jsonDataDecoderBuilderContext =
        new JsonDataDecoderBuilderContext(objectDecoders);

    String json = "[]";
    InputStream input = new ByteArrayInputStream(json.getBytes(UTF_8));
    int[] arr = jsonDataDecoderBuilderContext.decode(input, TypeToken.of(new Safe<int[]>() {}));
    assertThat(arr).isEqualTo(new int[0]);
  }

  @Test
  public void arrayOfArrayWithEmptyArray_shouldBeDecodedCorrectly() throws IOException {
    Map<Class<?>, ObjectDecoder<?>> objectDecoders = new HashMap<>();
    JsonDataDecoderBuilderContext jsonDataDecoderBuilderContext =
        new JsonDataDecoderBuilderContext(objectDecoders);

    String json = "[[],[1]]";
    InputStream input = new ByteArrayInputStream(json.getBytes(UTF_8));
    int[][] arr = jsonDataDecoderBuilderContext.decode(input, TypeToken.of(new Safe<int[][]>() {}));
    assertThat(arr).isEqualTo(new int[][] {{}, {1}});
  }

  @Test
  public void arrayOfArrayWithEmptyArrayNon_shouldBeDecodedCorrectly() throws IOException {
    Map<Class<?>, ObjectDecoder<?>> objectDecoders = new HashMap<>();
    JsonDataDecoderBuilderContext jsonDataDecoderBuilderContext =
        new JsonDataDecoderBuilderContext(objectDecoders);

    String json = "[[],[\"1\"]]";
    InputStream input = new ByteArrayInputStream(json.getBytes(UTF_8));
    String[][] arr =
        jsonDataDecoderBuilderContext.decode(input, TypeToken.of(new Safe<String[][]>() {}));
    assertThat(arr).isEqualTo(new String[][] {{}, {"1"}});
  }

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
  public void decodeAbstractMapClass_shouldThrowException() throws IOException {
    Map<Class<?>, ObjectDecoder<?>> objectDecoders = new HashMap<>();
    JsonDataDecoderBuilderContext jsonDataDecoderBuilderContext =
        new JsonDataDecoderBuilderContext(objectDecoders);

    String json = "{\"1\": \"1\", \"2\": \"2\"}";
    InputStream input = new ByteArrayInputStream(json.getBytes(UTF_8));

    assertThrows(
        "Abstract class and an interface can not be initialized",
        IllegalArgumentException.class,
        () -> {
          jsonDataDecoderBuilderContext.decode(
              input, TypeToken.of(new Safe<Map<String, String>>() {}));
        });
  }
}
