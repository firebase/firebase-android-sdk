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

package com.google.firebase.dataconnect.connectors.demo

import com.google.common.truth.Truth.assertThat
import com.google.firebase.Timestamp
import com.google.firebase.dataconnect.QueryRef
import com.google.firebase.dataconnect.connectors.demo.testutil.DemoConnectorIntegrationTestBase
import com.google.firebase.dataconnect.testutil.MAX_SAFE_INTEGER
import com.google.firebase.dataconnect.testutil.MAX_VALUE
import com.google.firebase.dataconnect.testutil.MIN_VALUE
import com.google.firebase.dataconnect.testutil.randomAlphanumericString
import com.google.firebase.dataconnect.testutil.toDate
import java.util.Calendar
import java.util.Date
import java.util.UUID
import kotlin.reflect.full.memberProperties
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

class ScalarVariablesAndDataIntegrationTest : DemoConnectorIntegrationTestBase() {

  @Test
  fun stringVariants() = runTest {
    val id = randomAlphanumericString()

    connector.insertStringVariants.execute(
      id = id,
      nonNullWithNonEmptyValue = "some non-empty value for a *non*-nullable field",
      nonNullWithEmptyValue = "",
    ) {
      nullableWithNullValue = null
      nullableWithNonNullValue = "some non-empty value for a *nullable* field"
      nullableWithEmptyValue = ""
    }

    val queryResult = connector.getStringVariantsById.execute(id)
    assertThat(queryResult.data.stringVariants)
      .isEqualTo(
        GetStringVariantsByIdQuery.Data.StringVariants(
          nonNullWithNonEmptyValue = "some non-empty value for a *non*-nullable field",
          nonNullWithEmptyValue = "",
          nullableWithNullValue = null,
          nullableWithNonNullValue = "some non-empty value for a *nullable* field",
          nullableWithEmptyValue = "",
        )
      )
  }

  @Test
  fun intVariants() = runTest {
    val id = randomAlphanumericString()

    connector.insertIntVariants.execute(
      id = id,
      nonNullWithZeroValue = 0,
      nonNullWithPositiveValue = 42424242,
      nonNullWithNegativeValue = -42424242,
      nonNullWithMaxValue = Int.MAX_VALUE,
      nonNullWithMinValue = Int.MIN_VALUE,
    ) {
      nullableWithNullValue = null
      nullableWithZeroValue = 0
      nullableWithPositiveValue = 24242424
      nullableWithNegativeValue = -24242424
      nullableWithMaxValue = Int.MAX_VALUE
      nullableWithMinValue = Int.MIN_VALUE
    }

    val queryResult = connector.getIntVariantsById.execute(id)
    assertThat(queryResult.data.intVariants)
      .isEqualTo(
        GetIntVariantsByIdQuery.Data.IntVariants(
          nonNullWithZeroValue = 0,
          nonNullWithPositiveValue = 42424242,
          nonNullWithNegativeValue = -42424242,
          nonNullWithMaxValue = Int.MAX_VALUE,
          nonNullWithMinValue = Int.MIN_VALUE,
          nullableWithNullValue = null,
          nullableWithZeroValue = 0,
          nullableWithPositiveValue = 24242424,
          nullableWithNegativeValue = -24242424,
          nullableWithMaxValue = Int.MAX_VALUE,
          nullableWithMinValue = Int.MIN_VALUE,
        )
      )
  }

  @Ignore(
    "b/339440054 Fix this test once -0.0 is correctly sent from the backend " +
      "instead of being converted to 0.0"
  )
  @Test
  fun floatCorrectlySerializesNegativeZero() {
    TODO(
      "this test is merely a placeholder as a reminder " +
        "and should be removed once the test is updated"
    )
  }

