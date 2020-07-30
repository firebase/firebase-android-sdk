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

import android.util.JsonReader;
import androidx.annotation.NonNull;
import com.google.firebase.decoders.ValueDecoderContext;
import java.io.IOException;

class ValueDecoderContextImpl implements ValueDecoderContext {

  private final JsonReader reader;

  static ValueDecoderContext from(JsonReader reader) {
    return new ValueDecoderContextImpl(reader);
  }

  private ValueDecoderContextImpl(JsonReader reader) {
    this.reader = reader;
  }

  @NonNull
  @Override
  public String decodeString() throws IOException {
    return reader.nextString();
  }

  @Override
  public boolean decodeBoolean() throws IOException {
    return reader.nextBoolean();
  }

  @Override
  public int decodeInteger() throws IOException {
    return reader.nextInt();
  }

  @Override
  public long decodeLong() throws IOException {
    return reader.nextLong();
  }

  @Override
  public double decodeDouble() throws IOException {
    return reader.nextDouble();
  }
}
