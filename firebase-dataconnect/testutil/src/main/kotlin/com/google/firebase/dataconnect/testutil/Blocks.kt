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

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class BlockReturningWithParameter<T, out R>(val returnValue: R) : ((T) -> R) {

  private val _calls = CopyOnWriteArrayList<T>()

  val calls: List<T>
    get() = _calls.toList()

  override operator fun invoke(argument: T): R {
    _calls.add(argument)
    return returnValue
  }
}

class BlockWithParameter<T> : ((T) -> Unit) {

  private val _calls = CopyOnWriteArrayList<T>()

  val calls: List<T>
    get() = _calls.toList()

  override operator fun invoke(argument: T) {
    _calls.add(argument)
  }
}

class BlockReturning<out T>(val returnValue: T) : (() -> T) {

  private val _callCount = AtomicInteger(0)

  val callCount: Int
    get() = _callCount.get()

  override operator fun invoke(): T {
    _callCount.incrementAndGet()
    return returnValue
  }
}

class BlockReturningUnit : (() -> Unit) {

  private val _callCount = AtomicInteger(0)

  val callCount: Int
    get() = _callCount.get()

  override operator fun invoke() {
    _callCount.incrementAndGet()
  }
}

class BlockThrowing(val message: String) : (() -> Nothing) {

  private val _callCount = AtomicInteger(0)

  val callCount: Int
    get() = _callCount.get()

  override operator fun invoke(): Nothing {
    _callCount.incrementAndGet()
    throw UnexpectedInvocationException(message)
  }

  class UnexpectedInvocationException(message: String) : Exception(message)
}

class BlockThrowingWithParameter<T>(val message: String) : ((T) -> Nothing) {

  private val _calls = CopyOnWriteArrayList<Any?>()

  val calls: List<Any?>
    get() = _calls.toList()

  override operator fun invoke(argument: T): Nothing {
    _calls.add(argument)
    throw UnexpectedInvocationException(message)
  }

  class UnexpectedInvocationException(message: String) : Exception(message)
}