  @Test
  fun floatVariants() = runTest {
    val id = randomAlphanumericString()

    connector.insertFloatVariants.execute(
      id = id,
      nonNullWithZeroValue = 0.0,
      nonNullWithNegativeZeroValue = 0.0, // TODO(b/339440054) change to -0.0
      nonNullWithPositiveValue = 123.456,
      nonNullWithNegativeValue = -987.654,
      nonNullWithMaxValue = Double.MAX_VALUE,
      nonNullWithMinValue = Double.MIN_VALUE,
      nonNullWithMaxSafeIntegerValue = MAX_SAFE_INTEGER,
    ) {
      nullableWithNullValue = null
      nullableWithZeroValue = 0.0
      nullableWithNegativeZeroValue = 0.0 // TODO(b/339440054) change to -0.0
      nullableWithPositiveValue = 789.012
      nullableWithNegativeValue = -321.098
      nullableWithMaxValue = Double.MAX_VALUE
      nullableWithMinValue = Double.MIN_VALUE
      nullableWithMaxSafeIntegerValue = MAX_SAFE_INTEGER
    }

    val queryResult = connector.getFloatVariantsById.execute(id)
    assertThat(queryResult.data.floatVariants)
      .isEqualTo(
        GetFloatVariantsByIdQuery.Data.FloatVariants(
          nonNullWithZeroValue = 0.0,
          nonNullWithNegativeZeroValue = 0.0, // TODO(b/339440054) change to -0.0
          nonNullWithPositiveValue = 123.456,
          nonNullWithNegativeValue = -987.654,
          nonNullWithMaxValue = Double.MAX_VALUE,
          nonNullWithMinValue = Double.MIN_VALUE,
          nonNullWithMaxSafeIntegerValue = MAX_SAFE_INTEGER,
          nullableWithNullValue = null,
          nullableWithZeroValue = 0.0,
          nullableWithNegativeZeroValue = 0.0, // TODO(b/339440054) change to -0.0
          nullableWithPositiveValue = 789.012,
          nullableWithNegativeValue = -321.098,
          nullableWithMaxValue = Double.MAX_VALUE,
          nullableWithMinValue = Double.MIN_VALUE,
          nullableWithMaxSafeIntegerValue = MAX_SAFE_INTEGER,
        )
      )
  }

  @Test
  fun booleanVariants() = runTest {
    val id = randomAlphanumericString()

    connector.insertBooleanVariants.execute(
      id = id,
      nonNullWithTrueValue = true,
      nonNullWithFalseValue = false,
    ) {
      nullableWithNullValue = null
      nullableWithTrueValue = true
      nullableWithFalseValue = false
    }

    val queryResult = connector.getBooleanVariantsById.execute(id)
    assertThat(queryResult.data.booleanVariants)
      .isEqualTo(
        GetBooleanVariantsByIdQuery.Data.BooleanVariants(
          nonNullWithTrueValue = true,
          nonNullWithFalseValue = false,
          nullableWithNullValue = null,
          nullableWithTrueValue = true,
          nullableWithFalseValue = false,
        )
      )
  }

  @Test
  fun int64Variants() = runTest {
    val id = randomAlphanumericString()

    connector.insertInt64variants.execute(
      id = id,
      nonNullWithZeroValue = 0,
      nonNullWithPositiveValue = 4242424242424242,
      nonNullWithNegativeValue = -4242424242424242,
      nonNullWithMaxValue = Long.MAX_VALUE,
      nonNullWithMinValue = Long.MIN_VALUE,
    ) {
      nullableWithNullValue = null
      nullableWithZeroValue = 0
      nullableWithPositiveValue = 2424242424242424
      nullableWithNegativeValue = -2424242424242424
      nullableWithMaxValue = Long.MAX_VALUE
      nullableWithMinValue = Long.MIN_VALUE
    }

    val queryResult = connector.getInt64variantsById.execute(id)
    assertThat(queryResult.data.int64Variants)
      .isEqualTo(
        GetInt64variantsByIdQuery.Data.Int64variants(
          nonNullWithZeroValue = 0,
          nonNullWithPositiveValue = 4242424242424242,
          nonNullWithNegativeValue = -4242424242424242,
          nonNullWithMaxValue = Long.MAX_VALUE,
          nonNullWithMinValue = Long.MIN_VALUE,
          nullableWithNullValue = null,
          nullableWithZeroValue = 0,
          nullableWithPositiveValue = 2424242424242424,
          nullableWithNegativeValue = -2424242424242424,
          nullableWithMaxValue = Long.MAX_VALUE,
          nullableWithMinValue = Long.MIN_VALUE,
        )
      )
  }

