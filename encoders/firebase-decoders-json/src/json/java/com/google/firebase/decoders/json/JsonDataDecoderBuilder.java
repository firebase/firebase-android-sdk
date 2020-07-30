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

package com.google.firebase.decoders.json;

import androidx.annotation.NonNull;
import com.google.firebase.decoders.AnnotatedFieldHandler;
import com.google.firebase.decoders.DataDecoder;
import com.google.firebase.decoders.DecoderConfig;
import com.google.firebase.decoders.ObjectDecoder;
import com.google.firebase.decoders.ValueDecoder;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;

public final class JsonDataDecoderBuilder implements DecoderConfig<JsonDataDecoderBuilder> {
  private final HashMap<Class<?>, ObjectDecoder<?>> objectDecoders = new HashMap<>();
  private final HashMap<Class<?>, ValueDecoder<?>> valueDecoders = new HashMap<>();
  private final Map<Class<? extends Annotation>, AnnotatedFieldHandler<?>> fieldHandlers =
      new HashMap<>();

  public JsonDataDecoderBuilder() {}

  @NonNull
  @Override
  public <T> JsonDataDecoderBuilder register(
      @NonNull Class<T> clazz, @NonNull ObjectDecoder<? extends T> objectDecoder) {
    objectDecoders.put(clazz, objectDecoder);
    return this;
  }

  @NonNull
  @Override
  public <U> JsonDataDecoderBuilder register(
      @NonNull Class<U> clazz, @NonNull ValueDecoder<? extends U> valueDecoder) {
    valueDecoders.put(clazz, valueDecoder);
    return this;
  }

  @NonNull
  @Override
  public <U extends Annotation> JsonDataDecoderBuilder register(
      @NonNull Class<U> clazz, @NonNull AnnotatedFieldHandler<U> objectDecoder) {
    fieldHandlers.put(clazz, objectDecoder);
    return this;
  }

  @NonNull
  public DataDecoder build() {
    return new JsonDataDecoderContext(objectDecoders, valueDecoders, fieldHandlers);
  }
}
