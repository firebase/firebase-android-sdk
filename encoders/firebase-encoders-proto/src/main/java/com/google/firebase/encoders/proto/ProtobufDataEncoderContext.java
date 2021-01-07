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

package com.google.firebase.encoders.proto;

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
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Map;

final class ProtobufDataEncoderContext implements ObjectEncoderContext {
  private static final Charset UTF_8 = Charset.forName("UTF-8");

  private OutputStream output;
  private final Map<Class<?>, ObjectEncoder<?>> objectEncoders;
  private final Map<Class<?>, ValueEncoder<?>> valueEncoders;
  private final ObjectEncoder<Object> fallbackEncoder;

  private static final FieldDescriptor MAP_KEY_DESC =
      FieldDescriptor.builder("key").withProperty(AtProtobuf.builder().tag(1).build()).build();

  private static final FieldDescriptor MAP_VALUE_DESC =
      FieldDescriptor.builder("value").withProperty(AtProtobuf.builder().tag(2).build()).build();

  // See https://developers.google.com/protocol-buffers/docs/proto#backwards_compatibility
  private static final ObjectEncoder<Map.Entry<Object, Object>> DEFAULT_MAP_ENCODER =
      (o, ctx) -> {
        ctx.add(MAP_KEY_DESC, o.getKey());
        ctx.add(MAP_VALUE_DESC, o.getValue());
      };

  ProtobufDataEncoderContext(
      OutputStream output,
      Map<Class<?>, ObjectEncoder<?>> objectEncoders,
      Map<Class<?>, ValueEncoder<?>> valueEncoders,
      ObjectEncoder<Object> fallbackEncoder) {
    this.output = output;
    this.objectEncoders = objectEncoders;
    this.valueEncoders = valueEncoders;
    this.fallbackEncoder = fallbackEncoder;
  }

  @NonNull
  @Override
  public ObjectEncoderContext add(@NonNull String name, @Nullable Object obj) throws IOException {
    return add(FieldDescriptor.of(name), obj);
  }

  @NonNull
  @Override
  public ObjectEncoderContext add(@NonNull String name, double value) throws IOException {
    return add(FieldDescriptor.of(name), value);
  }

  @NonNull
  @Override
  public ObjectEncoderContext add(@NonNull String name, int value) throws IOException {
    return add(FieldDescriptor.of(name), value);
  }

  @NonNull
  @Override
  public ObjectEncoderContext add(@NonNull String name, long value) throws IOException {
    return add(FieldDescriptor.of(name), value);
  }

  @NonNull
  @Override
  public ObjectEncoderContext add(@NonNull String name, boolean value) throws IOException {
    return add(FieldDescriptor.of(name), value);
  }

