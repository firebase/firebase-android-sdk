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

package com.google.firebase.encoders.json;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.Lists;
import com.google.firebase.encoders.DataEncoder;
import com.google.firebase.encoders.EncodingException;
import com.google.firebase.encoders.ObjectEncoder;
import com.google.firebase.encoders.ObjectEncoderContext;
import com.google.firebase.encoders.ValueEncoder;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.TimeZone;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link JsonValueObjectEncoderContext} */
@RunWith(AndroidJUnit4.class)
public class JsonValueObjectEncoderContextTest {

  private static final Calendar CALENDAR = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
  // Mon Nov 04 2019 16:45:32.212
  private static long TIME_IN_MILLIS = 1572885932212L;
  private static final String FORMATTED_TIME = "2019-11-04T16:45:32.212Z";

  static {
    CALENDAR.setTimeInMillis(TIME_IN_MILLIS);
  }

  static class DummyClass {
    static final DummyClass INSTANCE = new DummyClass();
  }

  static class InnerDummyClass {
    static final InnerDummyClass INSTANCE = new InnerDummyClass();
  }

  static class InnerInnerDummyClass {
    static final InnerInnerDummyClass INSTANCE = new InnerInnerDummyClass();
  }

  @Test
  public void testRegisterOverride() throws IOException {
    ObjectEncoder<DummyClass> objectEncoder =
        (o, ctx) -> {
          ctx.add("Inner", InnerDummyClass.INSTANCE);
        };
    ObjectEncoder<InnerDummyClass> innerObjectEncoder =
        (o, ctx) -> {
          ctx.add("Name", "innerClass");
        };
    ValueEncoder<InnerDummyClass> innerValueEncoder =
        (o, ctx) -> {
          ctx.add(1234);
        };

    String result =
        new JsonDataEncoderBuilder()
            .registerEncoder(DummyClass.class, objectEncoder)
            .registerEncoder(InnerDummyClass.class, innerObjectEncoder)
            .registerEncoder(InnerDummyClass.class, innerValueEncoder)
            .build()
            .encode(DummyClass.INSTANCE);

    assertThat(result).isEqualTo("{\"Inner\":1234}");

    // Invert the logic
    result =
        new JsonDataEncoderBuilder()
            .registerEncoder(DummyClass.class, objectEncoder)
            .registerEncoder(InnerDummyClass.class, innerValueEncoder)
            .registerEncoder(InnerDummyClass.class, innerObjectEncoder)
            .build()
            .encode(DummyClass.INSTANCE);

    assertThat(result).isEqualTo("{\"Inner\":{\"Name\":\"innerClass\"}}");
  }

  @Test
  public void testEncodingPrimitiveTypes() throws IOException {
    ObjectEncoder<DummyClass> objectEncoder =
        (o, ctx) -> {
          ctx.add("String", "string")
              .add("int", 2)
              .add("long", 42L)
              .add("double", 2.2d)
              .add("boolean", false)
              .add("null", null);
        };

    String result =
        new JsonDataEncoderBuilder()
            .registerEncoder(DummyClass.class, objectEncoder)
            .build()
            .encode(DummyClass.INSTANCE);

    assertThat(result)
        .isEqualTo(
            "{\"String\":\"string\",\"int\":2,\"long\":42,\"double\":2.2,\"boolean\":false,\"null\":null}");
  }

  @Test
  public void testEncodingTimestamp() throws IOException {
    ObjectEncoder<DummyClass> objectEncoder =
        (o, ctx) -> {
          ctx.add("Timestamp", CALENDAR.getTime());
        };

    String result =
        new JsonDataEncoderBuilder()
            .registerEncoder(DummyClass.class, objectEncoder)
            .build()
            .encode(DummyClass.INSTANCE);

    assertThat(result).isEqualTo(String.format("{\"Timestamp\":\"%s\"}", FORMATTED_TIME));
  }

