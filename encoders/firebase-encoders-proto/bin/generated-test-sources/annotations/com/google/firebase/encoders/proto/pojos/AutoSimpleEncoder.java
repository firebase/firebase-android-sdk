package com.google.firebase.encoders.proto.pojos;

import com.google.firebase.encoders.FieldDescriptor;
import com.google.firebase.encoders.ObjectEncoder;
import com.google.firebase.encoders.ObjectEncoderContext;
import com.google.firebase.encoders.config.Configurator;
import com.google.firebase.encoders.config.EncoderConfig;
import com.google.firebase.encoders.proto.AtProtobuf;
import java.io.IOException;
import java.lang.Override;
import java.lang.SuppressWarnings;

@SuppressWarnings("KotlinInternal")
public final class AutoSimpleEncoder implements Configurator {
  public static final int CODEGEN_VERSION = 2;

  public static final Configurator CONFIG = new AutoSimpleEncoder();

  private AutoSimpleEncoder() {
  }

  @Override
  public void configure(EncoderConfig<?> cfg) {
    cfg.registerEncoder(Simple.class, SimpleEncoder.INSTANCE);
  }

  private static final class SimpleEncoder implements ObjectEncoder<Simple> {
    static final SimpleEncoder INSTANCE = new SimpleEncoder();

    private static final FieldDescriptor I32_DESCRIPTOR = FieldDescriptor.builder("i32")
        .withProperty(AtProtobuf.builder()
          .tag(1)
          .build())
        .build();

    private static final FieldDescriptor I64_DESCRIPTOR = FieldDescriptor.builder("i64")
        .withProperty(AtProtobuf.builder()
          .tag(2)
          .build())
        .build();

    private static final FieldDescriptor AFLOAT_DESCRIPTOR = FieldDescriptor.builder("aFloat")
        .withProperty(AtProtobuf.builder()
          .tag(3)
          .build())
        .build();

    private static final FieldDescriptor ADOUBLE_DESCRIPTOR = FieldDescriptor.builder("aDouble")
        .withProperty(AtProtobuf.builder()
          .tag(4)
          .build())
        .build();

    private static final FieldDescriptor UI32_DESCRIPTOR = FieldDescriptor.builder("ui32")
        .withProperty(AtProtobuf.builder()
          .tag(5)
          .build())
        .build();

    private static final FieldDescriptor UI64_DESCRIPTOR = FieldDescriptor.builder("ui64")
        .withProperty(AtProtobuf.builder()
          .tag(6)
          .build())
        .build();

    private static final FieldDescriptor SI32_DESCRIPTOR = FieldDescriptor.builder("si32")
        .withProperty(AtProtobuf.builder()
          .tag(7)
          .intEncoding(com.google.firebase.encoders.proto.Protobuf.IntEncoding.SIGNED)
          .build())
        .build();

    private static final FieldDescriptor SI64_DESCRIPTOR = FieldDescriptor.builder("si64")
        .withProperty(AtProtobuf.builder()
          .tag(8)
          .intEncoding(com.google.firebase.encoders.proto.Protobuf.IntEncoding.SIGNED)
          .build())
        .build();

    @Override
    public void encode(Simple value, ObjectEncoderContext ctx) throws IOException {
      ctx.add(I32_DESCRIPTOR, value.public int getI32() );
      ctx.add(I64_DESCRIPTOR, value.public long getI64() );
      ctx.add(AFLOAT_DESCRIPTOR, value.public float getAFloat() );
      ctx.add(ADOUBLE_DESCRIPTOR, value.public double getADouble() );
      ctx.add(UI32_DESCRIPTOR, value.public int getUi32() );
      ctx.add(UI64_DESCRIPTOR, value.public long getUi64() );
      ctx.add(SI32_DESCRIPTOR, value.public int getSi32() );
      ctx.add(SI64_DESCRIPTOR, value.public long getSi64() );
    }
  }
}
