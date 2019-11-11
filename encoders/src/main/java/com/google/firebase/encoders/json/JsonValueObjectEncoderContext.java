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

package com.google.firebase.encoders.json;

import android.util.JsonWriter;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.encoders.EncodingException;
import com.google.firebase.encoders.ObjectEncoder;
import com.google.firebase.encoders.ObjectEncoderContext;
import com.google.firebase.encoders.ValueEncoder;
import com.google.firebase.encoders.ValueEncoderContext;
import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.Map;

final class JsonValueObjectEncoderContext implements ObjectEncoderContext, ValueEncoderContext {

  private final JsonWriter jsonWriter;
  private final Map<Class<?>, ObjectEncoder<?>> objectEncoders;
  private final Map<Class<?>, ValueEncoder<?>> valueEncoders;

  JsonValueObjectEncoderContext(
      @NonNull Writer writer,
      @NonNull Map<Class<?>, ObjectEncoder<?>> objectEncoders,
      @NonNull Map<Class<?>, ValueEncoder<?>> valueEncoders) {
    this.jsonWriter = new JsonWriter(writer);
    this.objectEncoders = objectEncoders;
    this.valueEncoders = valueEncoders;
  }

  @NonNull
  @Override
  public JsonValueObjectEncoderContext add(@NonNull String name, @Nullable Object o)
      throws IOException, EncodingException {
    jsonWriter.name(name);
    if (o == null) {
      jsonWriter.nullValue();
      return this;
    }
    return add(o);
  }

  @NonNull
  @Override
  public JsonValueObjectEncoderContext add(@NonNull String name, double value)
      throws IOException, EncodingException {
    jsonWriter.name(name);
    return add(value);
  }

  @NonNull
  @Override
  public JsonValueObjectEncoderContext add(@NonNull String name, int value)
      throws IOException, EncodingException {
    jsonWriter.name(name);
    return add(value);
  }

  @NonNull
  @Override
  public JsonValueObjectEncoderContext add(@NonNull String name, boolean value)
      throws IOException, EncodingException {
    jsonWriter.name(name);
    return add(value);
  }

  @NonNull
  @Override
  public JsonValueObjectEncoderContext add(@Nullable String value)
      throws IOException, EncodingException {
    jsonWriter.value(value);
    return this;
  }

  @NonNull
  @Override
  public JsonValueObjectEncoderContext add(double value) throws IOException, EncodingException {
    jsonWriter.value(value);
    return this;
  }

  @NonNull
  @Override
  public JsonValueObjectEncoderContext add(int value) throws IOException, EncodingException {
    jsonWriter.value(value);
    return this;
  }

  @NonNull
  @Override
  public JsonValueObjectEncoderContext add(boolean value) throws IOException, EncodingException {
    jsonWriter.value(value);
    return this;
  }

  @NonNull
  JsonValueObjectEncoderContext add(@Nullable Object o) throws IOException, EncodingException {
    if (o == null) {
      jsonWriter.nullValue();
      return this;
    }
    // TODO: Add missing primitive types.
    if (o.getClass().isArray()) {
      jsonWriter.beginArray();
      if (o.getClass().getComponentType() == int.class) {
        int[] array = (int[]) o;
        for (int item : array) {
          jsonWriter.value(item);
        }
      } else if (o.getClass().getComponentType() == double.class) {
        double[] array = (double[]) o;
        for (double item : array) {
          jsonWriter.value(item);
        }
      } else if (o.getClass().getComponentType() == boolean.class) {
        boolean[] array = (boolean[]) o;
        for (boolean item : array) {
          jsonWriter.value(item);
        }
      } else {
        Object[] array = (Object[]) o;
        for (Object item : array) {
          add(item);
        }
      }
      jsonWriter.endArray();
      return this;
    }
    if (o instanceof Collection) {
      Collection collection = (Collection) o;
      jsonWriter.beginArray();
      for (Object elem : collection) {
        add(elem);
      }
      jsonWriter.endArray();
      return this;
    }
    @SuppressWarnings("unchecked") // safe because get the encoder by checking the object's type.
    ObjectEncoder<Object> objectEncoder = (ObjectEncoder<Object>) objectEncoders.get(o.getClass());
    if (objectEncoder != null) {
      jsonWriter.beginObject();
      objectEncoder.encode(o, this);
      jsonWriter.endObject();
      return this;
    }
    @SuppressWarnings("unchecked") // safe because get the encoder by checking the object's type.
    ValueEncoder<Object> valueEncoder = (ValueEncoder<Object>) valueEncoders.get(o.getClass());
    if (valueEncoder != null) {
      valueEncoder.encode(o, this);
      return this;
    }

    throw new EncodingException(
        "Couldn't find encoder for type " + o.getClass().getCanonicalName());
  }

  void close() throws IOException {
    jsonWriter.flush();
  }
}
