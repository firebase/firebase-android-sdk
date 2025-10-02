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
import com.google.protobuf.DescriptorProtos.FileDescriptorProto
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import dagger.BindsInstance
import dagger.Component
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.PrintWriter
import java.io.StringWriter
import javax.inject.Inject

fun driver(input: InputStream, output: OutputStream) {
  val request = CodeGeneratorRequest.parseFrom(input)
  if (request.parameter.isEmpty()) {
    throw InvalidConfigException(
      "Required plugin option is missing. " +
        "Please specify the config file path via plugin options."
    )
  }
  val cfgFile = File(request.parameter)
  if (!cfgFile.exists() || !cfgFile.isFile) {
    throw InvalidConfigException("Config file '$cfgFile' does not exist or is a directory.")
  }

  val config = cfgFile.reader().use { ConfigReader.read(it) }

  val component: MainComponent = DaggerMainComponent.builder().config(config).build()
  component.plugin.run(request.protoFileList).writeTo(output)
}

/**
 * Main entry point to the executable jar.
 *
 * According to protoc plugin spec, this function is invoked with no arguments and receives a
 * serialized [CodeGeneratorRequest] via `stdin` and must return a [CodeGeneratorResponse] via
 * `stdout`.
 */
fun main(args: Array<String>) {
  runCatching { driver(System.`in`, System.out) }
    .onFailure {
      val stringWriter = StringWriter()
      it.printStackTrace(PrintWriter(stringWriter))
      CodeGeneratorResponse.newBuilder()
        .setError(stringWriter.toString())
        .build()
        .writeTo(System.out)
    }
}

class Plugin
@Inject
constructor(private val parser: DescriptorParser, private val generator: CodeGenerator) {
  fun run(protoFiles: List<FileDescriptorProto>): CodeGeneratorResponse {
    return generator.generate(parser.parse(protoFiles))
  }
}

@Component(modules = [ParsingModule::class])
interface MainComponent {
  val plugin: Plugin

  @Component.Builder
  interface Builder {
    @BindsInstance fun config(config: CodeGenConfig): Builder

    fun build(): MainComponent
  }
}
