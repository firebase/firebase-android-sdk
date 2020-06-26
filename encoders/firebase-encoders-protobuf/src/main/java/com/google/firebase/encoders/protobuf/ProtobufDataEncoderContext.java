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

package com.google.firebase.encoders.protobuf;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.encoders.EncodingException;
import com.google.firebase.encoders.FieldDescriptor;
import com.google.firebase.encoders.ObjectEncoder;
import com.google.firebase.encoders.ObjectEncoderContext;
import com.google.firebase.encoders.ValueEncoder;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

class ProtobufDataEncoderContext implements ObjectEncoderContext {
  private static final Charset UTF_8 = Charset.forName("UTF-8");

  private final Map<Class<?>, ObjectEncoder<?>> objectEncoders;
  private final Map<Class<?>, ValueEncoder<?>> valueEncoders;
  private final ObjectEncoder<Object> fallbackEncoder;
  private OutputStream output;

  private static final FieldDescriptor MAP_KEY_DESC =
      FieldDescriptor.builder("key").withProperty(AtProtobuf.builder().tag(1).build()).build();

  private static final FieldDescriptor MAP_VALUE_DESC =
      FieldDescriptor.builder("value").withProperty(AtProtobuf.builder().tag(2).build()).build();

  // See https://developers.google.com/protocol-buffers/docs/proto#backwards-compatibility
  private static final ObjectEncoder<Map<Object, Object>> DEFAULT_MAP_ENCODER =
      (o, ctx) -> {
        for (Entry<Object, Object> entry : o.entrySet()) {
          ctx.add(MAP_KEY_DESC, entry.getKey());
          ctx.add(MAP_VALUE_DESC, entry.getValue());
        }
      };

  ProtobufDataEncoderContext(
      Map<Class<?>, ObjectEncoder<?>> objectEncoders,
      Map<Class<?>, ValueEncoder<?>> valueEncoders,
      ObjectEncoder<Object> fallbackEncoder,
      OutputStream output) {
    this.objectEncoders = objectEncoders;
    this.valueEncoders = valueEncoders;
    this.fallbackEncoder = fallbackEncoder;
    this.output = output;
  }

  @NonNull
  @Override
  public ProtobufDataEncoderContext add(@NonNull String name, @Nullable Object obj) {
    throw new UnsupportedOperationException();
  }

  @NonNull
  @Override
  public ProtobufDataEncoderContext add(@NonNull String name, double value) {
    throw new UnsupportedOperationException();
  }

  @NonNull
  @Override
  public ProtobufDataEncoderContext add(@NonNull String name, int value) {
    throw new UnsupportedOperationException();
  }

  @NonNull
  @Override
  public ProtobufDataEncoderContext add(@NonNull String name, long value) {
    throw new UnsupportedOperationException();
  }

  @NonNull
  @Override
  public ProtobufDataEncoderContext add(@NonNull String name, boolean value) {
    throw new UnsupportedOperationException();
  }

  @NonNull
  @Override
  public ProtobufDataEncoderContext nested(@NonNull String name) {
    throw new UnsupportedOperationException();
  }

  @NonNull
  @Override
  public ProtobufDataEncoderContext add(@NonNull FieldDescriptor field, @Nullable Object obj)
      throws IOException {
    if (obj == null) {
      return this;
    }
    if (obj instanceof CharSequence) {
      CharSequence seq = (CharSequence) obj;
      int tag = getTag(field);
      int wire = 2;
      writeVarint((tag << 3) | wire);
      byte[] bytes = seq.toString().getBytes(UTF_8);
      writeVarint(bytes.length);
      output.write(bytes);
      return this;
    }
    if (obj instanceof Collection) {
      @SuppressWarnings("unchecked")
      Collection<Object> collection = (Collection<Object>) obj;
      for (Object value : collection) {
        add(field, value);
      }
      return this;
    }

    if (obj instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<Object, Object> map = (Map<Object, Object>) obj;
      if (!map.isEmpty()) {
        return doEncode(DEFAULT_MAP_ENCODER, field, map);
      }
      return this;
    }

    if (obj instanceof Double) {
      return add(field, (double) obj);
    }

    if (obj instanceof Float) {
      return add(field, (float) obj);
    }

    if (obj instanceof Number) {
      // todo properly handle numbers that don't fit in a long.
      return add(field, ((Number) obj).longValue());
    }

    @SuppressWarnings("unchecked")
    ObjectEncoder<Object> objectEncoder =
        (ObjectEncoder<Object>) objectEncoders.get(obj.getClass());

    if (objectEncoder != null) {
      return doEncode(objectEncoder, field, obj);
    }
    @SuppressWarnings("unchecked")
    ValueEncoder<Object> valueEncoder = (ValueEncoder<Object>) valueEncoders.get(obj.getClass());
    if (valueEncoder != null) {
      return doEncode(valueEncoder, field, obj);
    }
    return doEncode(fallbackEncoder, field, obj);
  }

