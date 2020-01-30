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

import android.util.Base64;
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
import java.util.Date;
import java.util.Map;

final class JsonValueObjectEncoderContext implements ObjectEncoderContext, ValueEncoderContext {

  private JsonValueObjectEncoderContext childContext = null;
  private boolean active = true;
  private final JsonWriter jsonWriter;
  private final Map<Class<?>, ObjectEncoder<?>> objectEncoders;
  private final Map<Class<?>, ValueEncoder<?>> valueEncoders;
  private final ObjectEncoder<Object> fallbackEncoder;
  private final boolean ignoreNullValues;

  JsonValueObjectEncoderContext(
      @NonNull Writer writer,
      @NonNull Map<Class<?>, ObjectEncoder<?>> objectEncoders,
      @NonNull Map<Class<?>, ValueEncoder<?>> valueEncoders,
      ObjectEncoder<Object> fallbackEncoder,
      boolean ignoreNullValues) {
    this.jsonWriter = new JsonWriter(writer);
    this.objectEncoders = objectEncoders;
    this.valueEncoders = valueEncoders;
    this.fallbackEncoder = fallbackEncoder;
    this.ignoreNullValues = ignoreNullValues;
  }

  private JsonValueObjectEncoderContext(JsonValueObjectEncoderContext anotherContext) {
    this.jsonWriter = anotherContext.jsonWriter;
    this.objectEncoders = anotherContext.objectEncoders;
    this.valueEncoders = anotherContext.valueEncoders;
    this.fallbackEncoder = anotherContext.fallbackEncoder;
    this.ignoreNullValues = anotherContext.ignoreNullValues;
  }

  @NonNull
  @Override
  public JsonValueObjectEncoderContext add(@NonNull String name, @Nullable Object o)
      throws IOException {
    if (ignoreNullValues) {
      return internalAddIgnoreNullValues(name, o);
    }
    return internalAdd(name, o);
  }

  @NonNull
  @Override
  public JsonValueObjectEncoderContext add(@NonNull String name, double value) throws IOException {
    maybeUnNest();
    jsonWriter.name(name);
    return add(value);
  }

  @NonNull
  @Override
  public JsonValueObjectEncoderContext add(@NonNull String name, int value) throws IOException {
    maybeUnNest();
    jsonWriter.name(name);
    return add(value);
  }

  @NonNull
  @Override
  public JsonValueObjectEncoderContext add(@NonNull String name, long value) throws IOException {
    maybeUnNest();
    jsonWriter.name(name);
    return add(value);
  }

  @NonNull
  @Override
  public JsonValueObjectEncoderContext add(@NonNull String name, boolean value) throws IOException {
    maybeUnNest();
    jsonWriter.name(name);
    return add(value);
  }

  @NonNull
  @Override
  public ObjectEncoderContext inline(@Nullable Object value) throws IOException {
    return add(value, true);
  }

  @NonNull
  @Override
  public ObjectEncoderContext nested(@NonNull String name) throws IOException {
    maybeUnNest();
    childContext = new JsonValueObjectEncoderContext(this);
    jsonWriter.name(name);
    jsonWriter.beginObject();
    return childContext;
  }

  @NonNull
  @Override
  public JsonValueObjectEncoderContext add(@Nullable String value) throws IOException {
    maybeUnNest();
    jsonWriter.value(value);
    return this;
  }

  @NonNull
  @Override
  public JsonValueObjectEncoderContext add(double value) throws IOException {
    maybeUnNest();
    jsonWriter.value(value);
    return this;
  }

  @NonNull
  @Override
  public JsonValueObjectEncoderContext add(int value) throws IOException {
    maybeUnNest();
    jsonWriter.value(value);
    return this;
  }

  @NonNull
  @Override
  public JsonValueObjectEncoderContext add(long value) throws IOException {
    maybeUnNest();
    jsonWriter.value(value);
    return this;
  }

  @NonNull
  @Override
  public JsonValueObjectEncoderContext add(boolean value) throws IOException {
    maybeUnNest();
    jsonWriter.value(value);
    return this;
  }

  @NonNull
  @Override
  public JsonValueObjectEncoderContext add(@Nullable byte[] bytes) throws IOException {
    maybeUnNest();
    if (bytes == null) {
      jsonWriter.nullValue();
    } else {
      jsonWriter.value(Base64.encodeToString(bytes, Base64.NO_WRAP));
    }
    return this;
  }