  @Test
  fun uuidVariants() = runTest {
    val id = randomAlphanumericString()
    val nonNullValue = UUID.randomUUID()
    val nullableWithNullValue = UUID.randomUUID()

    connector.insertUuidvariants.execute(
      id = id,
      nonNullValue = nonNullValue,
    ) {
      this.nullableWithNullValue = nullableWithNullValue
      nullableWithNonNullValue = null
    }

    val queryResult = connector.getUuidvariantsById.execute(id)
    assertThat(queryResult.data.uUIDVariants)
      .isEqualTo(
        GetUuidvariantsByIdQuery.Data.UUidvariants(
          nonNullValue = nonNullValue,
          nullableWithNullValue = nullableWithNullValue,
          nullableWithNonNullValue = null,
        )
      )
  }

  @Test
  fun dateVariantsInVariables() = runTest {
    val id = randomAlphanumericString()
    val dateWithNonZeroTime =
      Calendar.getInstance().apply { set(2024, Calendar.MAY, 23, 11, 12, 13) }.time

    connector.insertDateVariants.execute(
      id = id,
      nonNullValue = "2024-04-26".toDate(),
      minValue = "0001-01-01".toDate(),
      maxValue = "9999-12-31".toDate(),
      nonZeroTime = dateWithNonZeroTime,
    ) {
      nullableWithNullValue = null
      nullableWithNonNullValue = "2024-05-19".toDate()
    }

    val queryRef = connector.getDateVariantsById.ref(id).withStringData()
    val queryResult = queryRef.execute()
    assertThat(queryResult.data.dateVariants)
      .isEqualTo(
        GetDateVariantsByIdQueryStringData.DateVariants(
          nonNullValue = "2024-04-26",
          nullableWithNullValue = null,
          nullableWithNonNullValue = "2024-05-19",
          minValue = "0001-01-01",
          maxValue = "9999-12-31",
          nonZeroTime = "2024-05-23",
        )
      )
  }

  @Test
  fun timestampVariants() = runTest {
    val id = randomAlphanumericString()

    connector.insertTimestampVariants.execute(
      id = id,
      nonNullValue = Timestamp(1, 3_219),
      minValue = Timestamp.MIN_VALUE,
      maxValue = Timestamp.MAX_VALUE,
    ) {
      nullableWithNullValue = null
      nullableWithNonNullValue = Timestamp(-46_239, 4_628)
    }

    val queryResult = connector.getTimestampVariantsById.execute(id)

    /**
     * Note: Timestamp sent from SDK is always 9 digits nanosecond precision, meaning there are 9
     * digits in SSSSSSSSS parts. However, when running against different databases, this precision
     * might change, and server will truncate it to 0/3/6 digits precision without throwing an
     * error. That's why in the integration test, we only verify the second. Serializer will be
     * tested in unit tests.
     */
    assertTrue(
      queryResult.data.timestampVariants!!.verifySeconds(
        GetTimestampVariantsByIdQuery.Data.TimestampVariants(
          nonNullValue = Timestamp(1, 3_219),
          nullableWithNullValue = null,
          nullableWithNonNullValue = Timestamp(-46_239, 4_628),
          minValue = Timestamp.MIN_VALUE,
          maxValue = Timestamp.MAX_VALUE,
        )
      )
    )
  }

