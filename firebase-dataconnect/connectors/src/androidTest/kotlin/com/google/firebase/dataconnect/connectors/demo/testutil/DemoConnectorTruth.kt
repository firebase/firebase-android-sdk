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

package com.google.firebase.dataconnect.connectors.demo.testutil

import androidx.annotation.CheckResult
import com.google.firebase.dataconnect.connectors.demo.DemoConnector
import com.google.firebase.dataconnect.connectors.demo.GetFooByIdQuery
import com.google.firebase.dataconnect.connectors.demo.execute
import com.google.firebase.dataconnect.testutil.fail

/**
 * Returns an object with which fluent assertions can be made using the given [DemoConnector]
 * instance, similar to [com.google.common.truth.Truth] assertions.
 */
fun assertWith(connector: DemoConnector): DemoConnectorSubject = DemoConnectorSubjectImpl(connector)

interface DemoConnectorSubject {

  /** Returns an object with which assertions can be performed about a `Foo` with the given ID. */
  @CheckResult fun thatFooWithId(id: String): FooSubject

  /**
   * Returns an object with which assertions can be performed about all `Foo` objects whose `bar`
   * field is equal to the given value.
   */
  @CheckResult fun thatFoosWithBar(bar: String): FooListSubject

  /** Provides methods for performing assertions on a `Foo` object. */
  interface FooSubject {

    /** Throws if the `Foo` does not exist. */
    suspend fun exists()

    /** Throws if the `Foo` exists. */
    suspend fun doesNotExist()

    /**
     * Throws if the `Foo` does not exist, or exists with a `bar` field value different than the
     * given value.
     */
    suspend fun existsWithBar(expectedBar: String)
  }

  /** Provides methods for performing assertions on a (possibly empty) list of `Foo` objects. */
  interface FooListSubject {

    /** Throws if the number of existing `Foo` objects is different than the given value. */
    suspend fun exist(expectedCount: Int)

    /** Throws if one or more `Foo` objects exist. */
    suspend fun doNotExist()
  }
}

private class DemoConnectorSubjectImpl(private val connector: DemoConnector) :
  DemoConnectorSubject {
  override fun thatFooWithId(id: String) = FooSubjectImpl(connector, id)
  override fun thatFoosWithBar(bar: String) = FooListSubjectImpl(connector, bar)
}

private class FooSubjectImpl(private val connector: DemoConnector, private val id: String) :
  DemoConnectorSubject.FooSubject {
  override suspend fun exists() {
    loadFoo() ?: fail("Expected Foo with id=$id to exist, but it does not exist")
  }

  override suspend fun existsWithBar(expectedBar: String) {
    val foo = loadFoo()
    if (foo == null) {
      fail("Expected Foo with id=$id to exist with bar=$expectedBar, but it does not exist at all")
    } else if (foo.bar != expectedBar) {
      fail(
        "Expected Foo with id=$id to exist with bar=$expectedBar, and it does exist, " +
          "but its bar is different: ${foo.bar}"
      )
    }
  }

  override suspend fun doesNotExist() {
    val foo = loadFoo()
    if (foo != null) {
      fail("Expected Foo with id=$id to not exist, but it exists with bar=${foo.bar}")
    }
  }

  private suspend fun loadFoo(): GetFooByIdQuery.Data.Foo? =
    connector.getFooById.execute(id).data.foo
}

private class FooListSubjectImpl(private val connector: DemoConnector, private val bar: String) :
  DemoConnectorSubject.FooListSubject {

  override suspend fun doNotExist() {
    val count = fooCount()
    if (count > 0) {
      fail("Expected zero Foo rows to exist with bar=$bar to exist, but found $count")
    }
  }

  override suspend fun exist(expectedCount: Int) {
    val count = fooCount()
    if (count != expectedCount) {
      fail("Expected ${expectedCount} Foo rows to exist with bar=$bar to exist, but found $count")
    }
  }

  private suspend fun fooCount(): Int =
    // TODO: Remove the `!!` once the codegen is fixed to generate non-nullable lists.
    connector.getFoosByBar.execute { bar = this@FooListSubjectImpl.bar }.data.foos!!.size
}