  @NonNull
  JsonValueObjectEncoderContext add(@Nullable Object o, boolean inline) throws IOException {
    if (inline && cannotBeInline(o)) {
      throw new EncodingException(
          String.format("%s cannot be encoded inline", o == null ? null : o.getClass()));
    }
    if (o == null) {
      jsonWriter.nullValue();
      return this;
    }
    if (o instanceof Number) {
      jsonWriter.value((Number) o);
      return this;
    }

    if (o.getClass().isArray()) {
      // Byte[] are a special case of arrays, because they are not mapped to an array, but to a
      // string.
      if (o instanceof byte[]) {
        return add((byte[]) o);
      }

      jsonWriter.beginArray();
      if (o instanceof int[]) {
        for (int item : (int[]) o) {
          jsonWriter.value(item);
        }
      } else if (o instanceof long[]) {
        for (long item : (long[]) o) {
          add(item);
        }
      } else if (o instanceof double[]) {
        for (double item : (double[]) o) {
          jsonWriter.value(item);
        }
      } else if (o instanceof boolean[]) {
        for (boolean item : (boolean[]) o) {
          jsonWriter.value(item);
        }
      } else if (o instanceof Number[]) {
        for (Number item : (Number[]) o) {
          add(item, false);
        }

      } else {
        for (Object item : (Object[]) o) {
          add(item, false);
        }
      }
      jsonWriter.endArray();
      return this;
    }
    if (o instanceof Collection) {
      Collection collection = (Collection) o;
      jsonWriter.beginArray();
      for (Object elem : collection) {
        add(elem, false);
      }
      jsonWriter.endArray();
      return this;
    }
    if (o instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<Object, Object> map = (Map<Object, Object>) o;
      jsonWriter.beginObject();
      for (Map.Entry<Object, Object> entry : map.entrySet()) {
        Object key = entry.getKey();
        try {
          add((String) key, entry.getValue());
        } catch (ClassCastException ex) {
          throw new EncodingException(
              String.format(
                  "Only String keys are currently supported in maps, got %s of type %s instead.",
                  key, key.getClass()),
              ex);
        }
      }
      jsonWriter.endObject();
      return this;
    }
    @SuppressWarnings("unchecked") // safe because get the encoder by checking the object's type.
    ObjectEncoder<Object> objectEncoder = (ObjectEncoder<Object>) objectEncoders.get(o.getClass());
    if (objectEncoder != null) {
      return doEncode(objectEncoder, o, inline);
    }
    @SuppressWarnings("unchecked") // safe because get the encoder by checking the object's type.
    ValueEncoder<Object> valueEncoder = (ValueEncoder<Object>) valueEncoders.get(o.getClass());
    if (valueEncoder != null) {
      valueEncoder.encode(o, this);
      return this;
    }

    // Process enum last if it does not have a custom encoder registered.
    if (o instanceof Enum) {
      add(((Enum) o).name());
      return this;
    }

    return doEncode(fallbackEncoder, o, inline);
  }

  JsonValueObjectEncoderContext doEncode(ObjectEncoder<Object> encoder, Object o, boolean inline)
      throws IOException {
    if (!inline) jsonWriter.beginObject();
    encoder.encode(o, this);
    if (!inline) jsonWriter.endObject();
    return this;
  }

  private boolean cannotBeInline(Object value) {
    return value == null
        || value.getClass().isArray()
        || value instanceof Collection
        || value instanceof Date
        || value instanceof Enum
        || value instanceof Number;
  }

  void close() throws IOException {
    maybeUnNest();
    jsonWriter.flush();
  }

  private void maybeUnNest() throws IOException {
    if (!active) {
      throw new IllegalStateException(
          "Parent context used since this context was created. Cannot use this context anymore.");
    }
    if (childContext != null) {
      childContext.maybeUnNest();
      childContext.active = false;
      childContext = null;
      jsonWriter.endObject();
    }
  }

  private JsonValueObjectEncoderContext internalAdd(@NonNull String name, @Nullable Object o)
      throws IOException, EncodingException {
    maybeUnNest();
    jsonWriter.name(name);
    if (o == null) {
      jsonWriter.nullValue();
      return this;
    }
    return add(o, false);
  }

  private JsonValueObjectEncoderContext internalAddIgnoreNullValues(
      @NonNull String name, @Nullable Object o) throws IOException, EncodingException {
    if (o == null) {
      return this;
    }
    maybeUnNest();
    jsonWriter.name(name);
    return add(o, false);
  }
}
