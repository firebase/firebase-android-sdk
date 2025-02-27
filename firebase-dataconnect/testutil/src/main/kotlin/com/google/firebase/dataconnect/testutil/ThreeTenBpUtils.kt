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
import org.threeten.bp.LocalDate
import org.threeten.bp.Month
import org.threeten.bp.Year

fun Month.lengthInYear(year: Year): Int = length(year.isLeap)

fun Month.dayRangeInYear(year: Year): IntRange = 1..lengthInYear(year)

fun LocalDate.toDataConnectLocalDate(): com.google.firebase.dataconnect.LocalDate =
  com.google.firebase.dataconnect.LocalDate(
    year = year,
    month = monthValue,
    day = dayOfMonth,
  )

@SuppressLint("NewApi")
fun LocalDate.toJavaTimeLocalDate(): java.time.LocalDate =
  java.time.LocalDate.of(
    year,
    monthValue,
    dayOfMonth,
  )

@SuppressLint("NewApi")
fun LocalDate.toKotlinxDatetimeLocalDate(): kotlinx.datetime.LocalDate =
  kotlinx.datetime.LocalDate(
    year,
    monthValue,
    dayOfMonth,
  )