  @NonNull
  @Override
  public ObjectEncoderContext add(@NonNull FieldDescriptor field, @Nullable Object obj)
      throws IOException {
    if (obj == null) {
      return this;
    }
    if (obj instanceof CharSequence) {
      CharSequence seq = (CharSequence) obj;
      if (seq.length() == 0) {
        return this;
      }
      int tag = getTag(field);
      int wire = 2;
      writeVarInt32((tag << 3) | wire);
      byte[] bytes = seq.toString().getBytes(UTF_8);
      writeVarInt32(bytes.length);
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
      for (Map.Entry<Object, Object> entry : map.entrySet()) {
        doEncode(DEFAULT_MAP_ENCODER, field, entry);
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
      return add(field, ((Number) obj).longValue());
    }

    if (obj instanceof Boolean) {
      return add(field, (boolean) obj);
    }

    if (obj instanceof byte[]) {
      byte[] bytes = (byte[]) obj;
      if (bytes.length == 0) {
        return this;
      }
      int tag = getTag(field);
      int wire = 2;
      writeVarInt32((tag << 3) | wire);
      writeVarInt32(bytes.length);
      output.write(bytes);
      return this;
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

    if (obj instanceof ProtoEnum) {
      return add(field, ((ProtoEnum) obj).getNumber());
    }
    if (obj instanceof Enum) {
      return add(field, ((Enum<?>) obj).ordinal());
    }
    return doEncode(fallbackEncoder, field, obj);
  }

  @NonNull
  @Override
  public ObjectEncoderContext add(@NonNull FieldDescriptor field, double value) throws IOException {
    if (value == 0) {
      return this;
    }
    int tag = getTag(field);
    int wire = 1;
    writeVarInt32((tag << 3) | wire);
    output.write(allocateBuffer(8).putDouble(value).array());
    return this;
  }

  @NonNull
  @Override
  public ObjectEncoderContext add(@NonNull FieldDescriptor field, float value) throws IOException {
    if (value == 0) {
      return this;
    }
    int tag = getTag(field);
    int wire = 5;
    writeVarInt32((tag << 3) | wire);
    output.write(allocateBuffer(4).putFloat(value).array());
    return this;
  }

  @NonNull
  @Override
  public ProtobufDataEncoderContext add(@NonNull FieldDescriptor field, int value)
      throws IOException {
    if (value == 0) {
      return this;
    }
    Protobuf protobuf = getProtobuf(field);
    switch (protobuf.intEncoding()) {
      case DEFAULT:
        writeVarInt32((protobuf.tag() << 3));
        writeVarInt32(value);
        break;
      case SIGNED:
        writeVarInt32((protobuf.tag() << 3));
        writeVarInt32((value << 1) ^ (value >> 31));
        break;
      case FIXED:
        writeVarInt32((protobuf.tag() << 3) | 5);
        output.write(allocateBuffer(4).putInt(value).array());
        break;
    }
    return this;
  }

  @NonNull
  @Override
  public ProtobufDataEncoderContext add(@NonNull FieldDescriptor field, long value)
      throws IOException {
    if (value == 0) {
      return this;
    }
    Protobuf protobuf = getProtobuf(field);
    switch (protobuf.intEncoding()) {
      case DEFAULT:
        writeVarInt32((protobuf.tag() << 3));
        writeVarInt64(value);
        break;
      case SIGNED:
        writeVarInt32((protobuf.tag() << 3));
        writeVarInt64((value << 1) ^ (value >> 63));
        break;
      case FIXED:
        writeVarInt32((protobuf.tag() << 3) | 1);
        output.write(allocateBuffer(8).putLong(value).array());
        break;
    }
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
  public ObjectEncoderContext inline(@Nullable Object value) throws IOException {
    return encode(value);
  }

  ProtobufDataEncoderContext encode(@Nullable Object value) throws IOException {
    if (value == null) {
      return this;
    }
    @SuppressWarnings("unchecked")
    ObjectEncoder<Object> objectEncoder =
        (ObjectEncoder<Object>) objectEncoders.get(value.getClass());
    if (objectEncoder != null) {
      objectEncoder.encode(value, this);
      return this;
    }
    throw new EncodingException("No encoder for " + value.getClass());
  }

  @NonNull
  @Override
  public ObjectEncoderContext nested(@NonNull String name) throws IOException {
    return nested(FieldDescriptor.of(name));
  }

  @NonNull
  @Override
  public ObjectEncoderContext nested(@NonNull FieldDescriptor field) throws IOException {
    throw new EncodingException("nested() is not implemented for protobuf encoding.");
  }

  private <T> ProtobufDataEncoderContext doEncode(
      ObjectEncoder<T> encoder, FieldDescriptor field, T obj) throws IOException {

    long size = determineSize(encoder, obj);
    if (size == 0) {
      return this;
    }

    int tag = getTag(field);
    int wire = 2;
    writeVarInt32((tag << 3) | wire);
    writeVarInt64(size);
    encoder.encode(obj, this);
    return this;
  }

  private <T> long determineSize(ObjectEncoder<T> encoder, T obj) throws IOException {
    // TODO(vkryachko): consider reusing these output streams to avoid allocations.
    try (LengthCountingOutputStream out = new LengthCountingOutputStream()) {

      OutputStream originalStream = output;
      output = out;
      try {
        encoder.encode(obj, this);
      } finally {
        output = originalStream;
      }
      return out.getLength();
    }
  }

  private <T> ProtobufDataEncoderContext doEncode(
      ValueEncoder<T> encoder, FieldDescriptor field, T obj) throws IOException {
    // TODO(vkryachko): consider reusing value encoder contexts to avoid allocations.
    encoder.encode(obj, new ProtobufValueEncoderContext(field, this));
    return this;
  }

  private static ByteBuffer allocateBuffer(int length) {
    return ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);
  }

  private static int getTag(FieldDescriptor field) {
    Protobuf protobuf = field.getProperty(Protobuf.class);
    if (protobuf == null) {
      throw new EncodingException("Field has no @Protobuf config");
    }
    return protobuf.tag();
  }

  private static Protobuf getProtobuf(FieldDescriptor field) {
    Protobuf protobuf = field.getProperty(Protobuf.class);
    if (protobuf == null) {
      throw new EncodingException("Field has no @Protobuf config");
    }
    return protobuf;
  }

  private void writeVarInt32(int value) throws IOException {
    while ((value & 0xFFFFFF80) != 0L) {
      output.write((value & 0x7F) | 0x80);
      value >>>= 7;
    }
    output.write(value & 0x7F);
  }

  private void writeVarInt64(long value) throws IOException {
    while ((value & 0xFFFFFFFFFFFFFF80L) != 0L) {
      output.write(((int) value & 0x7F) | 0x80);
      value >>>= 7;
    }
    output.write((int) value & 0x7F);
  }
}
