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

import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.annotation.Annotation;

public final class AtMyAnnotation {
  private int intVal;

  private String strVal = "default";

  private MyAnnotation.MyEnum enumVal = MyAnnotation.MyEnum.VALUE1;

  public AtMyAnnotation intVal(int intVal) {
    this.intVal = intVal;
    return this;
  }

  public AtMyAnnotation strVal(String strVal) {
    this.strVal = strVal;
    return this;
  }

  public AtMyAnnotation enumVal(MyAnnotation.MyEnum enumVal) {
    this.enumVal = enumVal;
    return this;
  }

  public static AtMyAnnotation builder() {
    return new AtMyAnnotation();
  }

  public MyAnnotation build() {
    return new MyAnnotationImpl(intVal, strVal, enumVal);
  }

  private static final class MyAnnotationImpl implements MyAnnotation {
    private final int intVal;

    private final String strVal;

    private final MyAnnotation.MyEnum enumVal;

    MyAnnotationImpl(int intVal, String strVal, MyAnnotation.MyEnum enumVal) {
      this.intVal = intVal;
      this.strVal = strVal;
      this.enumVal = enumVal;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
      return MyAnnotation.class;
    }

    @Override
    public int intVal() {
      return intVal;
    }

    @Override
    public String strVal() {
      return strVal;
    }

    @Override
    public MyAnnotation.MyEnum enumVal() {
      return enumVal;
    }
  }
}