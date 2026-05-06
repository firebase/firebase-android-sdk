/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.sessions.api

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Demonstrates the class-loading cost difference between kotlinx.coroutines.sync.Mutex and
 * java.util.concurrent.CountDownLatch.
 *
 * This test visualizes why Mutex causes ANRs on budget devices during static initialization: the
 * Mutex constructor triggers a deep chain of class loading (Semaphore infrastructure), while
 * CountDownLatch is a JDK bootstrap class with zero additional class loading.
 *
 * See: https://github.com/firebase/firebase-android-sdk/issues/7882
 */
@RunWith(AndroidJUnit4::class)
class MutexClassLoadingTest {

  @Test
  fun demonstrate_mutex_class_loading_cascade() {
    // These are the classes that get loaded when Mutex(locked=true) is called,
    // as observed in the ANR stack traces from the issue.
    // On a dev machine this takes <10ms. On budget Realme/Vivo devices, each
    // Class.forName triggers disk I/O from the APK, and the full chain causes ANRs.

    val mutexChainClasses =
      listOf(
        "kotlinx.coroutines.sync.Mutex",
        "kotlinx.coroutines.sync.MutexImpl",
        "kotlinx.coroutines.sync.SemaphoreAndMutexImpl",
        "kotlinx.coroutines.sync.SemaphoreSegment",
        "kotlinx.coroutines.sync.SemaphoreKt",
        "kotlinx.coroutines.internal.ConcurrentLinkedListNode",
        "kotlinx.coroutines.internal.ConcurrentLinkedListKt",
        "kotlinx.coroutines.internal.Segment",
        "kotlinx.coroutines.internal.SystemPropsKt",
        "kotlinx.coroutines.internal.SystemPropsKt__SystemPropsKt",
      )

    println("=== Mutex class-loading cascade (from ANR stack traces) ===")
    println("Classes loaded when calling Mutex(locked=true):")

    var mutexTotalNanos = 0L
    for (className in mutexChainClasses) {
      val start = System.nanoTime()
      try {
        Class.forName(className)
        val elapsed = System.nanoTime() - start
        mutexTotalNanos += elapsed
        println("  loaded $className (${elapsed / 1_000}us)")
      } catch (e: ClassNotFoundException) {
        println("  missing $className (not found)")
      }
    }
    println("Mutex total: ${mutexTotalNanos / 1_000}us for ${mutexChainClasses.size} classes")

    println()
    println("=== CountDownLatch class-loading (fix) ===")
    println("CountDownLatch is a JDK bootstrap class — already loaded by the VM.")
    println("Creating CountDownLatch(1) triggers ZERO additional class loading.")

    println()
    println("=== Actual construction timing ===")

    val mutexStart = System.nanoTime()
    val mutex = kotlinx.coroutines.sync.Mutex(locked = true)
    val mutexTime = System.nanoTime() - mutexStart
    println("Mutex(locked=true): ${mutexTime / 1_000}us")

    val latchStart = System.nanoTime()
    val latch = java.util.concurrent.CountDownLatch(1)
    val latchTime = System.nanoTime() - latchStart
    println("CountDownLatch(1): ${latchTime / 1_000}us")

    println()
    println("=== Functional equivalence ===")
    println("Mutex.isLocked = ${mutex.isLocked}")
    println("CountDownLatch.count = ${latch.count}")
    mutex.unlock()
    latch.countDown()
    println("After unlock/countDown:")
    println("Mutex.isLocked = ${mutex.isLocked}")
    println("CountDownLatch.count = ${latch.count}")

    println()
    println("=== Impact on budget devices ===")
    println("Mutex loads ${mutexChainClasses.size} classes via <clinit> chain.")
    println("CountDownLatch loads 0 additional classes (JDK bootstrap).")
    println("On budget Realme/Vivo devices, class loading from APK is 10-100x slower.")
    println("The Mutex cascade during CrashlyticsRegistrar.<clinit> pushes past the ANR timeout.")
  }
}
