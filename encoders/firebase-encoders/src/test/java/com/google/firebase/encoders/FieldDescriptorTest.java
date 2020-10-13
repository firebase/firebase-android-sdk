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

package com.google.firebase.encoders;

import static com.google.common.truth.Truth.assertThat;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FieldDescriptorTest {

  private static final String FIELD_NAME_1 = "name1";
  private static final String FIELD_NAME_2 = "name2";

  @Test
  public void getName_shouldReturnCorrectly() {
    FieldDescriptor field = FieldDescriptor.builder(FIELD_NAME_1).build();

    assertThat(field.getName()).isEqualTo(FIELD_NAME_1);
  }

  @Test
  public void fieldDescriptor_shouldEqualItself() {
    FieldDescriptor field1 = FieldDescriptor.builder(FIELD_NAME_1).build();

    assertThat(field1.equals(field1)).isTrue();
    assertThat(field1.hashCode()).isEqualTo(field1.hashCode());
  }

  @Test
  public void equalFieldDescriptors_shouldBeEqual() {
    FieldDescriptor field1 = FieldDescriptor.builder(FIELD_NAME_1).build();
    FieldDescriptor field2 = FieldDescriptor.builder(FIELD_NAME_1).build();

    assertThat(field1).isEqualTo(field2);
    assertThat(field1.hashCode()).isEqualTo(field2.hashCode());
  }

  @Test
  public void unequalFieldDescriptors_shouldBeUnequal() {
    FieldDescriptor field1 = FieldDescriptor.builder(FIELD_NAME_1).build();
    FieldDescriptor field2 = FieldDescriptor.builder(FIELD_NAME_2).build();

    assertThat(field1).isNotEqualTo(field2);
    assertThat(field1.hashCode()).isNotEqualTo(field2.hashCode());
  }

  @Test
  public void equalFieldDescriptors_withAnnotations_shouldBeEqual() {
    FieldDescriptor field1 =
        FieldDescriptor.builder(FIELD_NAME_1).withProperty(OverrideImpl.INSTANCE).build();
    FieldDescriptor field2 =
        FieldDescriptor.builder(FIELD_NAME_1).withProperty(OverrideImpl.INSTANCE).build();

    assertThat(field1).isEqualTo(field2);
    assertThat(field1.hashCode()).isEqualTo(field2.hashCode());
  }

  @Test
  public void unequalFieldDescriptors_withAnnotations_shouldBeUnequal() {
    FieldDescriptor field1 =
        FieldDescriptor.builder(FIELD_NAME_1).withProperty(OverrideImpl.INSTANCE).build();
    FieldDescriptor field2 =
        FieldDescriptor.builder(FIELD_NAME_1).withProperty(MyAnnotation.INSTANCE).build();

    assertThat(field1).isNotEqualTo(field2);
    assertThat(field1.hashCode()).isNotEqualTo(field2.hashCode());
  }

  @Test
  public void withProperty_shouldCorrectlyPopulateProperties() {
    FieldDescriptor field =
        FieldDescriptor.builder("hello")
            .withProperty(OverrideImpl.INSTANCE)
            .withProperty(MyAnnotation.INSTANCE)
            .build();

    assertThat(field.getProperty(Override.class)).isSameInstanceAs(OverrideImpl.INSTANCE);
    assertThat(field.getProperty(MyAnnotation.class)).isSameInstanceAs(MyAnnotation.INSTANCE);
  }

  @Retention(RetentionPolicy.RUNTIME)
  private @interface MyAnnotation {
    MyAnnotation INSTANCE = new MyAnnotationImpl();

    class MyAnnotationImpl implements MyAnnotation {

      @Override
      public Class<? extends Annotation> annotationType() {
        return MyAnnotation.class;
      }

      @Override
      public String toString() {
        return "@MyAnnotation";
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

  private static class OverrideImpl implements Override {

    static final Override INSTANCE = new OverrideImpl();

    @Override
    public Class<? extends Annotation> annotationType() {
      return Override.class;
    }

    @Override
    public String toString() {
      return "@Override";
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
