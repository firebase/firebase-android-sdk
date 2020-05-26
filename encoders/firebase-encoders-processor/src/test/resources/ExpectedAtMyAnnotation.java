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

package com.example;

import java.lang.Class;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.annotation.Annotation;
import java.util.Arrays;

public final class AtMyAnnotation {
  private int intVal;

  private long longVal;

  private boolean boolVal;

  private short shortVal;

  private float floatVal;

  private double doubleVal;

  private double[] doubleArrayVal;

  private String strVal = "default";

  private MyAnnotation.MyEnum enumVal = com.example.MyAnnotation.MyEnum.VALUE1;

  public AtMyAnnotation intVal(int intVal) {
    this.intVal = intVal;
    return this;
  }

  public AtMyAnnotation longVal(long longVal) {
    this.longVal = longVal;
    return this;
  }

  public AtMyAnnotation boolVal(boolean boolVal) {
    this.boolVal = boolVal;
    return this;
  }

  public AtMyAnnotation shortVal(short shortVal) {
    this.shortVal = shortVal;
    return this;
  }

  public AtMyAnnotation floatVal(float floatVal) {
    this.floatVal = floatVal;
    return this;
  }

  public AtMyAnnotation doubleVal(double doubleVal) {
    this.doubleVal = doubleVal;
    return this;
  }

  public AtMyAnnotation doubleArrayVal(double[] doubleArrayVal) {
    this.doubleArrayVal = doubleArrayVal;
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
    return new MyAnnotationImpl(intVal, longVal, boolVal, shortVal, floatVal, doubleVal, doubleArrayVal, strVal, enumVal);
  }

  private static final class MyAnnotationImpl implements MyAnnotation {
    private final int intVal;

    private final long longVal;

    private final boolean boolVal;

    private final short shortVal;

    private final float floatVal;

    private final double doubleVal;

    private final double[] doubleArrayVal;

    private final String strVal;

    private final MyAnnotation.MyEnum enumVal;

    MyAnnotationImpl(int intVal, long longVal, boolean boolVal, short shortVal, float floatVal,
        double doubleVal, double[] doubleArrayVal, String strVal, MyAnnotation.MyEnum enumVal) {
      this.intVal = intVal;
      this.longVal = longVal;
      this.boolVal = boolVal;
      this.shortVal = shortVal;
      this.floatVal = floatVal;
      this.doubleVal = doubleVal;
      this.doubleArrayVal = doubleArrayVal;
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
    public long longVal() {
      return longVal;
    }

    @Override
    public boolean boolVal() {
      return boolVal;
    }

    @Override
    public short shortVal() {
      return shortVal;
    }

    @Override
    public float floatVal() {
      return floatVal;
    }

    @Override
    public double doubleVal() {
      return doubleVal;
    }

    @Override
    public double[] doubleArrayVal() {
      return doubleArrayVal;
    }

    @Override
    public String strVal() {
      return strVal;
    }

    @Override
    public MyAnnotation.MyEnum enumVal() {
      return enumVal;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) return true;
      if (!(other instanceof MyAnnotation)) return false;
      MyAnnotation that = (MyAnnotation) other;

      return (intVal == that.intVal())
          && (longVal == that.longVal())
          && (boolVal == that.boolVal())
          && (shortVal == that.shortVal())
          && (Float.floatToIntBits(floatVal) == Float.floatToIntBits(that.floatVal()))
          && (Double.doubleToLongBits(doubleVal) == Double.doubleToLongBits(that.doubleVal()))
          && (Arrays.equals(doubleArrayVal, that.doubleArrayVal()))
          && (doubleArrayVal.equals(that.doubleArrayVal()))
          && (strVal.equals(that.strVal()))
          && (enumVal.equals(that.enumVal()));
    }

    @Override
    public int hashCode() {
      return + (-15901618 ^ ((int)intVal))
          + (1338661755 ^ ((int)(longVal ^ (longVal >>> 32))))
          + (-373377111 ^ (boolVal ? 1231 : 1237))
          + (-549529221 ^ ((int)shortVal))
          + (1880085339 ^ (Float.floatToIntBits(floatVal)))
          + (696767088 ^ ((int) ((Double.doubleToLongBits(doubleVal) >>> 32) ^ Double.doubleToLongBits(doubleVal))))
          + (-1932334201 ^ Arrays.hashCode(doubleArrayVal))
          + (-1615429424 ^ strVal.hashCode())
          + (-170319456 ^ enumVal.hashCode())
          ;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder("@com.example.MyAnnotation");
      sb.append('(');
      sb.append("intVal=").append(intVal);
      sb.append("longVal=").append(longVal);
      sb.append("boolVal=").append(boolVal);
      sb.append("shortVal=").append(shortVal);
      sb.append("floatVal=").append(floatVal);
      sb.append("doubleVal=").append(doubleVal);
      sb.append("doubleArrayVal=").append(doubleArrayVal);
      sb.append("strVal=").append(strVal);
      sb.append("enumVal=").append(enumVal);
      sb.append(')');
      return sb.toString();
    }
  }
}