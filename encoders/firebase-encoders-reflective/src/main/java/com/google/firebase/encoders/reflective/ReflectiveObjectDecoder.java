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
import com.google.firebase.decoders.ObjectDecoder;
import com.google.firebase.decoders.ObjectDecoderContext;
import com.google.firebase.decoders.TypeCreator;
import java.util.HashMap;
import java.util.Map;

/**
 * Uses Java reflection to decode arbitrary objects.
 *
 * <p>{@link ReflectiveObjectDecoder} can be registered by {@link
 * com.google.firebase.decoders.json.JsonDataDecoderBuilder}
 *
 * <pre>{@code
 * DataDecoder decoder =
 *     new JsonDataDecoderBuilder()
 *     .registerFallBackDecoder(ReflectiveObjectDecoder.DEFAULT)
 *     .build();
 *
 * }</pre>
 */
public class ReflectiveObjectDecoder implements ObjectDecoder<Object> {

  @NonNull public static ReflectiveObjectDecoder DEFAULT = new ReflectiveObjectDecoder();

  private final Map<Class<?>, ObjectDecoder<?>> cache = new HashMap<>();

  private ReflectiveObjectDecoder() {}

  @NonNull
  @Override
  public TypeCreator<Object> decode(@NonNull ObjectDecoderContext<Object> ctx) {
    ObjectDecoder<Object> decoder = getDecoder(ctx.getTypeToken().getRawType());
    return decoder.decode(ctx);
  }

  private <U> ObjectDecoder<U> getDecoder(Class<U> type) {
    @SuppressWarnings("unchecked")
    ObjectDecoder<U> decoder = (ObjectDecoder<U>) cache.get(type);

    if (decoder == null) {
      decoder = ReflectiveObjectDecoderProvider.INSTANCE.get(type);
      cache.put(type, decoder);
    }
    return decoder;
  }
}
