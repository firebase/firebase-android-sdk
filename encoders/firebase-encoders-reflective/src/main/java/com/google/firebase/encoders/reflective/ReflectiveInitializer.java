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
import com.google.firebase.decoders.TypeToken;
import com.google.firebase.encoders.EncodingException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

final class ReflectiveInitializer {

  @NonNull
  static <T> T newInstance(TypeToken.ClassToken<T> classToken) {
    try {
      Constructor<T> constructor = classToken.getRawType().getDeclaredConstructor();
      constructor.setAccessible(true);
      return constructor.newInstance();
    } catch (NoSuchMethodException e) {
      // TODO: try JVM sun.misc.Unsafe to allocate an instance
      throw new EncodingException(
          "Class "
              + classToken
              + " does not define a no-argument constructor. If you are using ProGuard, make "
              + "sure these constructors are not stripped."
              + e);
    } catch (IllegalAccessException e) {
      throw new EncodingException("Could not instantiate type " + classToken, e);
    } catch (InstantiationException e) {
      throw new EncodingException("Could not instantiate type " + classToken, e);
    } catch (InvocationTargetException e) {
      throw new EncodingException("Could not instantiate type " + classToken, e);
    }
  }

  private ReflectiveInitializer() {}
}
