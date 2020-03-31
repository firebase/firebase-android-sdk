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

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.firebase.encoders.DataEncoder;
import com.google.firebase.encoders.ObjectEncoderContext;
import com.google.firebase.encoders.ValueEncoderContext;
import java.util.Collections;
import java.util.HashMap;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class JsonDataEncoderBuilderTests {
  static class Foo {}

  @Test
  public void configureWith_shouldCorrectlyRegisterObjectEncoder() {
    DataEncoder encoder =
        new JsonDataEncoderBuilder()
            .configureWith(
                cfg ->
                    cfg.registerEncoder(
                        Foo.class,
                        (Foo s, ObjectEncoderContext ctx) -> {
                          ctx.add("foo", "value");
                        }))
            .build();

    assertThat(encoder.encode(new Foo())).isEqualTo("{\"foo\":\"value\"}");
  }

  @Test
  public void configureWith_shouldCorrectlyRegisterValueEncoder() {
    DataEncoder encoder =
        new JsonDataEncoderBuilder()
            .configureWith(
                cfg ->
                    cfg.registerEncoder(
                        Foo.class,
                        (Foo s, ValueEncoderContext ctx) -> {
                          ctx.add("value");
                        }))
            .build();

    assertThat(encoder.encode(Collections.singletonMap("foo", new Foo())))
        .isEqualTo("{\"foo\":\"value\"}");
  }

  @Test
  public void ignoreNullValues_shouldCorrectlyEncodeObjectIgnoringNullObjects() {
    DataEncoder encoder =
        new JsonDataEncoderBuilder()
            .configureWith(
                cfg ->
                    cfg.registerEncoder(
                        Foo.class,
                        (Foo s, ObjectEncoderContext ctx) -> {
                          ctx.add("foo", "value");
                          ctx.add("bar", null);
                        }))
            .ignoreNullValues(true)
            .build();

    assertThat(encoder.encode(new Foo())).isEqualTo("{\"foo\":\"value\"}");
  }

  @Test
  public void ignoreNullValues_shouldCorrectlyEncodeValueIgnoringNullObjects() {
    DataEncoder encoder =
        new JsonDataEncoderBuilder()
            .configureWith(
                cfg ->
                    cfg.registerEncoder(
                        Foo.class,
                        (Foo s, ValueEncoderContext ctx) -> {
                          ctx.add("value");
                        }))
            .ignoreNullValues(true)
            .build();

    HashMap<String, Foo> fooMap = new HashMap<>();
    fooMap.put("foo", new Foo());
    fooMap.put("bar", null);
    assertThat(encoder.encode(fooMap)).isEqualTo("{\"foo\":\"value\"}");
  }
}
