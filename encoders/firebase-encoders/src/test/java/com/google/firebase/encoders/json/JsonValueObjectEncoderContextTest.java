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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.Lists;
import com.google.firebase.encoders.DataEncoder;
import com.google.firebase.encoders.EncodingException;
import com.google.firebase.encoders.ObjectEncoder;
import com.google.firebase.encoders.ValueEncoder;
import java.io.IOException;
import java.io.Writer;
import java.util.Calendar;
import java.util.TimeZone;
import org.junit.Assert;
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
    static DummyClass INSTANCE = new DummyClass();
  }

  static class InnerDummyClass {
    static InnerDummyClass INSTANCE = new InnerDummyClass();
  }

  @Test
  public void testEncodingPrimitiveTypes() throws IOException, EncodingException {
    ObjectEncoder<DummyClass> objectEncoder =
        (o, ctx) -> {
          ctx.add("String", "string")
              .add("Integer", 2)
              .add("Double", 2.2d)
              .add("Boolean", false)
              .add("Null", null);
        };

    String result =
        new JsonDataEncoderBuilder()
            .registerEncoder(DummyClass.class, objectEncoder)
            .build()
            .encode(DummyClass.INSTANCE);

    assertThat(result)
        .isEqualTo(
            "{\"String\":\"string\",\"Integer\":2,\"Double\":2.2,\"Boolean\":false,\"Null\":null}");
  }

  @Test
  public void testEncodingTimestamp() throws IOException, EncodingException {
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
  public void testEncodingArrayPrimitives() throws IOException, EncodingException {
    ObjectEncoder<DummyClass> objectEncoder =
        (o, ctx) -> {
          ctx.add("String", new String[] {"string1", "string2"})
              .add("Integer", new int[] {1, 2})
              .add("Double", new double[] {1.1d, 2.2d})
              .add("Boolean", new boolean[] {true, false})
              .add("Null", new String[] {null, null});
        };

    String result =
        new JsonDataEncoderBuilder()
            .registerEncoder(DummyClass.class, objectEncoder)
            .build()
            .encode(DummyClass.INSTANCE);

    assertThat(result)
        .isEqualTo(
            String.format(
                "{\"String\":%s,\"Integer\":%s,\"Double\":%s,\"Boolean\":%s,\"Null\":%s}",
                "[\"string1\",\"string2\"]", "[1,2]", "[1.1,2.2]", "[true,false]", "[null,null]"));
  }

  @Test
  public void testEncodingCollection() throws IOException, EncodingException {
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
  public void testEncodingCollectionBoxedPrimitives() throws IOException, EncodingException {
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
  public void testEncodingNestedCollection() throws IOException, EncodingException {
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
  public void testEncodingComplexTypes_InnerEncoder() throws IOException, EncodingException {
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
  public void testEncodingComplexTypes_InnerExtendedEncoder()
      throws IOException, EncodingException {
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
  public void testMissingEncoder() throws IOException, EncodingException {
    DataEncoder dataEncoder = new JsonDataEncoderBuilder().build();
    Assert.assertThrows(EncodingException.class, () -> dataEncoder.encode(DummyClass.INSTANCE));
  }

  @Test
  public void testEncoderError() throws IOException, EncodingException {
    ObjectEncoder<DummyClass> objectEncoder = (o, ctx) -> ctx.add("name", "value");
    Writer mockWriter = mock(Writer.class);
    doThrow(IOException.class).when(mockWriter).write(any(String.class));

    Assert.assertThrows(
        IOException.class,
        () ->
            new JsonDataEncoderBuilder()
                .registerEncoder(DummyClass.class, objectEncoder)
                .build()
                .encode(DummyClass.INSTANCE, mockWriter));
  }
}
