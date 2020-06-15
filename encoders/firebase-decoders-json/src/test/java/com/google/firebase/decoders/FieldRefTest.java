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

package com.google.firebase.decoders;

import static org.junit.Assert.assertThrows;

import com.google.firebase.encoders.FieldDescriptor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FieldRefTest {
  @Test
  public void primitivesTypeTokenUsedToCreateBoxedFieldRef_shouldThrowILLegalArgumentException() {
    assertThrows(
        "FieldRef.Boxed<T> can only be used to hold non-primitive type.",
        IllegalArgumentException.class,
        () -> {
          FieldRef.boxed(FieldDescriptor.of("test"), TypeToken.of(int.class));
        });
  }

  static class Foo {}

  @Test
  public void objectTypeTokenUsedToCreatePrimitiveFieldRef_shouldThrowILLegalArgumentException() {
    assertThrows(
        "FieldRef.Primitive<T> can only be used to hold primitive type.",
        IllegalArgumentException.class,
        () -> {
          FieldRef.primitive(FieldDescriptor.of("test"), TypeToken.of(Foo.class));
        });
  }
}
