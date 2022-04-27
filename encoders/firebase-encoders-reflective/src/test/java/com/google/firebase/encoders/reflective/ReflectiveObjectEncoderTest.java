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
import com.google.firebase.encoders.DataEncoder;
import com.google.firebase.encoders.annotations.Encodable;
import com.google.firebase.encoders.json.JsonDataEncoderBuilder;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ReflectiveObjectEncoderTest {
  static class Foo {
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
  public void test() {
    DataEncoder encoder =
        new JsonDataEncoderBuilder()
            .registerFallbackEncoder(ReflectiveObjectEncoder.DEFAULT)
            .build();

    String result = encoder.encode(new Foo());

    assertThat(result)
        .isEqualTo(
            "{\"string\":\"hello\",\"member\":{\"bool\":false},\"map\":{\"key\":22},\"bool\":false}");
  }
}
