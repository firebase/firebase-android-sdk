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

import com.ibm.icu.text.SimpleDateFormat
import java.util.Date

val MIN_DATE: Date
  get() = dateFromYYYYMMDD("1583-01-01")

val MAX_DATE: Date
  get() = dateFromYYYYMMDD("9999-12-31")

fun dateFromYYYYMMDD(date: String): Date = SimpleDateFormat("yyyy-MM-dd").parse(date)!!
