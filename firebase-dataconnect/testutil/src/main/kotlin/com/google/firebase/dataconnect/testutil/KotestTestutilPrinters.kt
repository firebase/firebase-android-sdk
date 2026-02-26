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

package com.google.firebase.dataconnect.testutil

import com.google.protobuf.Duration as DurationProto
import io.kotest.assertions.print.Print
import io.kotest.assertions.print.Printed
import io.kotest.assertions.print.Printers
import io.kotest.assertions.print.print
import io.kotest.assertions.print.printed
import kotlin.time.Duration
import org.apache.commons.statistics.inference.SignificanceResult

@Suppress("SpellCheckingInspection")
fun registerDataConnectKotestTestutilPrinters() {
  Printers.add(DurationProto::class, DurationProtoPrint)
  Printers.add(Duration::class, KotlinTimeDurationPrint)
  Printers.add(Pair::class, PairPrint)
  Printers.add(Triple::class, TriplePrint)
  Printers.add(Quadruple::class, QuadruplePrint)

  try {
    Printers.add(SignificanceResult::class, SignificanceResultPrint)
  } catch (e: NoClassDefFoundError) {
    // ignore, since org.apache.commons:commons-statistics-inference is a compileOnly dependency
  }
}

private object DurationProtoPrint : Print<DurationProto> {

  @Suppress("OVERRIDE_DEPRECATION")
  override fun print(a: DurationProto): Printed = a.printString.printed()

  private val DurationProto.printString: String
    get() = "DurationProto(seconds=$seconds, nanos=$nanos)"
}

private object KotlinTimeDurationPrint : Print<Duration> {

  @Suppress("OVERRIDE_DEPRECATION")
  override fun print(a: Duration): Printed = a.printString.printed()

  private val Duration.printString: String
    get() = toComponents { seconds, nanos ->
      "kotlin.time.Duration(seconds=$seconds, nanos=$nanos)"
    }
}

private object PairPrint : Print<Pair<*, *>> {

  @Suppress("OVERRIDE_DEPRECATION")
  override fun print(a: Pair<*, *>): Printed = a.printString.printed()

  private val Pair<*, *>.printString: String
    get() = "Pair(${first.print().value}, ${second.print().value})"
}

private object TriplePrint : Print<Triple<*, *, *>> {

  @Suppress("OVERRIDE_DEPRECATION")
  override fun print(a: Triple<*, *, *>): Printed = a.printString.printed()

  private val Triple<*, *, *>.printString: String
    get() = "Triple(${first.print().value}, ${second.print().value}, ${third.print().value})"
}

private object QuadruplePrint : Print<Quadruple<*, *, *, *>> {

  @Suppress("OVERRIDE_DEPRECATION")
  override fun print(a: Quadruple<*, *, *, *>): Printed = a.printString.printed()

  private val Quadruple<*, *, *, *>.printString: String
    get() =
      "Quadruple(${first.print().value}, ${second.print().value}, " +
        "${third.print().value}, ${fourth.print().value})"
}

private object SignificanceResultPrint : Print<SignificanceResult> {

  @Suppress("OVERRIDE_DEPRECATION")
  override fun print(a: SignificanceResult): Printed = a.printString.printed()

  private val SignificanceResult.printString: String
    get() =
      "SignificanceResult(statistic=${statistic.print().value}, pValue=${pValue.print().value})"
}
