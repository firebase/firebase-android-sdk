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

public final class AutoGenericsEncoder implements Configurator {
    public static final int CODEGEN_VERSION = 1;

    public static final Configurator CONFIG = new AutoGenericsEncoder();

    private AutoGenericsEncoder() {
    }

    @Override
    public void configure(EncoderConfig<?> cfg) {
        cfg.registerEncoder(Generics.class, GenericsEncoder.INSTANCE);
        cfg.registerEncoder(Bar.class, BarEncoder.INSTANCE);
        cfg.registerEncoder(Baz.class, BazEncoder.INSTANCE);
        cfg.registerEncoder(Foo.class, FooEncoder.INSTANCE);
        cfg.registerEncoder(Member3.class, Member3Encoder.INSTANCE);
        cfg.registerEncoder(Member4.class, Member4Encoder.INSTANCE);
        cfg.registerEncoder(Multi.class, MultiEncoder.INSTANCE);
        cfg.registerEncoder(Member.class, MemberEncoder.INSTANCE);
        cfg.registerEncoder(Member2.class, Member2Encoder.INSTANCE);
    }

    private static final class GenericsEncoder implements ObjectEncoder<Generics> {
        static final GenericsEncoder INSTANCE = new GenericsEncoder();

        @Override
        public void encode(Generics value, ObjectEncoderContext ctx) throws IOException {
            ctx.add("bar3", value.getBar3());
            ctx.add("bar4", value.getBar4());
            ctx.add("multi", value.getMulti());
        }
    }

    private static final class BarEncoder implements ObjectEncoder<Bar> {
        static final BarEncoder INSTANCE = new BarEncoder();

        @Override
        public void encode(Bar value, ObjectEncoderContext ctx) throws IOException {
            ctx.add("foo", value.getFoo());
        }
    }

    private static final class BazEncoder implements ObjectEncoder<Baz> {
        static final BazEncoder INSTANCE = new BazEncoder();

        @Override
        public void encode(Baz value, ObjectEncoderContext ctx) throws IOException {
            ctx.add("t", value.getT());
        }
    }

    private static final class FooEncoder implements ObjectEncoder<Foo> {
        static final FooEncoder INSTANCE = new FooEncoder();

        @Override
        public void encode(Foo value, ObjectEncoderContext ctx) throws IOException {
            ctx.add("t", value.getT());
        }
    }

    private static final class Member3Encoder implements ObjectEncoder<Member3> {
        static final Member3Encoder INSTANCE = new Member3Encoder();

        @Override
        public void encode(Member3 value, ObjectEncoderContext ctx) throws IOException {
        }
    }

    private static final class Member4Encoder implements ObjectEncoder<Member4> {
        static final Member4Encoder INSTANCE = new Member4Encoder();

        @Override
        public void encode(Member4 value, ObjectEncoderContext ctx) throws IOException {
        }
    }

    private static final class MultiEncoder implements ObjectEncoder<Multi> {
        static final MultiEncoder INSTANCE = new MultiEncoder();

        @Override
        public void encode(Multi value, ObjectEncoderContext ctx) throws IOException {
            ctx.add("fooT", value.getFooT());
            ctx.add("fooU", value.getFooU());
        }
    }

    private static final class MemberEncoder implements ObjectEncoder<Member> {
        static final MemberEncoder INSTANCE = new MemberEncoder();

        @Override
        public void encode(Member value, ObjectEncoderContext ctx) throws IOException {
        }
    }

    private static final class Member2Encoder implements ObjectEncoder<Member2> {
        static final Member2Encoder INSTANCE = new Member2Encoder();

        @Override
        public void encode(Member2 value, ObjectEncoderContext ctx) throws IOException {
        }
    }
}
