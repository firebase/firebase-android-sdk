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

package com.google.firebase.dataconnect

import com.google.firebase.dataconnect.util.nextSequenceNumber
import java.util.concurrent.atomic.AtomicReference

internal class LockFreeConcurrentMap<T> {

  private val state = AtomicReference(State(emptyArray(), emptyArray()))

  fun acquire(key: String, creator: () -> T): T {
    val lazyNewValue = lazy(LazyThreadSafetyMode.NONE, creator)

    while (true) {
      val capturedState = state.get()
      val index = capturedState.keys.binarySearch(key)
      val newState =
        if (index >= 0) {
          val referenceCountedValue = capturedState.values[index]
          if (referenceCountedValue.incRef()) {
            @Suppress("UNCHECKED_CAST") return referenceCountedValue.obj as T
          }
          State(
            capturedState.keys,
            capturedState.values.copyOf().also { it[index] = ReferenceCounted(lazyNewValue.value) },
          )
        } else {
          val insertIndex = (-index) - 1
          State(
            capturedState.keys.withInsertedElement(insertIndex, key),
            capturedState.values.withInsertedElement(
              insertIndex,
              ReferenceCounted(lazyNewValue.value)
            ),
          )
        }

      if (state.compareAndSet(capturedState, newState)) {
        println("zzyzx INSERTED VALUE")
        return lazyNewValue.value
      }
    }
  }

  fun release(key: String) {
    var capturedState = state.get()
    var index = capturedState.keys.binarySearch(key)
    require(index >= 0) { "key not found: $key" }

    val referenceCountedValue = capturedState.values[index]
    val newRefCount = referenceCountedValue.decRef()
    if (newRefCount > 0) {
      return
    }

    while (true) {
      val newState =
        State(
          capturedState.keys.withRemovedElement(index),
          capturedState.values.withRemovedElement(index),
        )

      if (state.compareAndSet(capturedState, newState)) {
        return
      }

      capturedState = state.get()
      index = capturedState.keys.binarySearch(key)
      if (index < 0 || capturedState.values[index] !== referenceCountedValue) {
        println("zzyzx REMOVED VALUE")
        return
      }
    }
  }

  // Attach a "sequence number" to the refcount to avoid the "ABA" problem when performing the
  // compare-and-swap, a problem that would be present if just the raw Int count was used.
  private data class ReferenceCount(val sequenceNumber: Long, val count: Int)

  private class ReferenceCounted(val obj: Any?) {
    private val refCount = AtomicReference(ReferenceCount(nextSequenceNumber(), 1))

    fun incRef(): Boolean {
      while (true) {
        val oldRefCount = refCount.get()
        check(oldRefCount.count >= 0) {
          "invalid oldRefCount.count: ${oldRefCount.count}" +
            " (must be greater than or equal to zero)"
        }
        if (oldRefCount.count == 0) {
          return false
        }
        val newRefCount = ReferenceCount(nextSequenceNumber(), oldRefCount.count + 1)
        if (refCount.compareAndSet(oldRefCount, newRefCount)) {
          return true
        }
      }
    }

    fun decRef(): Int {
      while (true) {
        val oldRefCount = refCount.get()
        check(oldRefCount.count > 0) {
          "invalid oldRefCount.count: ${oldRefCount.count}" +
            " (must be strictly greater than zero)"
        }
        val newRefCount = ReferenceCount(nextSequenceNumber(), oldRefCount.count - 1)
        if (refCount.compareAndSet(oldRefCount, newRefCount)) {
          return newRefCount.count
        }
      }
    }
  }

  private class State(
    val keys: Array<String>,
    val values: Array<ReferenceCounted>,
  )

  private companion object {

    inline fun <reified T> Array<T>.withInsertedElement(insertIndex: Int, value: T): Array<T> =
      Array(size + 1) {
        if (it < insertIndex) get(it) else if (it == insertIndex) value else get(it - 1)
      }

    inline fun <reified T> Array<T>.withRemovedElement(removeIndex: Int): Array<T> =
      Array(size - 1) { if (it < removeIndex) get(it) else get(it + 1) }
  }
}
