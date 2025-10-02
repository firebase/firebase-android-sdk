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

import com.google.common.collect.ImmutableListMultimap
import com.google.common.collect.ImmutableMultimap
import com.google.firebase.encoders.proto.CodeGenConfig
import com.google.firebase.encoders.proto.codegen.UserDefined.Message
import com.google.firebase.encoders.proto.codegen.UserDefined.ProtoEnum
import com.google.protobuf.DescriptorProtos
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto
import com.google.protobuf.DescriptorProtos.FileDescriptorProto
import dagger.Binds
import dagger.Module
import javax.inject.Inject

/** Transforms the protobuf message descriptors into a message graph for later use in codegen. */
interface DescriptorParser {
  fun parse(protoFiles: List<FileDescriptorProto>): Collection<UserDefined>
}

/**
 * Default [DescriptorParser] implementation.
 *
 * The parser repackages all input messages into the `vendorPackage` of the provided [config].
 * Additionally it only returns messages that are marked for inclusion in [config].
 *
 * Any extensions that are encountered are added directly to extended messages as fields, so
 * extensions are indistinguishable from message's own fields.
 */
class DefaultParser @Inject constructor(private val config: CodeGenConfig) : DescriptorParser {
  override fun parse(files: List<FileDescriptorProto>): List<UserDefined> {
    val parsedTypes =
      files
        .asSequence()
        .flatMap { file ->
          val javaPackage = "${config.vendorPackage}.${file.`package`}"

          val parent = Owner.Package(file.`package`, javaPackage, file.name)
          parseEnums(parent, file.enumTypeList).plus(parseMessages(parent, file.messageTypeList))
        }
        .toList()

    val extensions = discoverExtensions(files)

    for (type in parsedTypes) {
      val msgExtensions = extensions[type.protobufFullName]
      if (type !is Message || msgExtensions == null) {
        continue
      }
      type.addFields(msgExtensions)
    }
    resolveReferences(parsedTypes)
    return parsedTypes.filter { config.includeList.contains(it.protobufFullName) }
  }

  private fun resolveReferences(types: Collection<UserDefined>) {
    val messageIndex = types.asSequence().map { it.protobufFullName to it }.toMap()
    for (userDefined in types) {
      if (userDefined !is Message) {
        continue
      }
      for (field in userDefined.fields) {
        (field.type as? Unresolved)?.run {
          field.type =
            messageIndex[this.protobufName]
              ?: throw IllegalArgumentException(
                "Unresolved reference to $protobufName in ${userDefined.protobufFullName}."
              )
        }
      }
    }
  }

  private fun discoverExtensions(
    files: List<FileDescriptorProto>
  ): ImmutableMultimap<String, ProtoField> {

    val extensions: ImmutableListMultimap.Builder<String, ProtoField> =
      ImmutableListMultimap.builder<String, ProtoField>()
    for (file in files) {
      for (field in file.extensionList) {
        extensions.put(
          field.extendee.trimStart('.'),
          ProtoField(
            field.name,
            field.determineType(),
            field.number,
            field.label == FieldDescriptorProto.Label.LABEL_REPEATED
          )
        )
      }
    }
    return extensions.build()
  }

  private fun parseEnums(
    parent: Owner,
    enums: List<DescriptorProtos.EnumDescriptorProto>
  ): Sequence<ProtoEnum> {
    return enums.asSequence().map { enum ->
      ProtoEnum(parent, enum.name, enum.valueList.map { ProtoEnum.Value(it.name, it.number) })
    }
  }

  private fun parseMessages(
    parent: Owner,
    messages: List<DescriptorProtos.DescriptorProto>
  ): Sequence<UserDefined> {
    return messages.asSequence().flatMap { msg ->
      val m =
        Message(
          owner = parent,
          name = msg.name,
          fields =
            msg.fieldList.map {
              ProtoField(
                name = it.name,
                type = it.determineType(),
                number = it.number,
                repeated = it.label == FieldDescriptorProto.Label.LABEL_REPEATED
              )
            }
        )
      val newParent = Owner.MsgRef(m)

      sequenceOf(m)
        .plus(parseEnums(newParent, msg.enumTypeList))
        .plus(parseMessages(newParent, msg.nestedTypeList))
    }
  }
}

private fun FieldDescriptorProto.determineType(): ProtobufType {
  if (typeName != "") {
    return Unresolved(typeName.trimStart('.'))
  }

  return when (type) {
    FieldDescriptorProto.Type.TYPE_INT32 -> Primitive.INT32
    FieldDescriptorProto.Type.TYPE_DOUBLE -> Primitive.DOUBLE
    FieldDescriptorProto.Type.TYPE_FLOAT -> Primitive.FLOAT
    FieldDescriptorProto.Type.TYPE_INT64 -> Primitive.INT64
    FieldDescriptorProto.Type.TYPE_UINT64 -> Primitive.INT64
    FieldDescriptorProto.Type.TYPE_FIXED64 -> Primitive.FIXED64
    FieldDescriptorProto.Type.TYPE_FIXED32 -> Primitive.FIXED32
    FieldDescriptorProto.Type.TYPE_BOOL -> Primitive.BOOLEAN
    FieldDescriptorProto.Type.TYPE_STRING -> Primitive.STRING
    FieldDescriptorProto.Type.TYPE_BYTES -> Primitive.BYTES
    FieldDescriptorProto.Type.TYPE_UINT32 -> Primitive.INT32
    FieldDescriptorProto.Type.TYPE_SFIXED32 -> Primitive.SFIXED32
    FieldDescriptorProto.Type.TYPE_SFIXED64 -> Primitive.SFIXED64
    FieldDescriptorProto.Type.TYPE_SINT32 -> Primitive.SINT32
    FieldDescriptorProto.Type.TYPE_SINT64 -> Primitive.SINT64
    else -> throw IllegalArgumentException("$type is not supported")
  }
}

@Module
abstract class ParsingModule {
  @Binds abstract fun bindParser(parser: DefaultParser): DescriptorParser
}
