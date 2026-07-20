/*
 * Copyright 2026 Google LLC
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

@file:Suppress("UnusedReceiverParameter")

package com.google.firebase.dataconnect.testutil.property.arbitrary

import com.google.firebase.dataconnect.testutil.MutableBoolean
import com.google.firebase.dataconnect.testutil.MutableByte
import com.google.firebase.dataconnect.testutil.MutableChar
import com.google.firebase.dataconnect.testutil.MutableDouble
import com.google.firebase.dataconnect.testutil.MutableFloat
import com.google.firebase.dataconnect.testutil.MutableInt
import com.google.firebase.dataconnect.testutil.MutableLong
import com.google.firebase.dataconnect.testutil.MutableReference
import com.google.firebase.dataconnect.testutil.MutableShort
import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.char
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.short

fun Arb.Companion.mutableBoolean(boolean: Arb<Boolean> = Arb.boolean()): Arb<MutableBoolean> =
  boolean.map(::MutableBoolean)

fun Arb.Companion.mutableByte(byte: Arb<Byte> = Arb.byte()): Arb<MutableByte> =
  byte.map(::MutableByte)

fun Arb.Companion.mutableShort(short: Arb<Short> = Arb.short()): Arb<MutableShort> =
  short.map(::MutableShort)

fun Arb.Companion.mutableInt(int: Arb<Int> = Arb.int()): Arb<MutableInt> = int.map(::MutableInt)

fun Arb.Companion.mutableLong(long: Arb<Long> = Arb.long()): Arb<MutableLong> =
  long.map(::MutableLong)

fun Arb.Companion.mutableChar(char: Arb<Char> = Arb.char()): Arb<MutableChar> =
  char.map(::MutableChar)

fun Arb.Companion.mutableFloat(float: Arb<Float> = Arb.float()): Arb<MutableFloat> =
  float.map(::MutableFloat)

fun Arb.Companion.mutableDouble(double: Arb<Double> = Arb.double()): Arb<MutableDouble> =
  double.map(::MutableDouble)

fun Arb.Companion.mutableReference(any: Arb<Any> = Arb.any()): Arb<MutableReference<*>> =
  any.map(::MutableReference)
