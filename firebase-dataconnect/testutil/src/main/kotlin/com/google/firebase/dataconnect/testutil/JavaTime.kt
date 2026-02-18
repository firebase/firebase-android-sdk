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

package com.google.firebase.dataconnect.testutil

import android.annotation.SuppressLint

fun org.threeten.bp.Instant.toTimestamp(): com.google.firebase.Timestamp =
  com.google.firebase.Timestamp(epochSecond, nano)

fun com.google.firebase.dataconnect.LocalDate.toTheeTenAbpJavaLocalDate():
  org.threeten.bp.LocalDate = org.threeten.bp.LocalDate.of(year, month, day)

@SuppressLint("NewApi")
fun java.time.LocalDate.toTheeTenAbpJavaLocalDate(): org.threeten.bp.LocalDate {
  val threeTenBpMonth = org.threeten.bp.Month.of(monthValue)
  return org.threeten.bp.LocalDate.of(year, threeTenBpMonth, dayOfMonth)
}

fun kotlinx.datetime.LocalDate.toTheeTenAbpJavaLocalDate(): org.threeten.bp.LocalDate {
  val threeTenBpMonth = org.threeten.bp.Month.of(monthNumber)
  return org.threeten.bp.LocalDate.of(year, threeTenBpMonth, dayOfMonth)
}