  @Test
  fun dateVariantsInData() = runTest {
    val id = randomAlphanumericString()

    val mutationRef =
      connector.insertDateVariants.refWith(
        InsertDateVariantsMutationStringVariables(
          id = id,
          nonNullValue = "2024-04-26",
          nullableWithNullValue = null,
          nullableWithNonNullValue = "2024-05-19",
          minValue = "0001-01-01",
          maxValue = "9999-12-31",
          nonZeroTime = "2024-04-24",
        )
      )
    mutationRef.execute()

    val queryResult = connector.getDateVariantsById.execute(id)
    assertThat(queryResult.data.dateVariants)
      .isEqualTo(
        GetDateVariantsByIdQuery.Data.DateVariants(
          nonNullValue = "2024-04-26".toDate(),
          nullableWithNullValue = null,
          nullableWithNonNullValue = "2024-05-19".toDate(),
          minValue = "0001-01-01".toDate(),
          maxValue = "9999-12-31".toDate(),
          nonZeroTime = "2024-04-24".toDate(),
        )
      )
  }

  // TODO: Repeat the tests above for Int, Float, and Boolean.
  //  Make sure to test boundary values, like Int.MAX_VALUE, Float.NaN, true, and false.

  private companion object {
    fun QueryRef<*, GetDateVariantsByIdQuery.Variables>.withStringData() =
      dataConnect.query(
        operationName,
        variables,
        serializer<GetDateVariantsByIdQueryStringData>(),
        variablesSerializer
      )

    fun InsertDateVariantsMutation.refWith(variables: InsertDateVariantsMutationStringVariables) =
      connector.dataConnect.mutation(
        InsertDateVariantsMutation.operationName,
        variables,
        InsertDateVariantsMutation.dataDeserializer,
        serializer()
      )

    fun GetTimestampVariantsByIdQuery.Data.TimestampVariants.verifySeconds(
      other: GetTimestampVariantsByIdQuery.Data.TimestampVariants
    ): Boolean {
      val properties = GetTimestampVariantsByIdQuery.Data.TimestampVariants::class.memberProperties
      for (field in properties) {
        val actual = field.get(this)
        val expect = field.get(other)
        if (actual == null && expect == null) {
          continue
        } else if (actual is Timestamp && expect is Timestamp) {
          if (!comparedSerializedTimestamp(actual, expect)) {
            return false
          }
        } else if (actual is List<*> && expect is List<*>) {
          if (actual.size != expect.size) {
            return false
          }
          for (i in 0 until actual.size) {
            if (!comparedSerializedTimestamp(actual[i] as Timestamp, expect[i] as Timestamp)) {
              return false
            }
          }
        } else {
          throw IllegalArgumentException(
            "`TimestampVariants.verifySeconds` doesn't support these types."
          )
        }
      }
      return true
    }

    private fun comparedSerializedTimestamp(actual: Timestamp, expect: Timestamp): Boolean {
      return actual.seconds == expect.seconds
    }
  }

  /**
   * An alternative to [InsertDateVariantsMutation.Variables] where the fields are typed as [String]
   * instead of [Date].
   */
  @Serializable
  data class InsertDateVariantsMutationStringVariables(
    val id: String,
    val nonNullValue: String,
    val nullableWithNullValue: String?,
    val nullableWithNonNullValue: String?,
    val minValue: String,
    val nonZeroTime: String,
    val maxValue: String,
  )

  /**
   * An alternative to [GetDateVariantsByIdQuery.Data] where the fields are typed as [String]
   * instead of [Date].
   */
  @Serializable
  private data class GetDateVariantsByIdQueryStringData(val dateVariants: DateVariants?) {

    @Serializable
    data class DateVariants(
      val nonNullValue: String,
      val nullableWithNullValue: String?,
      val nullableWithNonNullValue: String?,
      val minValue: String,
      val maxValue: String,
      val nonZeroTime: String,
    )
  }
}
