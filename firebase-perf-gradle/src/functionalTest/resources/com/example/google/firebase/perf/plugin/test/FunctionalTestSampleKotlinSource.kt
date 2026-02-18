/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.google.firebase.perf.plugin.test

import android.app.Activity
import android.os.Bundle
import io.ktor.utils.io.core.BytePacketBuilder
import java.io.IOException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * A Sample kotlin class for the test project.
 *
 * This class depends on Ktor library that uses both Kotlin Inline Function and Multiplatform
 * Project.
 *
 * <pre> References:
 * - https://ktor.io/
 * - https://kotlinlang.org/docs/reference/inline-functions.html
 * - https://kotlinlang.org/docs/reference/multiplatform.html
 * - https://github.com/firebase/firebase-android-sdk/issues/1556 </pre>
 */
class FunctionalTestSampleKotlinSource : Activity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    buildPacket()
    makeOkHttpRequest()
  }

  inline fun buildPacket(): BytePacketBuilder {
    try {
      return BytePacketBuilder(10)
    } finally {
      // this block is required
    }
  }

  private fun makeOkHttpRequest() {
    val url = "https://www.google.com"
    val request = Request.Builder().url(url).build()
    val client = OkHttpClient()

    client
      .newCall(request)
      .enqueue(
        object : Callback {
          override fun onResponse(call: Call, response: Response) {
            val body = response.body?.string()
            println("API execution Succeeded, response size: " + body?.length)
          }

          override fun onFailure(call: Call, e: IOException) {
            println("API execution failed")
          }
        }
      )
  }
}
