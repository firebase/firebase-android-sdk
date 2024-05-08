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

import com.google.firebase.Timestamp

// "1583-01-01T00:00:00.000000Z"
val Timestamp.Companion.MIN_VALUE
  get() = Timestamp(-12_212_553_600, 0)

// "9999-12-31T23:59:59.999999999Z"
val Timestamp.Companion.MAX_VALUE
  get() = Timestamp(253_402_300_799, 999_999_999)
