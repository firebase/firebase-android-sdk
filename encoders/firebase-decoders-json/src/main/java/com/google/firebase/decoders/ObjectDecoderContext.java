// Copyright 2020 Google LLC
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

package com.google.firebase.decoders;

import androidx.annotation.NonNull;
import com.google.firebase.encoders.FieldDescriptor;

// TODO: decodeInline() and setUnknownBehavior(), implementation
/**
 * {@link ObjectDecoderContext} enables {@link ObjectDecoder} to decode objects. {@link
 * ObjectDecoder} should keep its decoding logic into a {@link ObjectDecoderContext} where each
 * {@link FieldDescriptor} corresponds to a {@link FieldRef}.
 */
public interface ObjectDecoderContext<T> {

  /**
   * Get {@link com.google.firebase.decoders.TypeToken.ClassToken} of decoding type {@code T}.
   *
   * <p>Use it when decode a field which have the same type as the of type {@code T}
   *
   * <p>For Example:
   *
   * <p>if a {@code class NodeObjectDecoder<T> implements ObjectDecoder<Node<T>>}, we want to get
   * its type token {@code Node<T>}, we will do:
   *
   * <p>{@code FieldRef.Boxed<Node<T>> nodeField =
   * objectDecoderContext.decode(FieldDescriptor.of("node"), objectDecoderContext.getTypeToken()); }
   */
  @NonNull
  TypeToken.ClassToken<T> getTypeToken();

  /**
   * Get {@link TypeTokenContainer} of decoding type {@code T} if type {@code T} contains type
   * arguments, otherwise an empty {@link TypeTokenContainer} will be given.
   *
   * <p>Use it when decode a field which have the same type as the of type argument of {@code T}
   *
   * <p>For Example:
   *
   * <p>if a {@code class NodeObjectDecoder<T> implements ObjectDecoder<Node<T>>}, we want to get
   * its type argument {@code T}, we will do:
   *
   * <p>{@code FieldRef.Boxed<T> tField = ctx.decode(FieldDescriptor.of("t"),
   * ctx.getTypeArgument(0)); }
   */
  @NonNull
  <TTypeArgument> TypeToken<TTypeArgument> getTypeArgument(int index);

  /** Decode {@link FieldDescriptor} into a {@link FieldRef} */
  @NonNull
  <TField> FieldRef.Boxed<TField> decode(
      @NonNull FieldDescriptor fileDescriptor, @NonNull TypeToken<TField> typeToken);

  @NonNull
  <TField> FieldRef.Boxed<TField> decodeInline(
      @NonNull TypeToken.ClassToken<TField> classToken,
      @NonNull ObjectDecoder<TField> objectDecoder);

  /** Decode {@link FieldDescriptor} into a {@link FieldRef} */
  @NonNull
  FieldRef.Primitive<Boolean> decodeBoolean(@NonNull FieldDescriptor fileDescriptor);

  /** Decode {@link FieldDescriptor} into a {@link FieldRef} */
  @NonNull
  FieldRef.Primitive<Integer> decodeInteger(@NonNull FieldDescriptor fileDescriptor);

  /** Decode {@link FieldDescriptor} into a {@link FieldRef} */
  @NonNull
  FieldRef.Primitive<Short> decodeShort(@NonNull FieldDescriptor fileDescriptor);

  /** Decode {@link FieldDescriptor} into a {@link FieldRef} */
  @NonNull
  FieldRef.Primitive<Long> decodeLong(@NonNull FieldDescriptor fileDescriptor);

  /** Decode {@link FieldDescriptor} into a {@link FieldRef} */
  @NonNull
  FieldRef.Primitive<Float> decodeFloat(@NonNull FieldDescriptor fileDescriptor);

  /** Decode {@link FieldDescriptor} into a {@link FieldRef} */
  @NonNull
  FieldRef.Primitive<Double> decodeDouble(@NonNull FieldDescriptor fileDescriptor);

  /** Decode {@link FieldDescriptor} into a {@link FieldRef} */
  @NonNull
  FieldRef.Primitive<Character> decodeChar(@NonNull FieldDescriptor fileDescriptor);
}