  @Test
  public void testEncodingArrayPrimitives() throws IOException {
    ObjectEncoder<DummyClass> objectEncoder =
        (o, ctx) -> {
          ctx.add("String", new String[] {"string1", "string2"})
              .add("int", new int[] {1, 2})
              .add("long", new long[] {3L, 4L})
              .add("double", new double[] {1.1d, 2.2d})
              .add("boolean", new boolean[] {true, false})
              .add("null", new String[] {null, null});
        };

    String result =
        new JsonDataEncoderBuilder()
            .registerEncoder(DummyClass.class, objectEncoder)
            .build()
            .encode(DummyClass.INSTANCE);

    assertThat(result)
        .isEqualTo(
            String.format(
                "{\"String\":%s,\"int\":%s,\"long\":%s,\"double\":%s,\"boolean\":%s,\"null\":%s}",
                "[\"string1\",\"string2\"]",
                "[1,2]",
                "[3,4]",
                "[1.1,2.2]",
                "[true,false]",
                "[null,null]"));
  }

  @Test
  public void testEncodingNumbers() {
    ObjectEncoder<DummyClass> objectEncoder =
        (o, ctx) -> ctx.add("Number", new Number[] {1, 2473946328429347632L, 0.0d});

    String result =
        new JsonDataEncoderBuilder()
            .registerEncoder(DummyClass.class, objectEncoder)
            .build()
            .encode(DummyClass.INSTANCE);

    assertThat(result).isEqualTo(String.format("{\"Number\":%s}", "[1,2473946328429347632,0.0]"));
  }

  @Test
  public void testEncodingLongs() {
    ObjectEncoder<DummyClass> objectEncoder =
        (o, ctx) -> ctx.add("long", new long[] {1L, 2473946328429347632L});

    String result =
        new JsonDataEncoderBuilder()
            .registerEncoder(DummyClass.class, objectEncoder)
            .build()
            .encode(DummyClass.INSTANCE);

    assertThat(result).isEqualTo(String.format("{\"long\":%s}", "[1,2473946328429347632]"));
  }

  enum MyEnum {
    VALUE_1,
    VALUE_2
  }

  @Test
  public void testEncodingEnum_withNoCustomEncoder() {
    String result =
        new JsonDataEncoderBuilder().build().encode(new MyEnum[] {MyEnum.VALUE_1, MyEnum.VALUE_2});
    assertThat(result).isEqualTo("[\"VALUE_1\",\"VALUE_2\"]");
  }

  @Test
  public void testEncodingEnum_withCustomEncoder() {
    ValueEncoder<MyEnum> encoder = (o, ctx) -> ctx.add(o.name().toLowerCase());
    String result =
        new JsonDataEncoderBuilder()
            .registerEncoder(MyEnum.class, encoder)
            .build()
            .encode(new MyEnum[] {MyEnum.VALUE_1, MyEnum.VALUE_2});
    assertThat(result).isEqualTo("[\"value_1\",\"value_2\"]");
  }

  @Test
  public void testEncodingCollection() throws IOException {
    ObjectEncoder<InnerDummyClass> anotherObjectEncoder =
        (o, ctx) -> {
          ctx.add("Name", "innerClass");
        };
    ObjectEncoder<DummyClass> objectEncoder =
        (o, ctx) -> {
          ctx.add("String", Lists.newArrayList("string1", "string2"))
              .add(
                  "Objects",
                  Lists.newArrayList(InnerDummyClass.INSTANCE, InnerDummyClass.INSTANCE));
        };

    String result =
        new JsonDataEncoderBuilder()
            .registerEncoder(DummyClass.class, objectEncoder)
            .registerEncoder(InnerDummyClass.class, anotherObjectEncoder)
            .build()
            .encode(DummyClass.INSTANCE);

    assertThat(result)
        .isEqualTo(
            String.format(
                "{\"String\":%s,\"Objects\":%s}",
                "[\"string1\",\"string2\"]",
                "[{\"Name\":\"innerClass\"},{\"Name\":\"innerClass\"}]"));
  }

