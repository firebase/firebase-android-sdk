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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public abstract class ReflectiveSetter {

  public abstract void set(@NonNull Object obj, @Nullable Object val);

  private ReflectiveSetter() {}

  @NonNull
  public static ReflectiveSetter of(@NonNull Field field){
    return new ReflectiveFieldSetter(field);
  }

  @NonNull
  public static ReflectiveSetter of(@NonNull Method setter){
    return new ReflectiveMethodSetter(setter);
  }

  static class ReflectiveFieldSetter extends ReflectiveSetter {
    private Field field;

    public ReflectiveFieldSetter(Field field) {
      this.field = field;
    }

    @Override
    public void set(@NonNull Object obj, @Nullable Object value) {
      field.setAccessible(true);
      try {
        field.set(obj, value);
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }
  }

  static class ReflectiveMethodSetter extends ReflectiveSetter {
    private Method method;

    public ReflectiveMethodSetter(Method method) {
      this.method = method;
    }

    @Override
    public void set(@NonNull Object obj, @Nullable Object val) {
      method.setAccessible(true);
      try {
        method.invoke(obj, val);
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      } catch (InvocationTargetException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
