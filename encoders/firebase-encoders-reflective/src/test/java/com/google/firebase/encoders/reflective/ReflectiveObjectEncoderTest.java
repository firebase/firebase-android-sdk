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

package com.google.firebase.encoders.reflective;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.encoders.DataEncoder;
import com.google.firebase.encoders.annotations.Alias;
import com.google.firebase.encoders.annotations.Encodable;
import com.google.firebase.encoders.json.JsonDataEncoderBuilder;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ReflectiveObjectEncoderTest {
  static class Foo {
    @Encodable.Field(name = "hello")
    public String getString() {
      return "hello";
    }

    public Member getMember() {
      return new Member();
    }

    @Encodable.Field(inline = true)
    public Member getInlineMember() {
      return new Member();
    }

    @Encodable.Ignore
    public String getIgnored() {
      return "ignored";
    }

    public Map<String, Number> getMap() {
      return Collections.singletonMap("key", BigDecimal.valueOf(22));
    }
  }

  static class Member {
    public boolean getBool() {
      return false;
    }
  }

  @Test
  public void test() throws JsonProcessingException {
    DataEncoder encoder =
        new JsonDataEncoderBuilder()
            .registerFallbackEncoder(ReflectiveObjectEncoder.DEFAULT)
            .build();

    String result = encoder.encode(new Foo());

    ObjectMapper mapper = new ObjectMapper();

    assertThat(mapper.reader().readTree(result))
        .isEqualTo(
            mapper
                .reader()
                .readTree(
                    "{\"hello\":\"hello\",\"member\":{\"bool\":false},\"map\":{\"key\":22},\"bool\":false}"));
  }

  @Target({ElementType.FIELD, ElementType.METHOD})
  @Retention(RetentionPolicy.RUNTIME)
  @Alias(Encodable.Ignore.class)
  public @interface Exclude {}

  static class IgnoreFoo {
    @Encodable.Ignore
    public String getExclude() {
      return "exclude";
    }
  }

  static class ExcludeFoo {
    @Exclude
    public String getExclude() {
      return "exclude";
    }
  }

  @Test
  public void getterMethodWithAliasOfEncodableIgnore_ShouldNotBeEncoded() {
    DataEncoder encoder =
        new JsonDataEncoderBuilder()
            .registerFallbackEncoder(ReflectiveObjectEncoder.DEFAULT)
            .build();

    String ignoreFooResult = encoder.encode(new IgnoreFoo());
    String excludeFooResult = encoder.encode(new ExcludeFoo());
    assertThat(excludeFooResult).isEqualTo(ignoreFooResult);
  }

  static class EncodableFieldFoo {
    @Encodable.Field(name = "newName")
    public String getStr() {
      return "str";
    }
  }

  @Target({ElementType.FIELD, ElementType.METHOD})
  @Retention(RetentionPolicy.RUNTIME)
  @Alias(Encodable.Field.class)
  public @interface PropertyName {
    @Alias.Property("name")
    String value();
  }

  static class PropertyNameFoo {
    @PropertyName("newName")
    public String getStr() {
      return "str";
    }
  }

  @Test
  public void getterMethodWithAliasOfEncodableField_ShouldEncodedCorrectly() {
    DataEncoder encoder =
        new JsonDataEncoderBuilder()
            .registerFallbackEncoder(ReflectiveObjectEncoder.DEFAULT)
            .build();

    String encodableFieldFooResult = encoder.encode(new EncodableFieldFoo());
    String propertyNameFooResult = encoder.encode(new PropertyNameFoo());

    assertThat(propertyNameFooResult).isEqualTo(encodableFieldFooResult);
  }

  @Target({ElementType.FIELD, ElementType.METHOD})
  @Retention(RetentionPolicy.RUNTIME)
  @Alias(Encodable.Field.class)
  public @interface Inline {
    @Alias.Property("inline")
    boolean value();
  }

  static class EncodableInlineFoo {
    @Encodable.Field(inline = true)
    public SubFoo getStr() {
      return new SubFoo();
    }
  }

  static class InlineFoo {
    @Inline(true)
    public SubFoo getStr() {
      return new SubFoo();
    }
  }

  static class SubFoo {
    public String getStr() {
      return "str";
    }
  }

  @Test
  public void getterMethodWithAliasOfEncodableFieldInline_ShouldEncodedCorrectly() {
    DataEncoder encoder =
        new JsonDataEncoderBuilder()
            .registerFallbackEncoder(ReflectiveObjectEncoder.DEFAULT)
            .build();

    String encodableInlineFooResult = encoder.encode(new EncodableInlineFoo());
    String inlineFooResult = encoder.encode(new InlineFoo());

    assertThat(inlineFooResult).isEqualTo(encodableInlineFooResult);
  }
}