  @Test
  public void testEncodingMap_withStringKey_shouldSucceed() {
    ObjectEncoder<DummyClass> objectEncoder =
        (o, ctx) -> {
          ctx.add("map", Collections.singletonMap("key", true));
        };

    String result =
        new JsonDataEncoderBuilder()
            .registerEncoder(DummyClass.class, objectEncoder)
            .build()
            .encode(DummyClass.INSTANCE);

    assertThat(result).isEqualTo(String.format("{\"map\":%s}", "{\"key\":true}"));
  }

  @Test
  public void testEncodingMap_withNonStringKey_shouldFail() {
    ObjectEncoder<DummyClass> objectEncoder =
        (o, ctx) -> {
          ctx.add("map", Collections.singletonMap(1L, true));
        };

    DataEncoder encoder =
        new JsonDataEncoderBuilder().registerEncoder(DummyClass.class, objectEncoder).build();

    assertThrows(
        "Only String keys are currently supported in maps",
        EncodingException.class,
        () -> encoder.encode(DummyClass.INSTANCE));
  }

  @Test
  public void testEncodingBytes() throws IOException {
    ObjectEncoder<DummyClass> objectEncoder =
        (o, ctx) -> {
          ctx.add("Bytes", "My {custom} value.".getBytes(Charset.forName("UTF-8")));
        };

    String result =
        new JsonDataEncoderBuilder()
            .registerEncoder(DummyClass.class, objectEncoder)
            .build()
            .encode(DummyClass.INSTANCE);

    assertThat(result).isEqualTo(String.format("{\"Bytes\":%s}", "\"TXkge2N1c3RvbX0gdmFsdWUu\""));
  }

  @Test
  public void testEncodingCollectionBoxedPrimitives() throws IOException {
    ObjectEncoder<DummyClass> objectEncoder =
        (o, ctx) -> {
          ctx.add("Integer", Lists.newArrayList(1, 2, 3))
              .add("Double", Lists.newArrayList(1.1, 2.2, 3.3))
              .add("Boolean", Lists.newArrayList(true, false));
          ;
        };

    String result =
        new JsonDataEncoderBuilder()
            .registerEncoder(DummyClass.class, objectEncoder)
            .build()
            .encode(DummyClass.INSTANCE);

    assertThat(result)
        .isEqualTo(
            String.format(
                "{\"Integer\":%s,\"Double\":%s,\"Boolean\":%s}",
                "[1,2,3]", "[1.1,2.2,3.3]", "[true,false]"));
  }

  @Test
  public void testEncodingNestedCollection() throws IOException {
    ObjectEncoder<InnerDummyClass> anotherObjectEncoder =
        (o, ctx) -> {
          ctx.add("Name", "innerClass");
        };
    ObjectEncoder<DummyClass> objectEncoder =
        (o, ctx) -> {
          ctx.add("String", Lists.newArrayList("string1", "string2"))
              .add(
                  "Objects",
                  Lists.newArrayList(
                      Lists.newArrayList(InnerDummyClass.INSTANCE),
                      Lists.newArrayList(InnerDummyClass.INSTANCE)));
        };

    String result =
        new JsonDataEncoderBuilder()
            .registerEncoder(DummyClass.class, objectEncoder)
            .registerEncoder(InnerDummyClass.class, anotherObjectEncoder)
            .build()
            .encode(DummyClass.INSTANCE);

    assertThat(result)
        .isEqualTo(
            String.format(
                "{\"String\":%s,\"Objects\":%s}",
                "[\"string1\",\"string2\"]",
                "[[{\"Name\":\"innerClass\"}],[{\"Name\":\"innerClass\"}]]"));
  }

