// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.firebase.dataconnect

import java.io.OutputStream

fun ByteArray.toHexString(): String =
  joinToString(separator = "") { it.toUByte().toInt().toString(16).padStart(2, '0') }

object NullOutputStream : OutputStream() {
  override fun write(b: Int) {}
  override fun write(b: ByteArray?) {}
  override fun write(b: ByteArray?, off: Int, len: Int) {}
}

class ReferenceCounted<T>(val obj: T, var refCount: Int)
