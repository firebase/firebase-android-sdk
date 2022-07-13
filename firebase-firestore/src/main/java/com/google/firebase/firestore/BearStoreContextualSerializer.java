package com.google.firebase.firestore;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import kotlin.Unit;
import kotlin.collections.CollectionsKt;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.internal.Intrinsics;
import kotlin.reflect.KClass;
import kotlinx.serialization.KSerializer;
import kotlinx.serialization.descriptors.ClassSerialDescriptorBuilder;
import kotlinx.serialization.descriptors.ContextAwareKt;
import kotlinx.serialization.descriptors.SerialDescriptor;
import kotlinx.serialization.descriptors.SerialDescriptorsKt;
import kotlinx.serialization.descriptors.SerialKind;
import kotlinx.serialization.encoding.Decoder;
import kotlinx.serialization.encoding.Encoder;

public class BearStoreContextualSerializer implements KSerializer<DocumentReference> {
    @Override
    public DocumentReference deserialize(@NonNull Decoder decoder) {
        return null;
    }


    @Override
    public void serialize(@NonNull Encoder encoder, DocumentReference documentReference) {

    }

    private final KClass serializableClass = kotlin.jvm.JvmClassMappingKt.getKotlinClass(DocumentReference.class);
    private final KSerializer fallbackSerializer = null;

    Function1 var5 = (Function1) (new Function1() {
        // $FF: synthetic method
        // $FF: bridge method
        public Object invoke(Object var1) {
            this.invoke((ClassSerialDescriptorBuilder) var1);
            return Unit.INSTANCE;
        }

        public final void invoke(@NotNull ClassSerialDescriptorBuilder $this$buildSerialDescriptor) {
            List var4;
            label16:
            {
                Intrinsics.checkParameterIsNotNull($this$buildSerialDescriptor, "$receiver");
                KSerializer var10001 = BearStoreContextualSerializer.this.fallbackSerializer;
                if (var10001 != null) {
                    SerialDescriptor var3 = var10001.getDescriptor();
                    if (var3 != null) {
                        var4 = var3.getAnnotations();
                        break label16;
                    }
                }

                var4 = null;
            }

            List var2 = var4;
            var4 = var2;
            if (var2 == null) {
                var4 = CollectionsKt.emptyList();
            }

            $this$buildSerialDescriptor.setAnnotations(var4);
        }
    });


    @NonNull
    @Override
    public SerialDescriptor getDescriptor() {
        return ContextAwareKt.withContext(
                SerialDescriptorsKt.buildSerialDescriptor(
                        "kotlinx.serialization.ContextualSerializer",
                        (SerialKind) SerialKind.CONTEXTUAL.INSTANCE,
                        new SerialDescriptor[0],
                        var5),
                this.serializableClass);
    }


}
//    @NotNull
//    public static final KClass DocRef = Reflection.getOrCreateKotlinClass((new ContextualSerializer(Reflection.getOrCreateKotlinClass(DocumentReference.class))).getClass());
//
//    public static final String GUESTBOOK_URL = "/guestbook";
//
//    @NotNull
//    public static final KClass getDocRef() {
//        return DocRef;
//    }



