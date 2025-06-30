package com.google.firebase.dataconnect.minimaldemo.test

import android.os.Trace
import com.google.firebase.firestore.util.Util
import io.kotest.property.Arb
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.printableAscii
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.nanoseconds

private const val ITERATION_COUNT = 5000
private const val LIST_SIZE = 2_000

suspend fun utf8PerformanceIntegrationTest(): TestResult {
  val originalTimes = mutableListOf<Duration>()
  val slowTimes = mutableListOf<Duration>()
  val newTimes = mutableListOf<Duration>()
  val denverTimes = mutableListOf<Duration>()
  val timesArb = Arb.of(originalTimes, slowTimes, newTimes, denverTimes)
  val nameArb = Arb.string(5..5, Codepoint.printableAscii())

  checkAll(ITERATION_COUNT, timesArb, Arb.int(0..LIST_SIZE), nameArb, nameArb) {
    list,
    insertIndex,
    insertString,
    prefix
    ->
    val tester = PerformanceTester(insertIndex = insertIndex, insertString = insertString, prefix=prefix)

    val testDuration =
      if (list === originalTimes) {
        tester.runTest { s1, s2 ->
          Trace.beginSection("compareUtf8StringsOriginal")
          try {
            Util.compareUtf8StringsOriginal(s1, s2)
          } finally {
            Trace.endSection()
          }
        }
      } else if (list === slowTimes) {
        tester.runTest { s1, s2 ->
          Trace.beginSection("compareUtf8StringsSlow")
          try {
            Util.compareUtf8StringsSlow(s1, s2)
          } finally {
            Trace.endSection()
          }
        }
      } else if (list === newTimes) {
        tester.runTest { s1, s2 ->
          Trace.beginSection("compareUtf8Strings")
          try {
            Util.compareUtf8Strings(s1, s2)
          } finally {
            Trace.endSection()
          }
        }
      } else if (list === denverTimes) {
        tester.runTest { s1, s2 ->
          Trace.beginSection("compareUtf8StringsDenver")
          try {
            Util.compareUtf8StringsDenver(s1, s2)
          } finally {
            Trace.endSection()
          }
        }
      } else {
        throw Exception("unknown list: $list [hgxsq8tnwd]")
      }

    list.add(testDuration)
  }

  return TestResult(
    original = TestResult.Result.fromTimes(originalTimes),
    slow = TestResult.Result.fromTimes(slowTimes),
    new = TestResult.Result.fromTimes(newTimes),
    denver = TestResult.Result.fromTimes(denverTimes),
  )
}

private class PerformanceTester(val insertIndex: Int, val insertString: String, val prefix: String) {

  inline fun runTest(crossinline compareFunc: (s1: String, s2: String) -> Int): Duration {
    val unsortedList = makeStringList(prefix)
    unsortedList.add(insertIndex, insertString)
    val startTimeNs = System.nanoTime()

    unsortedList.sortWith { s1, s2 -> compareFunc(s1, s2) }

    val endTimeNs = System.nanoTime()
    val elapsedTimeNs = endTimeNs - startTimeNs
    return elapsedTimeNs.nanoseconds
  }
}

private fun makeStringList(prefix: String): MutableList<String> {
  return MutableList(LIST_SIZE) { "/projects/asdfhasdkfjk/database/asdfsadf/items/$prefix$it" }
}

data class TestResult(val original: Result, val slow: Result, val new: Result, val denver: Result) {
  data class Result(val n: Int, val average: Duration) {
    companion object
  }
}

private fun TestResult.Result.Companion.fromTimes(times: List<Duration>): TestResult.Result {
  val average = times.map { it.inWholeMicroseconds }.average().roundToLong().microseconds
  return TestResult.Result(n = times.size, average = average)
}
