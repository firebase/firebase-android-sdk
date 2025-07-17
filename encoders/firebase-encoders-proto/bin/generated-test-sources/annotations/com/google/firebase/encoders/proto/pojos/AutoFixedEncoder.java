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
public final class AutoFixedEncoder implements Configurator {
  public static final int CODEGEN_VERSION = 2;

  public static final Configurator CONFIG = new AutoFixedEncoder();

  private AutoFixedEncoder() {
  }

  @Override
  public void configure(EncoderConfig<?> cfg) {
    cfg.registerEncoder(Fixed.class, FixedEncoder.INSTANCE);
  }

  private static final class FixedEncoder implements ObjectEncoder<Fixed> {
    static final FixedEncoder INSTANCE = new FixedEncoder();

    private static final FieldDescriptor F32_DESCRIPTOR = FieldDescriptor.builder("f32")
        .withProperty(AtProtobuf.builder()
          .tag(1)
          .intEncoding(com.google.firebase.encoders.proto.Protobuf.IntEncoding.FIXED)
          .build())
        .build();

    private static final FieldDescriptor SF32_DESCRIPTOR = FieldDescriptor.builder("sf32")
        .withProperty(AtProtobuf.builder()
          .tag(2)
          .intEncoding(com.google.firebase.encoders.proto.Protobuf.IntEncoding.FIXED)
          .build())
        .build();

    private static final FieldDescriptor F64_DESCRIPTOR = FieldDescriptor.builder("f64")
        .withProperty(AtProtobuf.builder()
          .tag(3)
          .intEncoding(com.google.firebase.encoders.proto.Protobuf.IntEncoding.FIXED)
          .build())
        .build();

    private static final FieldDescriptor SF64_DESCRIPTOR = FieldDescriptor.builder("sf64")
        .withProperty(AtProtobuf.builder()
          .tag(4)
          .intEncoding(com.google.firebase.encoders.proto.Protobuf.IntEncoding.FIXED)
          .build())
        .build();

    @Override
    public void encode(Fixed value, ObjectEncoderContext ctx) throws IOException {
      ctx.add(F32_DESCRIPTOR, value.public int getF32() );
      ctx.add(SF32_DESCRIPTOR, value.public int getSf32() );
      ctx.add(F64_DESCRIPTOR, value.public long getF64() );
      ctx.add(SF64_DESCRIPTOR, value.public long getSf64() );
    }
  }
}
