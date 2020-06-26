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
import com.google.firebase.encoders.EncodingException;
import com.google.firebase.encoders.GenericEncoder;
import com.google.firebase.encoders.ObjectEncoder;
import com.google.firebase.encoders.ValueEncoder;
import com.google.firebase.encoders.config.Configurator;
import com.google.firebase.encoders.config.EncoderConfig;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public class ProtobufDataEncoderBuilder implements EncoderConfig<ProtobufDataEncoderBuilder> {
  private static final ObjectEncoder<Object> DEFAULT_FALLBACK_ENCODER =
      (o, ctx) -> {
        throw new EncodingException(
            "Couldn't find encoder for type " + o.getClass().getCanonicalName());
      };

  private final Map<Class<?>, ObjectEncoder<?>> objectEncoders = new HashMap<>();
  private final Map<Class<?>, ValueEncoder<?>> valueEncoders = new HashMap<>();
  private ObjectEncoder<Object> fallbackEncoder = DEFAULT_FALLBACK_ENCODER;

  @NonNull
  public GenericEncoder<OutputStream, byte[]> build() {
    return new GenericEncoder<OutputStream, byte[]>() {
      @Override
      public void encode(@NonNull Object obj, @NonNull OutputStream outputStream)
          throws IOException {
        ProtobufDataEncoderContext ctx =
            new ProtobufDataEncoderContext(
                objectEncoders, valueEncoders, fallbackEncoder, outputStream);
        ctx.encode(obj);
      }

      @NonNull
      @Override
      public byte[] encode(@NonNull Object obj) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
          encode(obj, output);
        } catch (IOException e) {
          // should never happen
        }
        return output.toByteArray();
      }
    };
  }

  @NonNull
  @Override
  public <U> ProtobufDataEncoderBuilder registerEncoder(
      @NonNull Class<U> type, @NonNull ObjectEncoder<? super U> encoder) {
    objectEncoders.put(type, encoder);
    return this;
  }

  @NonNull
  @Override
  public <U> ProtobufDataEncoderBuilder registerEncoder(
      @NonNull Class<U> type, @NonNull ValueEncoder<? super U> encoder) {
    valueEncoders.put(type, encoder);
    return this;
  }

  @NonNull
  public ProtobufDataEncoderBuilder configureWith(@NonNull Configurator configurator) {
    configurator.configure(this);
    return this;
  }
}
