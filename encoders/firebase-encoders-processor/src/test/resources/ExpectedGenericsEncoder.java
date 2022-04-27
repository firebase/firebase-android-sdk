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

import com.google.firebase.encoders.FieldDescriptor;
import com.google.firebase.encoders.ObjectEncoder;
import com.google.firebase.encoders.ObjectEncoderContext;
import com.google.firebase.encoders.config.Configurator;
import com.google.firebase.encoders.config.EncoderConfig;
import java.io.IOException;
import java.lang.Override;

/**
 * @hide */
public final class AutoGenericsEncoder implements Configurator {
    public static final int CODEGEN_VERSION = 2;

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

        private static final FieldDescriptor BAR3_DESCRIPTOR = FieldDescriptor.of("bar3");

        private static final FieldDescriptor BAR4_DESCRIPTOR = FieldDescriptor.of("bar4");

        private static final FieldDescriptor MULTI_DESCRIPTOR = FieldDescriptor.of("multi");

        @Override
        public void encode(Generics value, ObjectEncoderContext ctx) throws IOException {
            ctx.add(BAR3_DESCRIPTOR, value.getBar3());
            ctx.add(BAR4_DESCRIPTOR, value.getBar4());
            ctx.add(MULTI_DESCRIPTOR, value.getMulti());
        }
    }

    private static final class BarEncoder implements ObjectEncoder<Bar> {
        static final BarEncoder INSTANCE = new BarEncoder();

        private static final FieldDescriptor FOO_DESCRIPTOR = FieldDescriptor.of("foo");

        @Override
        public void encode(Bar value, ObjectEncoderContext ctx) throws IOException {
            ctx.add(FOO_DESCRIPTOR, value.getFoo());
        }
    }

    private static final class BazEncoder implements ObjectEncoder<Baz> {
        static final BazEncoder INSTANCE = new BazEncoder();

        private static final FieldDescriptor T_DESCRIPTOR = FieldDescriptor.of("t");

        @Override
        public void encode(Baz value, ObjectEncoderContext ctx) throws IOException {
            ctx.add(T_DESCRIPTOR, value.getT());
        }
    }

    private static final class FooEncoder implements ObjectEncoder<Foo> {
        static final FooEncoder INSTANCE = new FooEncoder();

        private static final FieldDescriptor T_DESCRIPTOR = FieldDescriptor.of("t");

        @Override
        public void encode(Foo value, ObjectEncoderContext ctx) throws IOException {
            ctx.add(T_DESCRIPTOR, value.getT());
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

        private static final FieldDescriptor FOOT_DESCRIPTOR = FieldDescriptor.of("fooT");

        private static final FieldDescriptor FOOU_DESCRIPTOR = FieldDescriptor.of("fooU");

        @Override
        public void encode(Multi value, ObjectEncoderContext ctx) throws IOException {
            ctx.add(FOOT_DESCRIPTOR, value.getFooT());
            ctx.add(FOOU_DESCRIPTOR, value.getFooU());
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
