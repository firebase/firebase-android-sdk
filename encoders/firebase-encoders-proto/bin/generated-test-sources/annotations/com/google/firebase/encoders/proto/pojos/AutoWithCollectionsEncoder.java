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
public final class AutoWithCollectionsEncoder implements Configurator {
  public static final int CODEGEN_VERSION = 2;

  public static final Configurator CONFIG = new AutoWithCollectionsEncoder();

  private AutoWithCollectionsEncoder() {
  }

  @Override
  public void configure(EncoderConfig<?> cfg) {
    cfg.registerEncoder(WithCollections.class, WithCollectionsEncoder.INSTANCE);
    cfg.registerEncoder(Fixed.class, FixedEncoder.INSTANCE);
    cfg.registerEncoder(OtherTypes.class, OtherTypesEncoder.INSTANCE);
  }

  private static final class WithCollectionsEncoder implements ObjectEncoder<WithCollections> {
    static final WithCollectionsEncoder INSTANCE = new WithCollectionsEncoder();

    private static final FieldDescriptor VALUE_DESCRIPTOR = FieldDescriptor.builder("value")
        .withProperty(AtProtobuf.builder()
          .tag(1)
          .build())
        .build();

    private static final FieldDescriptor MYMAP_DESCRIPTOR = FieldDescriptor.builder("myMap")
        .withProperty(AtProtobuf.builder()
          .tag(2)
          .build())
        .build();

    private static final FieldDescriptor OTHERTYPES_DESCRIPTOR = FieldDescriptor.builder("otherTypes")
        .withProperty(AtProtobuf.builder()
          .tag(3)
          .build())
        .build();

    @Override
    public void encode(WithCollections value, ObjectEncoderContext ctx) throws IOException {
      ctx.add(VALUE_DESCRIPTOR, value.public java.lang.String getValue() );
      ctx.add(MYMAP_DESCRIPTOR, value.public Map<java.lang.String,com.google.firebase.encoders.proto.pojos.Fixed> getMyMap() );
      ctx.add(OTHERTYPES_DESCRIPTOR, value.public List<com.google.firebase.encoders.proto.pojos.OtherTypes> getOtherTypes() );
    }
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
