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

import androidx.annotation.NonNull;
import com.google.firebase.encoders.ObjectEncoder;
import com.google.firebase.encoders.ObjectEncoderContext;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Uses Java reflection to encode arbitrary objects. */
public class ReflectiveObjectEncoder implements ObjectEncoder<Object> {
  @NonNull public static final ReflectiveObjectEncoder DEFAULT = new ReflectiveObjectEncoder(true);
  private final Map<Class<?>, ObjectEncoder<?>> cache;
  private final ObjectEncoderProvider provider;

  private ReflectiveObjectEncoder(ObjectEncoderProvider provider, boolean threadSafe) {
    this.provider = provider;
    this.cache = threadSafe ? new ConcurrentHashMap<>() : new HashMap<>();
  }

  public ReflectiveObjectEncoder(boolean threadSafe) {
    this(ReflectiveObjectEncoderProvider.INSTANCE, threadSafe);
  }

  @Override
  public void encode(@NonNull Object obj, @NonNull ObjectEncoderContext ctx) throws IOException {

    @SuppressWarnings("unchecked")
    ObjectEncoder<Object> encoder = getEncoder((Class<Object>) obj.getClass());
    encoder.encode(obj, ctx);
  }

  private <T> ObjectEncoder<T> getEncoder(Class<T> type) {

    ObjectEncoder<T> encoder = get(type);

    if (encoder == null) {
      encoder = provider.get(type);
      put(type, encoder);
    }
    return encoder;
  }

  private <T> void put(Class<T> type, ObjectEncoder<T> encoder) {
    cache.put(type, encoder);
  }

  @SuppressWarnings("unchecked")
  private <T> ObjectEncoder<T> get(Class<T> type) {
    return (ObjectEncoder<T>) cache.get(type);
  }
}
