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
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.array
import io.kotest.property.arbitrary.az
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.char
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.javaDate
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.pair
import io.kotest.property.arbitrary.set
import io.kotest.property.arbitrary.short
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.triple
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KClass

fun Arb.Companion.any(
  excludes: List<KClass<*>> = emptyList(),
  extras: List<Arb<Any>> = emptyList(),
): Arb<Any> {
  val arbs = arbs(excludes, extras)
  return Arb.choice(arbs.toList())
}

fun Arb.Companion.any(exclude: KClass<*>): Arb<Any> = any(excludes = listOf(exclude))

fun Arb.Companion.any(exclude: KClass<*>, extra: Arb<Any>): Arb<Any> =
  any(excludes = listOf(exclude), extras = listOf(extra))

private class ScalarArbFactory<T : Any>(val type: KClass<T>, val factory: () -> Arb<T>)

private val allScalarArbFactories =
  listOf(
    ScalarArbFactory(String::class) { Arb.string(minSize = 0, maxSize = 10, Codepoint.az()) },
    ScalarArbFactory(Boolean::class, Arb.Companion::boolean),
    ScalarArbFactory(Byte::class, Arb.Companion::byte),
    ScalarArbFactory(Short::class, Arb.Companion::short),
    ScalarArbFactory(Int::class, Arb.Companion::int),
    ScalarArbFactory(Long::class, Arb.Companion::long),
    ScalarArbFactory(Char::class, Arb.Companion::char),
    ScalarArbFactory(Float::class, Arb.Companion::float),
    ScalarArbFactory(Double::class, Arb.Companion::double),
    ScalarArbFactory(MutableBoolean::class, Arb.Companion::mutableBoolean),
    ScalarArbFactory(MutableByte::class, Arb.Companion::mutableByte),
    ScalarArbFactory(MutableShort::class, Arb.Companion::mutableShort),
    ScalarArbFactory(MutableInt::class, Arb.Companion::mutableInt),
    ScalarArbFactory(MutableLong::class, Arb.Companion::mutableLong),
    ScalarArbFactory(MutableChar::class, Arb.Companion::mutableChar),
    ScalarArbFactory(MutableFloat::class, Arb.Companion::mutableFloat),
    ScalarArbFactory(MutableDouble::class, Arb.Companion::mutableDouble),
    ScalarArbFactory(AtomicBoolean::class) { Arb.boolean().map(::AtomicBoolean) },
    ScalarArbFactory(AtomicInteger::class) { Arb.int().map(::AtomicInteger) },
    ScalarArbFactory(AtomicLong::class) { Arb.long().map(::AtomicLong) },
    ScalarArbFactory(Date::class, Arb.Companion::javaDate),
  )

private fun arbs(
  excludes: List<KClass<*>> = emptyList(),
  extras: List<Arb<Any>> = emptyList(),
): Sequence<Arb<Any>> = sequence {
  val scalarArbs = buildList {
    allScalarArbFactories.forEach {
      if (it.type !in excludes) {
        add(it.factory())
      }
      addAll(extras)
    }
  }

  scalarArbs.forEach { yield(it) }
  val scalarArb = Arb.choice(scalarArbs)

  if (Map::class !in excludes) {
    yield(Arb.map(scalarArb, scalarArb, minSize = 0, maxSize = 5))
  }
  if (List::class !in excludes) {
    yield(Arb.list(scalarArb, 0..5))
  }
  if (Set::class !in excludes) {
    yield(Arb.set(scalarArb, 0..5))
  }
  if (Array::class !in excludes) {
    yield(Arb.array(scalarArb, 0..5))
  }
  if (MutableReference::class !in excludes) {
    yield(scalarArb.map(::MutableReference))
  }
  if (AtomicReference::class !in excludes) {
    yield(scalarArb.map(::AtomicReference))
  }
  if (Pair::class !in excludes) {
    yield(Arb.pair(scalarArb, scalarArb))
  }
  if (Triple::class !in excludes) {
    yield(Arb.triple(scalarArb, scalarArb, scalarArb))
  }
}
