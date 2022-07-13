package com.google.firebase.firestore;

import kotlin.Metadata;
import kotlin.collections.CollectionsKt;
import kotlin.jvm.internal.Reflection;
import kotlin.reflect.KClass;
import kotlinx.serialization.ContextualSerializer;
import kotlinx.serialization.DeserializationStrategy;
import kotlinx.serialization.KSerializer;
import kotlinx.serialization.SerializationStrategy;
import kotlinx.serialization.descriptors.SerialDescriptor;
import kotlinx.serialization.encoding.Decoder;
import kotlinx.serialization.encoding.Encoder;
import kotlinx.serialization.modules.SerializersModule;
import org.jetbrains.annotations.NotNull;

@Metadata(
    mv = {1, 6, 0},
    k = 1,
    d1 = {
      "\u0000H\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0011\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0001\n\u0000\bÆ\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002¢\u0006\u0002\u0010\u0003J\u0010\u0010\u000e\u001a\u00020\u00022\u0006\u0010\u000f\u001a\u00020\u0010H\u0016J\u0016\u0010\u0011\u001a\b\u0012\u0004\u0012\u00020\u00020\u00012\u0006\u0010\u0012\u001a\u00020\u0013H\u0002J\u0018\u0010\u0014\u001a\u00020\u00152\u0006\u0010\u0016\u001a\u00020\u00172\u0006\u0010\u0018\u001a\u00020\u0002H\u0016J\u0010\u0010\u0019\u001a\u00020\u001a*\u0006\u0012\u0002\b\u00030\nH\u0002R\u0014\u0010\u0004\u001a\u00020\u00058VX\u0096\u0004¢\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007R\u0016\u0010\b\u001a\n\u0012\u0004\u0012\u00020\u0002\u0018\u00010\u0001X\u0082\u0004¢\u0006\u0002\n\u0000R\u0014\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u00020\nX\u0082\u0004¢\u0006\u0002\n\u0000R\u001a\u0010\u000b\u001a\f\u0012\b\u0012\u0006\u0012\u0002\b\u00030\u00010\fX\u0082\u0004¢\u0006\u0004\n\u0002\u0010\r¨\u0006\u001b"
    },
    d2 = {
      "Lcom/google/firebase/firestore/DocumentRefContextualSerializer;",
      "Lkotlinx/serialization/KSerializer;",
      "Lcom/google/firebase/firestore/DocumentReference;",
      "()V",
      "descriptor",
      "Lkotlinx/serialization/descriptors/SerialDescriptor;",
      "getDescriptor",
      "()Lkotlinx/serialization/descriptors/SerialDescriptor;",
      "fallbackSerializer",
      "serializableClass",
      "Lkotlin/reflect/KClass;",
      "typeArgumentsSerializers",
      "",
      "[Lkotlinx/serialization/KSerializer;",
      "deserialize",
      "decoder",
      "Lkotlinx/serialization/encoding/Decoder;",
      "innerserializer",
      "serializersModule",
      "Lkotlinx/serialization/modules/SerializersModule;",
      "serialize",
      "",
      "encoder",
      "Lkotlinx/serialization/encoding/Encoder;",
      "value",
      "serializerNotRegistered",
      "",
      "com.google.firebase-firebase-firestore"
    })
public final class DocumentRefContextualSerializer implements KSerializer<DocumentReference> {
  static {
    INSTANCE = new DocumentRefContextualSerializer();
    serializableClass = Reflection.getOrCreateKotlinClass(DocumentReference.class);
  }

  private static final KClass serializableClass;
  @NotNull public static final DocumentRefContextualSerializer INSTANCE;

  private final KSerializer serializer(SerializersModule serializersModule) {
    return serializersModule.getContextual(serializableClass, CollectionsKt.emptyList());
  }

  @NotNull
  public SerialDescriptor getDescriptor() {
    return (new ContextualSerializer(Reflection.getOrCreateKotlinClass(DocumentReference.class)))
        .getDescriptor();
  }

  @NotNull
  public DocumentReference deserialize(@NotNull Decoder decoder) {
    return (DocumentReference)
        decoder.decodeSerializableValue(
            (DeserializationStrategy) this.serializer(decoder.getSerializersModule()));
  }

  public void serialize(@NotNull Encoder encoder, @NotNull DocumentReference value) {
    encoder.encodeSerializableValue(
        (SerializationStrategy) this.serializer(encoder.getSerializersModule()), value);
  }
}
