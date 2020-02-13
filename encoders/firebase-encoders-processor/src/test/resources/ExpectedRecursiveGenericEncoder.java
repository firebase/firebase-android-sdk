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

package com.example;

import com.google.firebase.encoders.ObjectEncoder;
import com.google.firebase.encoders.ObjectEncoderContext;
import com.google.firebase.encoders.config.Configurator;
import com.google.firebase.encoders.config.EncoderConfig;
import java.io.IOException;
import java.lang.Override;
import javax.annotation.Generated;

@Generated("com.google.firebase.encoders.processor.EncodableProcessor")
public final class AutoMainClassEncoder implements Configurator {
    public static final int CODEGEN_VERSION = 1;

    public static final Configurator CONFIG = new AutoMainClassEncoder();

    private AutoMainClassEncoder() {
    }

    @Override
    public void configure(EncoderConfig<?> cfg) {
        cfg.registerEncoder(MainClass.class, MainClassEncoder.INSTANCE);
        cfg.registerEncoder(Child.class, ChildEncoder.INSTANCE);
    }

    private static final class MainClassEncoder implements ObjectEncoder<MainClass> {
        static final MainClassEncoder INSTANCE = new MainClassEncoder();

        @Override
        public void encode(MainClass value, ObjectEncoderContext ctx) throws IOException {
            ctx.add("child", value.getChild());
        }
    }

    private static final class ChildEncoder implements ObjectEncoder<Child> {
        static final ChildEncoder INSTANCE = new ChildEncoder();

        @Override
        public void encode(Child value, ObjectEncoderContext ctx) throws IOException {
            ctx.add("stringChild", value.getStringChild());
            ctx.add("intChild", value.getIntChild());
            ctx.add("main", value.getMain());
        }
    }
}