  @Test
  public void testEncodingComplexTypes_InnerEncoder() throws IOException {
    ObjectEncoder<DummyClass> objectEncoder =
        (o, ctx) -> {
          ctx.add("String", "string")
              .add("Null", null)
              .add("InnerObject", InnerDummyClass.INSTANCE);
        };
    ObjectEncoder<InnerDummyClass> anotherObjectEncoder =
        (o, ctx) -> {
          ctx.add("Name", "innerClass")
              .add("Numbers", new int[] {12, 35})
              .add("Timestamp", CALENDAR.getTime());
        };

    String result =
        new JsonDataEncoderBuilder()
            .registerEncoder(DummyClass.class, objectEncoder)
            .registerEncoder(InnerDummyClass.class, anotherObjectEncoder)
            .build()
            .encode(DummyClass.INSTANCE);

    String innerObject =
        String.format(
            "{\"Name\":\"innerClass\",\"Numbers\":[12,35],\"Timestamp\":\"%s\"}", FORMATTED_TIME);
    String outerObject =
        String.format("{\"String\":\"string\",\"Null\":null,\"InnerObject\":%s}", innerObject);

    assertThat(result).isEqualTo(outerObject);
  }

  @Test
  public void testEncodingComplexTypes_InnerExtendedEncoder() throws IOException {
    ObjectEncoder<DummyClass> objectEncoder =
        (o, ctx) -> {
          ctx.add("String", "string")
              .add("Null", null)
              .add("InnerObject", InnerDummyClass.INSTANCE);
        };
    ValueEncoder<InnerDummyClass> anotherEncoder =
        (o, ctx) -> {
          ctx.add("A very complex value");
        };

    String result =
        new JsonDataEncoderBuilder()
            .registerEncoder(DummyClass.class, objectEncoder)
            .registerEncoder(InnerDummyClass.class, anotherEncoder)
            .build()
            .encode(DummyClass.INSTANCE);

    String innerObject = "\"A very complex value\"";
    String outerObject =
        String.format("{\"String\":\"string\",\"Null\":null,\"InnerObject\":%s}", innerObject);

    assertThat(result).isEqualTo(outerObject);
  }

  @Test
  public void testMissingEncoder() throws IOException {
    DataEncoder dataEncoder = new JsonDataEncoderBuilder().build();
    assertThrows(EncodingException.class, () -> dataEncoder.encode(DummyClass.INSTANCE));
  }

  @Test
  public void testEncoderError() throws IOException {
    ObjectEncoder<DummyClass> objectEncoder = (o, ctx) -> ctx.add("name", "value");
    Writer mockWriter = mock(Writer.class);
    doThrow(IOException.class).when(mockWriter).write(any(String.class));

    assertThrows(
        IOException.class,
        () ->
            new JsonDataEncoderBuilder()
                .registerEncoder(DummyClass.class, objectEncoder)
                .build()
                .encode(DummyClass.INSTANCE, mockWriter));
  }

  @Test
  public void testNested_whenUsedCorrectly_shouldProduceNestedJson() {
    ObjectEncoder<DummyClass> objectEncoder =
        (o, ctx) -> {
          ctx.add("name", "value");
          ctx.nested("nested1").add("key1", "value1");
          ctx.add("after1", true);
          ctx.nested("nested2").add("key2", "value2");
          ctx.add("after2", true);
        };

    String result =
        new JsonDataEncoderBuilder()
            .registerEncoder(DummyClass.class, objectEncoder)
            .build()
            .encode(DummyClass.INSTANCE);

    assertThat(result)
        .isEqualTo(
            "{\"name\":\"value\",\"nested1\":{\"key1\":\"value1\"},"
                + "\"after1\":true,\"nested2\":{\"key2\":\"value2\"},\"after2\":true}");
  }

  @Test
  public void testTwiceNested_whenUsedCorrectly_shouldProduceNestedJson() {
    ObjectEncoder<DummyClass> objectEncoder =
        (o, ctx) -> {
          ctx.add("name", "value");
          ctx.nested("nested1").add("key1", "value1").nested("nested2");
          ctx.add("after1", true);
        };

    String result =
        new JsonDataEncoderBuilder()
            .registerEncoder(DummyClass.class, objectEncoder)
            .build()
            .encode(DummyClass.INSTANCE);

    assertThat(result)
        .isEqualTo(
            "{\"name\":\"value\",\"nested1\":{\"key1\":\"value1\",\"nested2\":{}},\"after1\":true}");
  }

