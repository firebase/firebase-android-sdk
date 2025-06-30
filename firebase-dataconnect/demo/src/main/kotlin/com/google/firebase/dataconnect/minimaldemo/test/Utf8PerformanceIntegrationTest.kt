package com.google.firebase.dataconnect.minimaldemo.test

import com.google.firebase.firestore.util.Util
import io.kotest.property.Arb
import io.kotest.property.arbitrary.of
import io.kotest.property.checkAll
import kotlin.math.roundToLong

private const val ITERATION_COUNT = 500
private const val LIST_SIZE = 10_000

suspend fun utf8PerformanceIntegrationTest(): TestResult {
  val originalTimes = mutableListOf<Long>()
  val slowTimes = mutableListOf<Long>()
  val newTimes = mutableListOf<Long>()
  val denverTimes = mutableListOf<Long>()
  val timesArb = Arb.of(originalTimes, slowTimes, newTimes, denverTimes)

  checkAll(ITERATION_COUNT, timesArb) { list ->
    val startTimeNs = System.nanoTime()

    if (list === originalTimes) {
      doTest { s1, s2 -> Util.compareUtf8StringsOriginal(s1, s2) }
    } else if (list === slowTimes) {
      doTest { s1, s2 -> Util.compareUtf8StringsSlow(s1, s2) }
    } else if (list === newTimes) {
      doTest { s1, s2 -> Util.compareUtf8Strings(s1, s2) }
    } else if (list === denverTimes) {
      doTest { s1, s2 -> Util.compareUtf8StringsDenver(s1, s2) }
    } else {
      throw Exception("unknown list: $list [hgxsq8tnwd]")
    }

    val endTimeNs = System.nanoTime()
    val elapsedTimeNs = endTimeNs - startTimeNs
    list.add(elapsedTimeNs)
  }

  return TestResult(
    original = TestResult.Result.fromLogTimes(originalTimes),
    slow = TestResult.Result.fromLogTimes(slowTimes),
    new = TestResult.Result.fromLogTimes(newTimes),
    denver = TestResult.Result.fromLogTimes(denverTimes),
  )
}

private inline fun doTest(crossinline compareFunc: (s1: String, s2: String) -> Int) {
  strings.sortedWith { s1, s2 -> compareFunc(s1, s2) }
}

private val strings = List(LIST_SIZE) { "/projects/asdfhasdkfjk/database/asdfsadf/items/item$it" }

data class TestResult(val original: Result, val slow: Result, val new: Result, val denver: Result) {
  data class Result(val n: Int, val averageMs: Long) {
    companion object
  }
}

private fun TestResult.Result.Companion.fromLogTimes(times: List<Long>): TestResult.Result {
  val averageMs = times.average().roundToLong() / 1_000_000
  return TestResult.Result(n = times.size, averageMs = averageMs)
}
