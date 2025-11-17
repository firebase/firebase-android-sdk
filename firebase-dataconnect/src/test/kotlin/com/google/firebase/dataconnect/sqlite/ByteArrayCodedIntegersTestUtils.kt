/*
 * Copyright 2025 Google LLC
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

package com.google.firebase.dataconnect.sqlite

import com.google.firebase.dataconnect.sqlite.CodedIntegersExts.putSInt32
import com.google.firebase.dataconnect.sqlite.CodedIntegersExts.putSInt64
import com.google.firebase.dataconnect.sqlite.CodedIntegersExts.putUInt32
import com.google.firebase.dataconnect.sqlite.CodedIntegersExts.putUInt64
import com.google.firebase.dataconnect.testutil.BuildByteArrayDSL

fun BuildByteArrayDSL.putSInt32(value: Int): Int = write { it.putSInt32(value) }

fun BuildByteArrayDSL.putSInt64(value: Long): Int = write { it.putSInt64(value) }

fun BuildByteArrayDSL.putUInt32(value: Int): Int = write { it.putUInt32(value) }

fun BuildByteArrayDSL.putUInt64(value: Long): Int = write { it.putUInt64(value) }
