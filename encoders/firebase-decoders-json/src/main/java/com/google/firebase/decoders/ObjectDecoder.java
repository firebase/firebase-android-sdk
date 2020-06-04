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

/**
 * This interface should be implemented by a specific ObjectDecoder to provide its decoding logic.
 *
 * <p>Decoding logic is written into corresponding {@link ObjectDecoderContext}
 *
 * <p>For Example:
 *
 * <pre>{@code
 * class Foo<T> {
 *   int a;
 *   boolean b;
 *   T t;
 *
 *   Foo(int a, boolean b, T t) {
 *     this.a = a;
 *     this.b = b;
 *     this.t = t;
 *   }
 * }
 *
 * class FooObjectDecoder<T> implements ObjectDecoder<Foo<T>> {
 *   @Override
 *   public TypeCreator<Foo<T>> decode(@NonNull ObjectDecoderContext<Foo<T>> ctx) {
 *     FieldRef.Primitive<Integer> aField = ctx.decodeInteger(FieldDescriptor.of("a"));
 *     FieldRef.Primitive<Boolean> bField = ctx.decodeBoolean(FieldDescriptor.of("b"));
 *     FieldRef.Boxed<T> tField = ctx.decode(FieldDescriptor.of("t"), ctx.getTypeArguments().at(0));
 *     FieldRef.Boxed<SubFoo<String>> subFooField =
 *         ctx.decode(FieldDescriptor.of("subFoo"), TypeToken.of(new Safe<SubFoo<String>>() {}));
 *
 *     return (creationCtx ->
 *         new Foo<T>(
 *             creationCtx.getInteger(aField),
 *             creationCtx.getBoolean(bField),
 *             (T) creationCtx.get(tField)));
 *   }
 * }
 *
 * }</pre>
 */
public interface ObjectDecoder<T> {
  /**
   * Should be override by specific ObjectDecoder to provide its decoding logic and Type Creation.
   */
  @NonNull
  TypeCreator<T> decode(@NonNull ObjectDecoderContext<T> ctx);
}