  @Test
  public void testNested_whenUsedAfterParent_shouldThrow() {
    ObjectEncoder<DummyClass> objectEncoder =
        (o, ctx) -> {
          ObjectEncoderContext nested = ctx.nested("nested1");
          ctx.add("after1", true);
          nested.add("hello", "world");
        };

    DataEncoder encoder =
        new JsonDataEncoderBuilder().registerEncoder(DummyClass.class, objectEncoder).build();

    assertThrows(IllegalStateException.class, () -> encoder.encode(DummyClass.INSTANCE));
  }

  @Test
  public void testInline_shouldProduceValuesInParentObject() {
    ObjectEncoder<DummyClass> objectEncoder =
        (o, ctx) -> ctx.add("name", "value").inline(InnerDummyClass.INSTANCE).add("after1", true);
    ObjectEncoder<InnerDummyClass> innerEncoder = (o, ctx) -> ctx.add("inner", "class");

    String result =
        new JsonDataEncoderBuilder()
            .registerEncoder(DummyClass.class, objectEncoder)
            .registerEncoder(InnerDummyClass.class, innerEncoder)
            .build()
            .encode(DummyClass.INSTANCE);

    assertThat(result).isEqualTo("{\"name\":\"value\",\"inner\":\"class\",\"after1\":true}");
  }

  @Test
  public void testNestedInline_shouldProduceValuesInParentObject() {
    ObjectEncoder<DummyClass> objectEncoder =
        (o, ctx) -> ctx.add("name", "value").inline(InnerDummyClass.INSTANCE).add("after1", true);
    ObjectEncoder<InnerDummyClass> innerEncoder =
        (o, ctx) -> ctx.inline(InnerInnerDummyClass.INSTANCE);

    ObjectEncoder<InnerInnerDummyClass> innerInnerEncoder = (o, ctx) -> ctx.add("key", true);

    String result =
        new JsonDataEncoderBuilder()
            .registerEncoder(DummyClass.class, objectEncoder)
            .registerEncoder(InnerDummyClass.class, innerEncoder)
            .registerEncoder(InnerInnerDummyClass.class, innerInnerEncoder)
            .build()
            .encode(DummyClass.INSTANCE);

    assertThat(result).isEqualTo("{\"name\":\"value\",\"key\":true,\"after1\":true}");
  }

  @Test
  public void testInline_withNull_shouldThrow() {
    ObjectEncoder<DummyClass> objectEncoder = (o, ctx) -> ctx.inline(null);

    DataEncoder encoder =
        new JsonDataEncoderBuilder().registerEncoder(DummyClass.class, objectEncoder).build();
    assertThrows(EncodingException.class, () -> encoder.encode(DummyClass.INSTANCE));
  }

  @Test
  public void testInline_withLong_shouldThrow() {
    ObjectEncoder<DummyClass> objectEncoder = (o, ctx) -> ctx.inline(Long.valueOf(100));

    DataEncoder encoder =
        new JsonDataEncoderBuilder().registerEncoder(DummyClass.class, objectEncoder).build();
    assertThrows(EncodingException.class, () -> encoder.encode(DummyClass.INSTANCE));
  }

  @Test
  public void testInline_withArray_shouldThrow() {
    ObjectEncoder<DummyClass> objectEncoder = (o, ctx) -> ctx.inline(new int[] {1, 2, 3});

    DataEncoder encoder =
        new JsonDataEncoderBuilder().registerEncoder(DummyClass.class, objectEncoder).build();
    assertThrows(EncodingException.class, () -> encoder.encode(DummyClass.INSTANCE));
  }

  @Test
  public void testInline_withDate_shouldThrow() {
    ObjectEncoder<DummyClass> objectEncoder = (o, ctx) -> ctx.inline(new Date());

    DataEncoder encoder =
        new JsonDataEncoderBuilder().registerEncoder(DummyClass.class, objectEncoder).build();
    assertThrows(EncodingException.class, () -> encoder.encode(DummyClass.INSTANCE));
  }
}
