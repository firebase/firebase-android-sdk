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
import com.google.firebase.encoders.EncodingException;
import com.google.firebase.encoders.ObjectEncoder;
import com.google.firebase.encoders.ValueEncoder;
import com.google.firebase.encoders.config.Configurator;
import com.google.firebase.encoders.config.EncoderConfig;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/** Implements protocol buffer encoding. */
public class ProtobufEncoder {
  private final Map<Class<?>, ObjectEncoder<?>> objectEncoders;
  private final Map<Class<?>, ValueEncoder<?>> valueEncoders;
  private final ObjectEncoder<Object> fallbackEncoder;

  ProtobufEncoder(
      Map<Class<?>, ObjectEncoder<?>> objectEncoders,
      Map<Class<?>, ValueEncoder<?>> valueEncoders,
      ObjectEncoder<Object> fallbackEncoder) {
    this.objectEncoders = objectEncoders;
    this.valueEncoders = valueEncoders;
    this.fallbackEncoder = fallbackEncoder;
  }

  /** Encodes an arbitrary object and directly writes into the output stream. */
  public void encode(@NonNull Object value, @NonNull OutputStream outputStream) throws IOException {
    ProtobufDataEncoderContext context =
        new ProtobufDataEncoderContext(
            outputStream, objectEncoders, valueEncoders, fallbackEncoder);
    context.encode(value);
  }

  /** Encodes an arbitrary object and returns it as a byte array. */
  @NonNull
  public byte[] encode(@NonNull Object value) {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try {
      encode(value, output);
    } catch (IOException e) {
      // Should not happen (TM) A ByteArrayOutputStream does not throw IOException.
    }
    return output.toByteArray();
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder implements EncoderConfig<Builder> {
    private static final ObjectEncoder<Object> DEFAULT_FALLBACK_ENCODER =
        (o, ctx) -> {
          throw new EncodingException(
              "Couldn't find encoder for type " + o.getClass().getCanonicalName());
        };

    private final Map<Class<?>, ObjectEncoder<?>> objectEncoders = new HashMap<>();
    private final Map<Class<?>, ValueEncoder<?>> valueEncoders = new HashMap<>();
    private ObjectEncoder<Object> fallbackEncoder = DEFAULT_FALLBACK_ENCODER;

    @NonNull
    @Override
    public <U> Builder registerEncoder(
        @NonNull Class<U> type, @NonNull ObjectEncoder<? super U> encoder) {
      objectEncoders.put(type, encoder);
      // Remove it from the other map if present.
      valueEncoders.remove(type);
      return this;
    }

    @NonNull
    @Override
    public <U> Builder registerEncoder(
        @NonNull Class<U> type, @NonNull ValueEncoder<? super U> encoder) {
      valueEncoders.put(type, encoder);
      // Remove it from the other map if present.
      objectEncoders.remove(type);
      return this;
    }

    @NonNull
    public Builder registerFallbackEncoder(@NonNull ObjectEncoder<Object> fallbackEncoder) {
      this.fallbackEncoder = fallbackEncoder;
      return this;
    }

    @NonNull
    public Builder configureWith(@NonNull Configurator config) {
      config.configure(this);
      return this;
    }

    public ProtobufEncoder build() {
      return new ProtobufEncoder(
          new HashMap<>(objectEncoders), new HashMap<>(valueEncoders), fallbackEncoder);
    }
  }
}
