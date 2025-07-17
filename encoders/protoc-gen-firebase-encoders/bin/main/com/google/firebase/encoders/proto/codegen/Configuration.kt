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

import com.google.firebase.encoders.proto.CodeGenConfig
import com.google.protobuf.TextFormat

object ConfigReader {
  fun read(readable: Readable): CodeGenConfig {
    val builder = CodeGenConfig.newBuilder()
    try {
      TextFormat.merge(readable, builder)
    } catch (ex: TextFormat.ParseException) {
      throw InvalidConfigException("Unable to parse config.", ex)
    }
    val config = builder.build()
    if (config.vendorPackage.isEmpty()) {
      throw InvalidConfigException("vendor_package is not set in config.")
    }

    return config
  }
}

class InvalidConfigException(msg: String, cause: Throwable? = null) : RuntimeException(msg, cause)
