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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TypeTokenTest {
  static class Foo {}

  // Primitive type
  @Test
  public void primitiveType_typeIsCorrectlyCaptured() {
    TypeToken<Integer> integerTypeToken = TypeToken.of(int.class);
    assertThat(integerTypeToken).isInstanceOf(TypeToken.ClassToken.class);
    TypeToken.ClassToken<Integer> intToken = (TypeToken.ClassToken<Integer>) integerTypeToken;
    assertThat(intToken.getRawType()).isEqualTo(int.class);

    TypeToken<Boolean> booleanTypeToken = TypeToken.of(boolean.class);
    assertThat(booleanTypeToken).isInstanceOf(TypeToken.ClassToken.class);
    TypeToken.ClassToken<Boolean> booleanToken = (TypeToken.ClassToken<Boolean>) booleanTypeToken;
    assertThat(booleanToken.getRawType()).isEqualTo(boolean.class);
  }

  // Array type
  @Test
  public void generalArrayTypeWithSafe_componentTypeIsCorrectlyCaptured() {
    TypeToken<Foo[]> typeToken = TypeToken.of(new Safe<Foo[]>() {});
    assertThat(typeToken).isInstanceOf(TypeToken.ArrayToken.class);
    TypeToken.ArrayToken<Foo[]> arrayToken = (TypeToken.ArrayToken<Foo[]>) typeToken;
    TypeToken<?> componentTypeToken = arrayToken.getComponentType();
    assertThat(componentTypeToken).isInstanceOf(TypeToken.ClassToken.class);
    TypeToken.ClassToken<Foo> componentToken = (TypeToken.ClassToken<Foo>) componentTypeToken;
    assertThat(componentToken.getRawType()).isEqualTo(Foo.class);
  }

  @Test
  public void generalArrayTypeWithoutSafe_componentTypeIsCorrectlyCaptured() {
    TypeToken<Foo[]> typeToken = TypeToken.of(Foo[].class);
    assertThat(typeToken).isInstanceOf(TypeToken.ArrayToken.class);
    TypeToken.ArrayToken<Foo[]> arrayToken = (TypeToken.ArrayToken<Foo[]>) typeToken;
    TypeToken<?> componentTypeToken = arrayToken.getComponentType();
    assertThat(componentTypeToken).isInstanceOf(TypeToken.ClassToken.class);
    TypeToken.ClassToken<Foo> componentToken = (TypeToken.ClassToken<Foo>) componentTypeToken;
    assertThat(componentToken.getRawType()).isEqualTo(Foo.class);
  }

  @Test
  public void genericArrayType_rawTypeIsCorrectlyCaptured() {
    TypeToken<List<String>[]> typeToken = TypeToken.of(new Safe<List<String>[]>() {});
    assertThat(typeToken).isInstanceOf(TypeToken.ArrayToken.class);
    TypeToken.ArrayToken<List<String>[]> arrayToken =
        (TypeToken.ArrayToken<List<String>[]>) typeToken;
    TypeToken<List<String>> componentType = (TypeToken<List<String>>) arrayToken.getComponentType();
    assertThat(componentType).isInstanceOf(TypeToken.ClassToken.class);
    TypeToken.ClassToken<List<String>> componentClassType =
        (TypeToken.ClassToken<List<String>>) componentType;
    assertThat(componentClassType.getRawType()).isEqualTo(List.class);
    TypeToken<String> argumentType =
        ((TypeToken.ClassToken<List<String>>) componentType).getTypeArguments().at(0);
    assertThat(argumentType).isInstanceOf(TypeToken.ClassToken.class);
    TypeToken.ClassToken<String> argumentClassType = (TypeToken.ClassToken<String>) argumentType;
    assertThat(argumentClassType.getRawType()).isEqualTo(String.class);
  }

  // Plain Class Type
  @Test
  public void plainClassTypeWithSafe_rawTypeIsCorrectlyCaptured() {
    TypeToken<Foo> typeToken = TypeToken.of(new Safe<Foo>() {});
    assertThat(typeToken).isInstanceOf(TypeToken.ClassToken.class);
    TypeToken.ClassToken<Foo> typeClassToken = (TypeToken.ClassToken<Foo>) typeToken;
    assertThat(typeClassToken.getRawType()).isEqualTo(Foo.class);
  }

  @Test
  public void plainClassTypeWithoutSafe_rawTypeIsCorrectlyCaptured() {
    TypeToken<Foo> typeToken = TypeToken.of(Foo.class);
    assertThat(typeToken).isInstanceOf(TypeToken.ClassToken.class);
    TypeToken.ClassToken<Foo> typeClassToken = (TypeToken.ClassToken<Foo>) typeToken;
    assertThat(typeClassToken.getRawType()).isEqualTo(Foo.class);
  }

  // Generic Type
  @Test
  public void genericType_actualTypeParametersAreCorrectlyCaptured() {
    TypeToken<Map<String, Foo>> mapTypeToken = TypeToken.of(new Safe<Map<String, Foo>>() {});
    assertThat(mapTypeToken).isInstanceOf(TypeToken.ClassToken.class);
    TypeToken.ClassToken<Map<String, Foo>> mapClassToken =
        (TypeToken.ClassToken<Map<String, Foo>>) mapTypeToken;
    TypeTokenContainer typeTokenContainer = mapClassToken.getTypeArguments();
    TypeToken<String> firstArgumentToken = typeTokenContainer.at(0);
    TypeToken<Foo> secondArgumentTypeToken = typeTokenContainer.at(1);
    assertThat(firstArgumentToken).isInstanceOf(TypeToken.ClassToken.class);
    assertThat(secondArgumentTypeToken).isInstanceOf(TypeToken.ClassToken.class);
    TypeToken.ClassToken<String> firstArgumentClassToken =
        (TypeToken.ClassToken<String>) firstArgumentToken;
    TypeToken.ClassToken<Foo> secondArgumentClassToken =
        (TypeToken.ClassToken<Foo>) secondArgumentTypeToken;
    assertThat(firstArgumentClassToken.getRawType()).isEqualTo(String.class);
    assertThat(secondArgumentClassToken.getRawType()).isEqualTo(Foo.class);
  }

  @Test
  public void nestedGenericType_actualTypeParametersAreCorrectlyCaptured() {
    TypeToken<List<List<String>>> typeToken = TypeToken.of(new Safe<List<List<String>>>() {});
    assertThat(typeToken).isInstanceOf(TypeToken.ClassToken.class);
    TypeToken.ClassToken<List<List<String>>> typeClassToken =
        (TypeToken.ClassToken<List<List<String>>>) typeToken;
    TypeToken<List<String>> componentToken = typeClassToken.getTypeArguments().at(0);
    assertThat(componentToken).isInstanceOf(TypeToken.ClassToken.class);
    TypeToken.ClassToken<List<String>> componentClassToken =
        (TypeToken.ClassToken<List<String>>) componentToken;
    assertThat(componentClassToken.getRawType()).isEqualTo(List.class);
    TypeToken<String> innerComponentToken = componentClassToken.getTypeArguments().at(0);
    assertThat(innerComponentToken).isInstanceOf(TypeToken.ClassToken.class);
    TypeToken.ClassToken<String> innerComponentClassToken =
        (TypeToken.ClassToken<String>) innerComponentToken;
    assertThat(innerComponentClassToken.getRawType()).isEqualTo(String.class);
  }

  // Bounded Wildcard Type
  @Test
  public void boundedWildcardTypeWithExtend_actualTypeParameterIsCastedToUpperBound() {
    TypeToken<List<? extends Number>> typeToken =
        TypeToken.of(new Safe<List<? extends Number>>() {});
    assertThat(typeToken).isInstanceOf(TypeToken.ClassToken.class);
    TypeToken.ClassToken<List<? extends Number>> typeClassToken =
        (TypeToken.ClassToken<List<? extends Number>>) typeToken;
    TypeToken<Number> componentType = typeClassToken.getTypeArguments().at(0);
    assertThat(componentType).isInstanceOf(TypeToken.ClassToken.class);
    TypeToken.ClassToken<Number> componentClassType = (TypeToken.ClassToken<Number>) componentType;
    assertThat(componentClassType.getRawType()).isEqualTo(Number.class);
  }

  @Test
  public void boundedWildcardTypeWithSuper_notSupported() {
    assertThrows(
        "<? super T> is not supported",
        IllegalArgumentException.class,
        () -> {
          TypeToken.of(new Safe<List<? super Foo>>() {});
        });
  }
}
