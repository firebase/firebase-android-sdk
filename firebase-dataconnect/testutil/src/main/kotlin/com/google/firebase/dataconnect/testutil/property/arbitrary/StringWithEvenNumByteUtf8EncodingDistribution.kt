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

import io.kotest.property.Arb
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string

fun Arb.Companion.codepointWith1ByteUtf8Encoding(): Arb<Codepoint> =
  int(0 until 0x80).map(::Codepoint)

fun Arb.Companion.codepointWith2ByteUtf8Encoding(): Arb<Codepoint> =
  int(0x80 until 0x800).map(::Codepoint)

fun Arb.Companion.codepointWith3ByteUtf8Encoding(): Arb<Codepoint> =
  Arb.choice(
      int(0x800 until 0xd800),
      int(0xe000 until 0x10000),
    )
    .map(::Codepoint)

fun Arb.Companion.codepointWith4ByteUtf8Encoding(): Arb<Codepoint> =
  int(0x10000..0x10FFFF).map(::Codepoint)

fun Arb.Companion.codepointWithEvenNumByteUtf8EncodingDistribution(): Arb<Codepoint> =
  Arb.choice(
    codepointWith1ByteUtf8Encoding(),
    codepointWith2ByteUtf8Encoding(),
    codepointWith3ByteUtf8Encoding(),
    codepointWith4ByteUtf8Encoding(),
  )

fun Arb.Companion.stringWithEvenNumByteUtf8EncodingDistribution(length: IntRange): Arb<String> =
  string(length, codepointWithEvenNumByteUtf8EncodingDistribution())
