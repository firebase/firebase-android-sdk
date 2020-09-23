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

/**
 * @hide */
public final class AutoTypeWithListEncoder implements Configurator {
    public static final int CODEGEN_VERSION = 1;

    public static final Configurator CONFIG = new AutoTypeWithListEncoder();

    private AutoTypeWithListEncoder() {
    }

    @Override
    public void configure(EncoderConfig<?> cfg) {
        cfg.registerEncoder(TypeWithList.class, TypeWithListEncoder.INSTANCE);
        cfg.registerEncoder(Member.class, MemberEncoder.INSTANCE);
    }

    private static final class TypeWithListEncoder implements ObjectEncoder<TypeWithList> {
        static final TypeWithListEncoder INSTANCE = new TypeWithListEncoder();

        @Override
        public void encode(TypeWithList value, ObjectEncoderContext ctx) throws IOException {
            ctx.add("member", value.getMember());
        }
    }

    private static final class MemberEncoder implements ObjectEncoder<Member> {
        static final MemberEncoder INSTANCE = new MemberEncoder();

        @Override
        public void encode(Member value, ObjectEncoderContext ctx) throws IOException {
        }
    }
}
