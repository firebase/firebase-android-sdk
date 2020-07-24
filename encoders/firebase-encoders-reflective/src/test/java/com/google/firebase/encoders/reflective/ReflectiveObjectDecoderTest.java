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

package com.google.firebase.encoders.reflective;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.firebase.decoders.DataDecoder;
import com.google.firebase.decoders.Safe;
import com.google.firebase.decoders.TypeToken;
import com.google.firebase.decoders.json.JsonDataDecoderBuilder;
import com.google.firebase.encoders.annotations.Encodable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ReflectiveObjectDecoderTest {
  private static DataDecoder decoder =
      new JsonDataDecoderBuilder().registerFallBackDecoder(ReflectiveObjectDecoder.DEFAULT).build();

  static class FieldFoo {
    public int i;

    public FieldFoo() {}
  }

  @Test
  public <T> void publicFieldObject_ShouldDecodeCorrectly() throws IOException {
    String json = "{\"i\": 1}";
    InputStream input = new ByteArrayInputStream(json.getBytes(UTF_8));
    FieldFoo foo = decoder.decode(input, TypeToken.of(FieldFoo.class));

    assertThat(foo.i).isEqualTo(1);
  }

  static class SetterFoo {
    private int i;

    private void setI(int i) {
      this.i = i;
    }

    public SetterFoo() {}
  }

  @Test
  public <T> void privateSetter_ShouldDecodeCorrectly() throws IOException {
    String json = "{\"i\": 1}";
    InputStream input = new ByteArrayInputStream(json.getBytes(UTF_8));
    SetterFoo foo = decoder.decode(input, TypeToken.of(SetterFoo.class));

    assertThat(foo.i).isEqualTo(1);
  }

  static class AnnotationFoo {
    private String str;

    @Encodable.Field(name = "newName")
    public void setStr(String str) {
      this.str = str;
    }

    public AnnotationFoo() {}
  }

  @Test
  public <T> void decodeWithReplacedFieldName_ShouldDecodeCorrectly() throws IOException {
    String json = "{\"newName\": \"newName\"}";
    InputStream input = new ByteArrayInputStream(json.getBytes(UTF_8));
    AnnotationFoo foo = decoder.decode(input, TypeToken.of(AnnotationFoo.class));

    assertThat(foo.str).isEqualTo("newName");
  }

  static class Foo<T> {
    public int a;
    public boolean b;
    public T t;
    public SubFoo<String> subFoo;

    public Foo() {}
  }

  static class SubFoo<T> {
    public int a;
    public boolean b;
    public T t;
    public T s;

    public SubFoo() {}
  }

  @Test
  public <T> void nestedGenericType_ShouldDecodeCorrectly() throws IOException {
    String json =
        "{\"a\":1, \"b\":true, \"t\":\"str\", \"subFoo\": {\"a\":1, \"b\":true, \"t\":\"str\"}}";
    InputStream input = new ByteArrayInputStream(json.getBytes(UTF_8));
    Foo<String> foo = decoder.decode(input, TypeToken.of(new Safe<Foo<String>>() {}));

    assertThat(foo.a).isEqualTo(1);
    assertThat(foo.b).isEqualTo(true);
    assertThat(foo.t).isEqualTo("str");
    assertThat(foo.subFoo.a).isEqualTo(1);
    assertThat(foo.subFoo.b).isEqualTo(true);
    assertThat(foo.subFoo.t).isEqualTo("str");
  }

  static class MyFoo {
    private String s;
    private MySubFoo subFoo;

    public void setS(String s) {
      this.s = s;
    }

    @Encodable.Field(inline = true)
    public void setSubFoo(MySubFoo subFoo) {
      this.subFoo = subFoo;
    }

    public MyFoo() {}
  }

  static class MySubFoo {
    private int i;

    public void setI(int i) {
      this.i = i;
    }

    public MySubFoo() {}
  }

  @Test
  public void decodeInline_shouldDecodeCorrectly() throws IOException {
    String json = "{\"s\": \"str\", \"i\": 1}";
    InputStream input = new ByteArrayInputStream(json.getBytes(UTF_8));
    MyFoo myFoo = decoder.decode(input, TypeToken.of(new Safe<MyFoo>() {}));
    assertThat(myFoo.s).isEqualTo("str");
    assertThat(myFoo.subFoo.i).isEqualTo(1);
  }
}
