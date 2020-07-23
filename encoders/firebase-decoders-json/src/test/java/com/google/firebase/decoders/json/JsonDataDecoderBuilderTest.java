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
public class JsonDataDecoderBuilderTest {

  static class MyFoo {
    String s;
    MySubFoo subFoo;

    MyFoo(String s, MySubFoo subFoo) {
      this.s = s;
      this.subFoo = subFoo;
    }
  }

  static class MySubFoo {
    int i;

    MySubFoo(int i) {
      this.i = i;
    }
  }

  static class MyFooObjectDecoder implements ObjectDecoder<MyFoo> {

    @NonNull
    @Override
    public TypeCreator<MyFoo> decode(@NonNull ObjectDecoderContext<MyFoo> ctx) {
      FieldRef.Boxed<String> sField =
          ctx.decode(FieldDescriptor.of("str"), TypeToken.of(String.class));
      FieldRef.Boxed<MySubFoo> subFooField =
          ctx.decodeInline(
              (TypeToken.ClassToken<MySubFoo>) TypeToken.of(new Safe<MySubFoo>() {}),
              new MySubFooObjectDecoder());
      return (creationCtx -> new MyFoo(creationCtx.get(sField), creationCtx.get(subFooField)));
    }
  }

  static class MySubFooObjectDecoder implements ObjectDecoder<MySubFoo> {

    @NonNull
    @Override
    public TypeCreator<MySubFoo> decode(@NonNull ObjectDecoderContext<MySubFoo> ctx) {
      FieldRef.Primitive<Integer> iField = ctx.decodeInteger(FieldDescriptor.of("i"));
      return (creationCtx -> new MySubFoo(creationCtx.getInteger(iField)));
    }
  }

  @Test
  public void decodeInline_shouldDecodeCorrectly() throws IOException {
    JsonDataDecoderBuilder builder = new JsonDataDecoderBuilder();
    DataDecoder decoder =
        builder
            .register(MyFoo.class, new MyFooObjectDecoder())
            .register(MySubFoo.class, new MySubFooObjectDecoder())
            .build();

    String json = "{\"str\": \"str\", \"i\": 1}";
    InputStream input = new ByteArrayInputStream(json.getBytes(UTF_8));
    MyFoo myFoo = decoder.decode(input, TypeToken.of(MyFoo.class));
    assertThat(myFoo.s).isEqualTo("str");
    assertThat(myFoo.subFoo.i).isEqualTo(1);
  }
}
