package com.google.firebase.firestore

import com.google.firebase.firestore.util.Util2
import com.google.firebase.firestore.util.Util3
import io.kotest.property.Arb
import io.kotest.property.arbitrary.of
import io.kotest.property.checkAll
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlinx.coroutines.test.runTest
import org.junit.Test

const val ITERATION_COUNT = 300
const val LIST_SIZE = 2_000

class Utf8PerformanceIntegrationTest {

  @Test
  fun test() =
    runTest(timeout = Duration.INFINITE) {
      val originalTimes = mutableListOf<Long>()
      val slowTimes = mutableListOf<Long>()
      val newTimes = mutableListOf<Long>()
      val timesArb = Arb.of(originalTimes, slowTimes, newTimes)

      checkAll(ITERATION_COUNT, timesArb) { list ->
        val startTimeNs = System.nanoTime()

        if (list === originalTimes) {
          doTest { s1, s2 -> s1.compareTo(s2) }
        } else if (list === slowTimes) {
          doTest { s1, s2 -> Util2.compareUtf8Strings(s1, s2) }
        } else if (list === newTimes) {
          doTest { s1, s2 -> Util3.compareUtf8Strings(s1, s2) }
        } else {
          throw Exception("unknown list: $list [hgxsq8tnwd]")
        }

        val endTimeNs = System.nanoTime()
        val elapsedTimeNs = endTimeNs - startTimeNs
        list.add(elapsedTimeNs)
      }

      logTimes("original", originalTimes)
      logTimes("new-slow", slowTimes)
      logTimes("new-fast", newTimes)
      logTimes("new-denver", emptyList())
    }

  private inline fun doTest(crossinline compareFunc: (s1: String, s2: String) -> Int) {
    strings.sortedWith({ s1, s2 -> compareFunc(s1, s2) })
  }

  private companion object {

    val strings = List(LIST_SIZE) { "/projects/asdfhasdkfjk/database/asdfsadf/items/item$it" }

    fun logTimes(name: String, list: List<Long>) {
      val averageMs = list.average().roundToLong() / 1_000_000
      println("$name: ${averageMs}ms (n=${list.size})")
    }
  }
}
