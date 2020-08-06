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
import com.google.firebase.decoders.DataDecoder;
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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class JsonDataDecoderBuilderContextDefaultValueTest {
  @Test
  public void nullValueInBoxedNumericArray_shouldKeptAsNull() throws IOException {
    DataDecoder decoder = new JsonDataDecoderBuilder().build();

    String json = "[0, null]";
    InputStream input = new ByteArrayInputStream(json.getBytes(UTF_8));
    Integer[] intArr = decoder.decode(input, TypeToken.of(new Safe<Integer[]>() {}));
    assertThat(intArr).isEqualTo(new Integer[] {0, null});
  }

  @Test
  public void nullValueInPrimitiveNumericArray_shouldThrowException() throws IOException {
    DataDecoder decoder = new JsonDataDecoderBuilder().build();

    String json = "[null]";
    InputStream input = new ByteArrayInputStream(json.getBytes(UTF_8));
    assertThrows(
        "primitive element should not have null value",
        Exception.class,
        () -> {
          decoder.decode(input, TypeToken.of(new Safe<int[]>() {}));
        });
  }

  static class Foo {
    int i;
    short s;
    long l;
    double d;
    float f;
    boolean b;
    char c;
    int[] ai;
    Integer ii;
    Short ss;
    Long ll;
    Double dd;
    Float ff;
    Boolean bb;
    Character cc;
    String str;
    Object obj;
    Integer[] aii;

    Foo(
        int i,
        short s,
        long l,
        double d,
        float f,
        boolean b,
        char c,
        int[] ai,
        Integer ii,
        Short ss,
        Long ll,
        Double dd,
        Float ff,
        Boolean bb,
        Character cc,
        String str,
        Object obj,
        Integer[] aii) {
      this.i = i;
      this.s = s;
      this.l = l;
      this.d = d;
      this.f = f;
      this.b = b;
      this.c = c;
      this.ai = ai;
      this.ii = ii;
      this.ss = ss;
      this.ll = ll;
      this.dd = dd;
      this.ff = ff;
      this.bb = bb;
      this.cc = cc;
      this.str = str;
      this.obj = obj;
      this.aii = aii;
    }
  }

  static class FooObjectDecoder implements ObjectDecoder<Foo> {

    @NonNull
    @Override
    public TypeCreator<Foo> decode(@NonNull ObjectDecoderContext<Foo> ctx) {
      FieldRef.Primitive<Integer> iField = ctx.decodeInteger(FieldDescriptor.of("i"));
      FieldRef.Primitive<Short> sField = ctx.decodeShort(FieldDescriptor.of("s"));
      FieldRef.Primitive<Long> lField = ctx.decodeLong(FieldDescriptor.of("l"));
      FieldRef.Primitive<Double> dField = ctx.decodeDouble(FieldDescriptor.of("d"));
      FieldRef.Primitive<Float> fField = ctx.decodeFloat(FieldDescriptor.of("f"));
      FieldRef.Primitive<Boolean> bField = ctx.decodeBoolean(FieldDescriptor.of("b"));
      FieldRef.Primitive<Character> cField = ctx.decodeChar(FieldDescriptor.of("c"));
      FieldRef.Boxed<int[]> aiField =
          ctx.decode(FieldDescriptor.of("ai"), TypeToken.of(int[].class));
      FieldRef.Boxed<Integer> iiField =
          ctx.decode(FieldDescriptor.of("ii"), TypeToken.of(Integer.class));
      FieldRef.Boxed<Short> ssField =
          ctx.decode(FieldDescriptor.of("ss"), TypeToken.of(Short.class));
      FieldRef.Boxed<Long> llField = ctx.decode(FieldDescriptor.of("ll"), TypeToken.of(Long.class));
      FieldRef.Boxed<Double> ddField =
          ctx.decode(FieldDescriptor.of("dd"), TypeToken.of(Double.class));
      FieldRef.Boxed<Float> ffField =
          ctx.decode(FieldDescriptor.of("ff"), TypeToken.of(Float.class));
      FieldRef.Boxed<Boolean> bbField =
          ctx.decode(FieldDescriptor.of("bb"), TypeToken.of(Boolean.class));
      FieldRef.Boxed<Character> ccField =
          ctx.decode(FieldDescriptor.of("cc"), TypeToken.of(Character.class));
      FieldRef.Boxed<String> strField =
          ctx.decode(FieldDescriptor.of("str"), TypeToken.of(String.class));
      FieldRef.Boxed<Object> objField =
          ctx.decode(FieldDescriptor.of("obj"), TypeToken.of(Object.class));
      FieldRef.Boxed<Integer[]> aiiField =
          ctx.decode(FieldDescriptor.of("aii"), TypeToken.of(Integer[].class));

      return (creationCtx ->
          new Foo(
              creationCtx.getInteger(iField),
              creationCtx.getShort(sField),
              creationCtx.getLong(lField),
              creationCtx.getDouble(dField),
              creationCtx.getFloat(fField),
              creationCtx.getBoolean(bField),
              creationCtx.getChar(cField),
              creationCtx.get(aiField),
              creationCtx.get(iiField),
              creationCtx.get(ssField),
              creationCtx.get(llField),
              creationCtx.get(ddField),
              creationCtx.get(ffField),
              creationCtx.get(bbField),
              creationCtx.get(ccField),
              creationCtx.get(strField),
              creationCtx.get(objField),
              creationCtx.get(aiiField)));
    }
  }

  @Test
  public void jsonInputWithNullValues_DefaultValuesAreDecodeCorrectly() throws IOException {
    DataDecoder decoder =
        new JsonDataDecoderBuilder().register(Foo.class, new FooObjectDecoder()).build();

    String json =
        "{\"s\":null, \"l\":null, \"d\":null, \"f\":null, \"b\":null, \"c\":null, \"ai\":null, \"ii\":null, \"ss\":null, \"ll\":null, \"dd\":null, \"ff\":null, \"bb\":null, \"cc\":null, \"str\":null, \"obj\":null, \"aii\":null}";
    InputStream input = new ByteArrayInputStream(json.getBytes(UTF_8));
    Foo foo = decoder.decode(input, TypeToken.of(Foo.class));

    assertThat(foo.i).isEqualTo(0);
    assertThat(foo.s).isEqualTo(0);
    assertThat(foo.l).isEqualTo(0);
    assertThat(foo.d).isEqualTo(Double.valueOf(0));
    assertThat(foo.f).isEqualTo(Float.valueOf(0));
    assertThat(foo.b).isEqualTo(false);
    assertThat(foo.c).isEqualTo(Character.MIN_VALUE);
    assertThat(foo.ai).isEqualTo(new int[0]);
    assertThat(foo.ii).isEqualTo(0);
    assertThat(foo.ss).isEqualTo(0);
    assertThat(foo.ll).isEqualTo(0);
    assertThat(foo.dd).isEqualTo(Double.valueOf(0));
    assertThat(foo.ff).isEqualTo(Float.valueOf(0));
    assertThat(foo.bb).isEqualTo(false);
    assertThat(foo.cc).isEqualTo(Character.MIN_VALUE);
    assertThat(foo.str).isEqualTo("");
    assertThat(foo.obj).isEqualTo(null);
    assertThat(foo.aii).isEqualTo(new Integer[0]);
  }

  @Test
  public void jsonInputWithMissingEntries_DefaultValuesAreDecodeCorrectly() throws IOException {
    DataDecoder decoder =
        new JsonDataDecoderBuilder().register(Foo.class, new FooObjectDecoder()).build();

    String json = "{}";
    InputStream input = new ByteArrayInputStream(json.getBytes(UTF_8));
    Foo foo = decoder.decode(input, TypeToken.of(Foo.class));

    assertThat(foo.i).isEqualTo(0);
    assertThat(foo.s).isEqualTo(0);
    assertThat(foo.l).isEqualTo(0);
    assertThat(foo.d).isEqualTo(Double.valueOf(0));
    assertThat(foo.f).isEqualTo(Float.valueOf(0));
    assertThat(foo.b).isEqualTo(false);
    assertThat(foo.c).isEqualTo(Character.MIN_VALUE);
    assertThat(foo.ai).isEqualTo(new int[0]);
    assertThat(foo.ii).isEqualTo(0);
    assertThat(foo.ss).isEqualTo(0);
    assertThat(foo.ll).isEqualTo(0);
    assertThat(foo.dd).isEqualTo(Double.valueOf(0));
    assertThat(foo.ff).isEqualTo(Float.valueOf(0));
    assertThat(foo.bb).isEqualTo(false);
    assertThat(foo.cc).isEqualTo(Character.MIN_VALUE);
    assertThat(foo.str).isEqualTo("");
    assertThat(foo.obj).isEqualTo(null);
    assertThat(foo.aii).isEqualTo(new Integer[0]);
  }
}
