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
public final class AutoOtherTypesEncoder implements Configurator {
  public static final int CODEGEN_VERSION = 2;

  public static final Configurator CONFIG = new AutoOtherTypesEncoder();

  private AutoOtherTypesEncoder() {
  }

  @Override
  public void configure(EncoderConfig<?> cfg) {
    cfg.registerEncoder(OtherTypes.class, OtherTypesEncoder.INSTANCE);
  }

  private static final class OtherTypesEncoder implements ObjectEncoder<OtherTypes> {
    static final OtherTypesEncoder INSTANCE = new OtherTypesEncoder();

    private static final FieldDescriptor STR_DESCRIPTOR = FieldDescriptor.builder("str")
        .withProperty(AtProtobuf.builder()
          .tag(1)
          .build())
        .build();

    private static final FieldDescriptor BYTES_DESCRIPTOR = FieldDescriptor.builder("bytes")
        .withProperty(AtProtobuf.builder()
          .tag(2)
          .build())
        .build();

    private static final FieldDescriptor BOOL_DESCRIPTOR = FieldDescriptor.builder("bool")
        .withProperty(AtProtobuf.builder()
          .tag(3)
          .build())
        .build();

    private static final FieldDescriptor WRAPPEDBOOL_DESCRIPTOR = FieldDescriptor.builder("wrappedBool")
        .withProperty(AtProtobuf.builder()
          .tag(4)
          .build())
        .build();

    @Override
    public void encode(OtherTypes value, ObjectEncoderContext ctx) throws IOException {
      ctx.add(STR_DESCRIPTOR, value.public java.lang.String getStr() );
      ctx.add(BYTES_DESCRIPTOR, value.public byte[] getBytes() );
      ctx.add(BOOL_DESCRIPTOR, value.public boolean isBool() );
      ctx.add(WRAPPEDBOOL_DESCRIPTOR, value.public java.lang.Boolean getWrappedBool() );
    }
  }
}
