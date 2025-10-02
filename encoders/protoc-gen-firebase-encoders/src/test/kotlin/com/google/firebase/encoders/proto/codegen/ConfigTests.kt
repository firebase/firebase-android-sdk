/*
 * Copyright 2021 Google LLC
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

package com.google.firebase.encoders.proto.codegen

import com.google.common.truth.Truth.assertThat
import com.google.firebase.encoders.proto.CodeGenConfig
import java.nio.CharBuffer
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ConfigTests {
  @Test
  fun `read() with valid txtpb file should succeed`() {
    val vendorPackage = "com.example"
    val myProto = "com.example.proto.MyProto"
    val config =
      ConfigReader.read(
        """
            vendor_package: "$vendorPackage"
            include: "$myProto"
        """
          .trimIndent()
      )

    assertThat(config)
      .isEqualTo(
        CodeGenConfig.newBuilder().setVendorPackage(vendorPackage).addInclude(myProto).build()
      )
  }

  @Test
  fun `read() with invalid file should fail`() {
    assertThrows(InvalidConfigException::class.java) { ConfigReader.read("invalid") }
  }
}

private fun ConfigReader.read(value: String): CodeGenConfig = read(CharBuffer.wrap(value))
