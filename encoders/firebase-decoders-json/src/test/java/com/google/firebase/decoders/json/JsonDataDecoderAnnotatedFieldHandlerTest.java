package com.google.firebase.decoders.json;// Copyright 2020 Google LLC
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

 import static com.google.common.truth.Truth.assertThat;
 import static java.nio.charset.StandardCharsets.UTF_8;

 import androidx.annotation.NonNull;
 import com.google.firebase.decoders.AnnotatedFieldHandler;
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
 import java.lang.annotation.Annotation;
 import java.lang.annotation.ElementType;
 import java.lang.annotation.Retention;
 import java.lang.annotation.RetentionPolicy;
 import java.lang.annotation.Target;
 import java.util.HashMap;
 import java.util.Map;
 import org.junit.Test;
 import org.junit.runner.RunWith;
 import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class JsonDataDecoderAnnotatedFieldHandlerTest {

  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.METHOD, ElementType.FIELD})
  private @interface Default {
    String value();

    Default INSTANCE = new DefaultImpl();

    class DefaultImpl implements Default {
      @Override
      public Class<? extends Annotation> annotationType() {
        return Default.class;
      }

      @Override
      public String value() {
        return "default";
      }

      @Override
      public boolean equals(Object obj) {
        return super.equals(obj);
      }

      @Override
      public int hashCode() {
        return super.hashCode();
      }
    }
  }

  private static class Foo {
    @Default("default")
    String str;

    Foo(String str) {
      this.str = str;
    }
  }

  static class FooObjectDecoder implements ObjectDecoder<Foo> {
    @NonNull
    @Override
    public TypeCreator<Foo> decode(@NonNull ObjectDecoderContext<Foo> ctx) {
      FieldDescriptor strFieldDescriptor =
          FieldDescriptor.builder("str").withProperty(Default.INSTANCE).build();
      FieldRef.Boxed<String> strField = ctx.decode(strFieldDescriptor,
 TypeToken.of(String.class));
      return (creationCtx -> new Foo(creationCtx.get(strField)));
    }
  }

  @Test
  public void customizedAnnotation_shouldProcessCorrectly() throws IOException {
    Map<Class<?>, ObjectDecoder<?>> objectDecoders = new HashMap<>();
    Map<Class<?>, AnnotatedFieldHandler<?>> fieldHandlers = new HashMap<>();

    objectDecoders.put(Foo.class, new FooObjectDecoder());
    fieldHandlers.put(
        Default.class,
        (annotation, fieldDecodedResult, type) -> {
          if (fieldDecodedResult == null) {
            if (type.equals(String.class)) {
              return ((Default) annotation).value();
            }
          }
          return fieldDecodedResult;
        });

    JsonDataDecoderContext jsonDataDecoderContext =
        new JsonDataDecoderContext(objectDecoders, fieldHandlers);

    String json = "{\"str\":null}";
    InputStream input = new ByteArrayInputStream(json.getBytes(UTF_8));
    Foo foo = jsonDataDecoderContext.decode(input, TypeToken.of(new Safe<Foo>() {}));
    assertThat(foo.str).isEqualTo("default");
  }

  @Test
  public void customizedAnnotationRegisteredWithBuilder_shouldProcessCorrectly()
      throws IOException {
    DataDecoder decoder =
        new JsonDataDecoderBuilder()
            .register(Foo.class, new FooObjectDecoder())
            .register(
                Default.class,
                (annotation, fieldDecodedResult, type) -> {
                  if (fieldDecodedResult == null) {
                    if (type.equals(String.class)) {
                      return annotation.value();
                    }
                  }
                  return fieldDecodedResult;
                })
            .build();

    String json = "{\"str\":null}";
    InputStream input = new ByteArrayInputStream(json.getBytes(UTF_8));
    Foo foo = decoder.decode(input, TypeToken.of(new Safe<Foo>() {}));
    assertThat(foo.str).isEqualTo("default");
  }
}