  private <T> ProtobufDataEncoderContext doEncode(
      ObjectEncoder<T> encoder, FieldDescriptor field, T obj) throws IOException {
    LengthCountingOutputStream out = new LengthCountingOutputStream();

    OutputStream originalStream = output;
    output = out;
    try {
      encoder.encode(obj, this);
    } finally {
      output = originalStream;
    }
    int tag = getTag(field);
    int wire = 2;
    writeVarint((tag << 3) | wire);
    writeVarint(out.getLength());
    encoder.encode(obj, this);
    return this;
  }

  private <T> ProtobufDataEncoderContext doEncode(
      ValueEncoder<T> encoder, FieldDescriptor field, T obj) {
    // TODO(vkryachko): implement value encoders support.
    throw new UnsupportedOperationException("ValueEncoders are not supported yet.");
  }

  @NonNull
  @Override
  public ProtobufDataEncoderContext add(@NonNull FieldDescriptor field, double value)
      throws IOException {
    int tag = getTag(field);
    int wire = 1;
    writeVarint((tag << 3) | wire);
    output.write(ByteBuffer.allocate(8).putDouble(value).array());
    return this;
  }

  private static int getTag(FieldDescriptor field) {
    Protobuf protobuf = field.getProperty(Protobuf.class);
    if (protobuf == null) {
      throw new EncodingException("Field has no @Protobuf config");
    }
    return protobuf.tag();
  }

  @NonNull
  @Override
  public ProtobufDataEncoderContext add(@NonNull FieldDescriptor field, int value)
      throws IOException {
    int tag = getTag(field);
    int wire = 0;
    writeVarint((tag << 3) | wire);
    writeVarint(value);
    return this;
  }

  @NonNull
  @Override
  public ProtobufDataEncoderContext add(@NonNull FieldDescriptor field, long value)
      throws IOException {
    int tag = getTag(field);
    int wire = 0;
    output.write((tag << 3) | wire);
    writeVarint(value);
    return this;
  }

  @NonNull
  @Override
  public ProtobufDataEncoderContext add(@NonNull FieldDescriptor field, boolean value)
      throws IOException {
    if (!value) {
      return this;
    }
    return add(field, 1);
  }

  @NonNull
  @Override
  public ProtobufDataEncoderContext inline(@Nullable Object value) throws IOException {
    encode(value);
    return this;
  }

  @NonNull
  @Override
  public ProtobufDataEncoderContext nested(@NonNull FieldDescriptor field) {
    // TODO(vkryachko): implement.
    return this;
  }

  void encode(@Nullable Object value) throws IOException {
    if (value == null) {
      return;
    }
    @SuppressWarnings("unchecked")
    ObjectEncoder<Object> objectEncoder =
        (ObjectEncoder<Object>) objectEncoders.get(value.getClass());
    if (objectEncoder != null) {
      objectEncoder.encode(value, this);
      return;
    }
    throw new EncodingException("No encoder for " + value.getClass());
  }

  private void writeVarint(int value) throws IOException {
    while ((value & 0xFFFFFF80) != 0L) {
      output.write((value & 0x7F) | 0x80);
      value >>>= 7;
    }
    output.write(value & 0x7F);
  }

  private void writeVarint(long value) throws IOException {
    while ((value & 0xFFFFFFFFFFFFFF80L) != 0L) {
      output.write(((int) value & 0x7F) | 0x80);
      value >>>= 7;
    }
    output.write((int) value & 0x7F);
  }
}
