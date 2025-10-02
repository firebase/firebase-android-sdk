/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.dataconnect.testutil.property.arbitrary

import com.google.protobuf.ListValue
import com.google.protobuf.NullValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.Sample
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.choose
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.string
import io.kotest.property.asSample

object Proto

val Arb.Companion.proto: Proto
  get() = Proto

private enum class ValueType {
  Boolean,
  String,
  Double,
  List,
  Map,
  Null,
}

private class ProtoValueArb(
  val valueType: Arb<ValueType>,
  val boolean: Arb<Boolean>,
  val string: Arb<String>,
  val number: Arb<Double>,
  private val listFactory: ProtoValueArb.() -> Arb<ListValue>,
  private val structFactory: ProtoValueArb.() -> Arb<Struct>,
) : Arb<Value>() {
  val list: Arb<ListValue> by lazy(LazyThreadSafetyMode.PUBLICATION) { listFactory(this) }

  val struct: Arb<Struct> by lazy(LazyThreadSafetyMode.PUBLICATION) { structFactory(this) }

  override fun edgecase(rs: RandomSource) = null // no edge cases

  override fun sample(rs: RandomSource): Sample<Value> {
    val builder = Value.newBuilder()
    when (valueType.next(rs)) {
      ValueType.Boolean -> builder.setBoolValue(boolean.next(rs))
      ValueType.String -> builder.setStringValue(string.next(rs))
      ValueType.Double -> builder.setNumberValue(number.next(rs))
      ValueType.List -> builder.setListValue(list.next(rs))
      ValueType.Map -> builder.setStructValue(struct.next(rs))
      ValueType.Null -> builder.setNullValue(NullValue.NULL_VALUE)
    }
    return builder.build().asSample()
  }

  companion object {
    fun newInstance(): ProtoValueArb {
      val string: Arb<String> = Arb.string(0..100, Codepoint.alphanumeric())
      val size: Arb<Int> = Arb.choose(1 to 3, 3 to 2, 5 to 1, 6 to 0)

      fun listFactory(value: ProtoValueArb): Arb<ListValue> =
        ProtoListValueArb(size = size, value = value)

      fun structFactory(value: ProtoValueArb): Arb<Struct> =
        ProtoStructValueArb(key = string, size = size, value = value)

      return ProtoValueArb(
        valueType = Arb.enum(),
        boolean = Arb.boolean(),
        string = string,
        number = Arb.double(),
        listFactory = ::listFactory,
        structFactory = ::structFactory,
      )
    }
  }
}

private class ProtoListValueArb(
  val size: Arb<Int>,
  val value: Arb<Value>,
) : Arb<ListValue>() {
  override fun edgecase(rs: RandomSource) = ListValue.getDefaultInstance()!!

  override fun sample(rs: RandomSource): Sample<ListValue> {
    val builder = ListValue.newBuilder()
    repeat(size.next(rs)) { builder.addValues(value.next(rs)) }
    return builder.build().asSample()
  }
}

private class ProtoStructValueArb(
  val key: Arb<String>,
  val size: Arb<Int>,
  val value: Arb<Value>,
) : Arb<Struct>() {
  override fun edgecase(rs: RandomSource) = Struct.getDefaultInstance()!!

  override fun sample(rs: RandomSource): Sample<Struct> {
    val builder = Struct.newBuilder()
    repeat(size.next(rs)) { builder.putFields(key.next(rs), value.next(rs)) }
    return builder.build().asSample()
  }
}

fun Proto.value(): Arb<Value> = ProtoValueArb.newInstance()

fun Proto.struct(): Arb<Struct> = ProtoValueArb.newInstance().struct

fun Proto.list(): Arb<ListValue> = ProtoValueArb.newInstance().list
