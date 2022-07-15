// Copyright 2022 Google LLC
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

package com.google.firebase.firestore.encoding;

import java.util.Date;
import kotlin.jvm.internal.Reflection;
import kotlinx.serialization.KSerializer;
import kotlinx.serialization.modules.SerializersModule;
import kotlinx.serialization.modules.SerializersModuleBuilder;
import org.jetbrains.annotations.NotNull;

/**
 * The {@code SerializersModule} that provides a {@code KSerializer} at runtime for data type: {code
 * Date}
 *
 * <p>Serializers inside of {@code SerializersModule} will be registered by the kotlin compiler
 * plugin at compile time. Then, at runtime, the pre-registered serializers will be matched and
 * utilized by the Encoder(Decoder), if the property of the custom object has the @{@code
 * Contextual} annotation.
 */
public final class FirestoreSerializersModule {
  @NotNull private static final SerializersModule FirestoreSerializersModule;

  @NotNull
  public static SerializersModule getFirestoreSerializersModule() {
    return FirestoreSerializersModule;
  }

  static {
    SerializersModuleBuilder builder = new SerializersModuleBuilder();
    KSerializer serializer =
        (KSerializer) FirestoreNativeDataTypeSerializer.DateSerializer.INSTANCE;
    builder.contextual(Reflection.getOrCreateKotlinClass(Date.class), serializer);
    FirestoreSerializersModule = builder.build();
  }
}
