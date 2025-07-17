package com.google.firebase.encoders.proto;

import java.lang.Class;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.annotation.Annotation;

public final class AtProtobuf {
  private int tag;

  private Protobuf.IntEncoding intEncoding = Protobuf.IntEncoding.DEFAULT;

  public AtProtobuf tag(int tag) {
    this.tag = tag;
    return this;
  }

  public AtProtobuf intEncoding(Protobuf.IntEncoding intEncoding) {
    this.intEncoding = intEncoding;
    return this;
  }

  public static AtProtobuf builder() {
    return new AtProtobuf();
  }

  public Protobuf build() {
    return new ProtobufImpl(tag, intEncoding);
  }

  private static final class ProtobufImpl implements Protobuf {
    private final int tag;

    private final Protobuf.IntEncoding intEncoding;

    ProtobufImpl(int tag, Protobuf.IntEncoding intEncoding) {
      this.tag = tag;
      this.intEncoding = intEncoding;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
      return Protobuf.class;
    }

    @Override
    public int tag() {
      return tag;
    }

    @Override
    public Protobuf.IntEncoding intEncoding() {
      return intEncoding;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) return true;
      if (!(other instanceof Protobuf)) return false;
      Protobuf that = (Protobuf) other;

      return (tag == that.tag())
          && (intEncoding.equals(that.intEncoding()));
    }

    @Override
    public int hashCode() {
      return + (14552422 ^ ((int)tag))
      + (2041407134 ^ intEncoding.hashCode())
      ;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder("@com.google.firebase.encoders.proto.Protobuf");
      sb.append('(');
      sb.append("tag=").append(tag);
      sb.append("intEncoding=").append(intEncoding);
      sb.append(')');
      return sb.toString();
    }
  }
}
