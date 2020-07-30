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

import android.icu.text.SimpleDateFormat;
import androidx.annotation.NonNull;
import com.google.firebase.decoders.DataDecoder;
import com.google.firebase.decoders.FieldRef;
import com.google.firebase.decoders.ObjectDecoder;
import com.google.firebase.decoders.ObjectDecoderContext;
import com.google.firebase.decoders.Safe;
import com.google.firebase.decoders.TypeCreator;
import com.google.firebase.decoders.TypeToken;
import com.google.firebase.decoders.ValueDecoder;
import com.google.firebase.decoders.ValueDecoderContext;
import com.google.firebase.encoders.FieldDescriptor;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.Date;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ValueDecoderTest {

  static class DateValueDecoder implements ValueDecoder<Date> {

    @NonNull
    @Override
    public TypeCreator<Date> decode(@NonNull ValueDecoderContext ctx) {
      FieldRef.Boxed<String> fieldRef = ctx.decodeString();
      return (creationCtx) -> {
        String val = creationCtx.get(fieldRef);
        try {
          return new SimpleDateFormat("dd/MM/yyyy").parse(val);
        } catch (ParseException e) {
          throw new RuntimeException("Date Literal(" + val + ") with Wrong Format");
        }
      };
    }
  }

  @Test
  public void valueObjectInArray_shouldDecodedCorrectly() throws IOException, ParseException {
    DataDecoder decoder =
        new JsonDataDecoderBuilder().register(Date.class, new DateValueDecoder()).build();
    String json = "[\"29/10/1996\",\"01/01/2013\"]";
    InputStream input = new ByteArrayInputStream(json.getBytes(UTF_8));
    Date[] results = decoder.decode(input, TypeToken.of(new Safe<Date[]>() {}));

    SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy");
    assertThat(results)
        .isEqualTo(new Date[] {format.parse("29/10/1996"), format.parse("01/01/2013")});
  }

  static class Foo {
    Date date;

    Foo(Date date) {
      this.date = date;
    }
  }

  static class FooObjectDecoder implements ObjectDecoder<Foo> {

    @NonNull
    @Override
    public TypeCreator<Foo> decode(@NonNull ObjectDecoderContext<Foo> ctx) {
      FieldRef.Boxed<Date> ref = ctx.decode(FieldDescriptor.of("date"), TypeToken.of(Date.class));
      return (creationCtx) -> new Foo(creationCtx.get(ref));
    }
  }

  @Test
  public void valueObjectInObject_shouldDecodedCorrectly() throws IOException, ParseException {
    DataDecoder decoder =
        new JsonDataDecoderBuilder()
            .register(Foo.class, new FooObjectDecoder())
            .register(Date.class, new DateValueDecoder())
            .build();
    String json = "{\"date\": \"01/01/2013\"}";
    InputStream input = new ByteArrayInputStream(json.getBytes(UTF_8));
    Foo foo = decoder.decode(input, TypeToken.of(Foo.class));

    SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy");
    assertThat(foo.date).isEqualTo(format.parse("01/01/2013"));
  }
}
