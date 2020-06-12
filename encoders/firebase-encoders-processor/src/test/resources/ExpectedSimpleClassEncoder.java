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

import com.google.firebase.encoders.ObjectEncoder;
import com.google.firebase.encoders.ObjectEncoderContext;
import com.google.firebase.encoders.config.Configurator;
import com.google.firebase.encoders.config.EncoderConfig;
import java.io.IOException;
import java.lang.Override;

public final class AutoSimpleClassEncoder implements Configurator {
  public static final int CODEGEN_VERSION = 1;

  public static final Configurator CONFIG = new AutoSimpleClassEncoder();

  private AutoSimpleClassEncoder() {}

  @Override
  public void configure(EncoderConfig<?> cfg) {
    cfg.registerEncoder(SimpleClass.class, SimpleClassEncoder.INSTANCE);
  }

  private static final class SimpleClassEncoder implements ObjectEncoder<SimpleClass> {
    static final SimpleClassEncoder INSTANCE = new SimpleClassEncoder();

    @Override
    public void encode(SimpleClass value, ObjectEncoderContext ctx)
        throws IOException {
      ctx.add("int", value.getInt());
      ctx.add("bool", value.isBool());
      ctx.add("map", value.getMap());
      ctx.add("foo", value.getField());
    }
  }
}
