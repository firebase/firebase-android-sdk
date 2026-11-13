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

package com.google.firebase.sql

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Tests the classes that keep their constants in their companion object, such as [Count]. */
class ConstantsTest {

  @Test
  fun eachConstantPrintsTheValueThatItHolds() {
    assertThat(Count.EXACT.toString()).isEqualTo("exact")
    assertThat(FilterOperator.GT_ALL.toString()).isEqualTo("gt(all)")
    assertThat(TextSearchType.WEBSEARCH.toString()).isEqualTo("wfts")
    assertThat(RpcMethod.HEAD.toString()).isEqualTo("HEAD")
    assertThat(listOf(ExplainFormat.TEXT, ExplainFormat.JSON).map { it.toString() })
      .containsExactly("text", "json")
      .inOrder()
    assertThat(
        listOf(
            FirebaseSqlExceptionCategory.POSTGREST,
            FirebaseSqlExceptionCategory.PLATFORM,
            FirebaseSqlExceptionCategory.CLIENT
          )
          .map { it.toString() }
      )
      .containsExactly("sql-postgrest", "sql-platform", "sql-client")
      .inOrder()
  }

  @Test
  fun eachConstantHoldsItsOwnValue() {
    val constants =
      listOf(
        constantsOf<Count>(),
        constantsOf<FilterOperator>(),
        constantsOf<FirebaseSqlExceptionCategory>(),
        constantsOf<RpcMethod>(),
        constantsOf<TextSearchType>(),
        constantsOf<ExplainFormat>()
      )

    assertThat(constants.map { it.size }).containsExactly(3, 43, 3, 3, 4, 2).inOrder()
    for (byName in constants) {
      assertThat(byName.values.map { it.toString() }.toSet()).hasSize(byName.size)
    }
  }
}
