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
import com.google.protobuf.TextFormat
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest
import java.io.File
import java.io.OutputStream
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class DriverTests {
  object DevNullOutputStream : OutputStream() {
    override fun write(b: Int) {}
  }

  @Test
  fun `driver should throw if config file is not specified`() {
    val thrown =
      assertThrows(InvalidConfigException::class.java) {
        driver(CodeGeneratorRequest.getDefaultInstance().asInput(), DevNullOutputStream)
      }
    assertThat(thrown.message).contains("Required plugin option is missing")
  }
  @Test
  fun `driver should throw if config file does not exist`() {
    val requestInput =
      CodeGeneratorRequest.newBuilder().setParameter("does_not_exist.txtpb").build().asInput()
    val thrown =
      assertThrows(InvalidConfigException::class.java) { driver(requestInput, DevNullOutputStream) }
    assertThat(thrown.message).contains("does not exist")
  }

  @Test
  fun `driver should throw if config file has syntax error`() {
    val cfg = File.createTempFile("invalid_cfg", null)
    cfg.writeText("invalid")
    val requestInput =
      CodeGeneratorRequest.newBuilder().setParameter(cfg.absolutePath).build().asInput()

    val thrown =
      assertThrows(InvalidConfigException::class.java) { driver(requestInput, DevNullOutputStream) }
    assertThat(thrown.cause).isInstanceOf(TextFormat.ParseException::class.java)
  }

  @Test
  fun `driver should throw if vendor_package is empty`() {
    val cfg = File.createTempFile("invalid_cfg", null)
    cfg.writeText("include: \"\"")
    val requestInput =
      CodeGeneratorRequest.newBuilder().setParameter(cfg.absolutePath).build().asInput()

    val thrown =
      assertThrows(InvalidConfigException::class.java) { driver(requestInput, DevNullOutputStream) }
    assertThat(thrown.message).contains("vendor_package is not set")
  }

  @Test
  fun `driver should succeed if config is valid`() {
    val cfg = File.createTempFile("valid_cfg", null)
    cfg.writeText("vendor_package: \"com.example\"")
    val requestInput =
      CodeGeneratorRequest.newBuilder().setParameter(cfg.absolutePath).build().asInput()

    driver(requestInput, DevNullOutputStream)
  }
}

private fun CodeGeneratorRequest.asInput() = toByteString().newInput()
