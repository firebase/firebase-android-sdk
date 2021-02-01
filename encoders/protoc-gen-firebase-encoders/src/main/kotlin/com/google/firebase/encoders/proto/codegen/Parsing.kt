// Copyright 2021 Google LLC
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

package com.google.firebase.encoders.proto.codegen

import com.google.firebase.encoders.proto.CodeGenConfig
import com.google.protobuf.DescriptorProtos.FileDescriptorProto
import dagger.Binds
import dagger.Module
import javax.inject.Inject

/**
 * Transforms the protobuf message descriptors into a message graph for later use in codegen.
 */
interface DescriptorParser {
    fun parse(protoFiles: List<FileDescriptorProto>): Collection<UserDefined>
}

class DefaultParser @Inject constructor(private val config: CodeGenConfig) : DescriptorParser {
    override fun parse(protoFiles: List<FileDescriptorProto>): Collection<UserDefined> {
        return listOf()
    }
}

@Module
abstract class ParsingModule {
    @Binds
    abstract fun bindParser(parser: DefaultParser): DescriptorParser
}
