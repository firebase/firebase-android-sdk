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

/** Represents a protocol buffer type. */
sealed class ProtobufType {
  abstract val javaName: String
}

/**
 * Primitive protocol buffer type.
 *
 * A set of all possible primitive types is defined below.
 *
 * @property javaName Java type name that corresponds to a given protobuf primitive type.
 * @property defaultValue Default literal value of a primitive type according to the proto3 spec.
 */
sealed class Primitive(override val javaName: String, val defaultValue: String) : ProtobufType() {
  override fun toString(): String = this::class.java.simpleName

  object INT32 : Primitive("int", "0")
  object SINT32 : Primitive("int", "0")
  object FIXED32 : Primitive("int", "0")
  object SFIXED32 : Primitive("int", "0")
  object FLOAT : Primitive("float", "0")

  object INT64 : Primitive("long", "0")
  object SINT64 : Primitive("long", "0")
  object FIXED64 : Primitive("long", "0")
  object SFIXED64 : Primitive("long", "0")
  object DOUBLE : Primitive("double", "0")

  object BOOLEAN : Primitive("boolean", "false")
  object STRING : Primitive("String", "\"\"")
  object BYTES : Primitive("byte[]", "new byte[0]")
}

/**
 * User defined type.
 *
 * Can be one of [Message] or [ProtoEnum].
 */
sealed class UserDefined : ProtobufType() {
  /** A fully qualified protobuf message name, i.e. `com.example.Outer.Inner`. */
  abstract val protobufFullName: String

  /**
   * Specifies the scope that this type is defined in, i.e. a [Owner.Package] or a parent [Message].
   */
  abstract val owner: Owner

  /** Unqualified name of this type */
  abstract val name: String

  /** A fully qualified java name of this type, i.e. `com.example.Outer$Inner`. */
  override val javaName: String
    get() = "${owner.javaName}${owner.scopeSeparator}$name"

  /** Represents a protobuf `message` type. */
  class Message(override val owner: Owner, override val name: String, fields: List<ProtoField>) :
    UserDefined() {
    private var mutableFields: List<ProtoField> = fields
    override val protobufFullName: String
      get() = "${owner.protobufFullName}.$name"

    override fun toString(): String {
      return "Message(owner=$owner,name=$name,fields=$fields)"
    }

    val fields: List<ProtoField>
      get() = mutableFields

    fun addFields(fields: Iterable<ProtoField>) {
      mutableFields = mutableFields + fields
    }
  }

  /** Represents a protobuf `enum` type. */
  data class ProtoEnum(
    override val owner: Owner,
    override val name: String,
    val values: List<Value>
  ) : UserDefined() {
    override val protobufFullName: String
      get() = "${owner.protobufFullName}.$name"

    /** Represents possible enum values including name and field number. */
    data class Value(val name: String, val value: Int)
  }
}

/**
 * Represent a not yet resolved type, only its fully qualified name is known.
 *
 * This type is required during the parsing process. The issue is that when fields are parsed they
 * can reference types that have not themselves been parsed yet. Additionally reference cycles are
 * possible, when a message `A` has a field of type `A` either directly or transitively.
 *
 * To address that all non-primitive fields are initially set to [Unresolved], and once all messages
 * are parsed, all unresolved references are replaced with their respective [protobuf types]
 * [protobufName].
 *
 * @property protobufName Fully-qualified protobuf name of the message/enum.
 */
data class Unresolved(val protobufName: String) : ProtobufType() {
  override val javaName: String
    get() =
      throw UnsupportedOperationException(
        "Unresolved types don't have a javaName, they are intended to be resolved " +
          "after parsing is complete."
      )
}

/**
 * Own user define types.
 *
 * According to the protocol buffer language definition, user defined types can either be
 * file-scoped or nested within another [UserDefined.Message]. Hence the only possible owners are
 * [Owner.Package] or [UserDefined.Message] (represented by [Owner.MsgRef]) respectively.
 */
sealed class Owner(val scopeSeparator: Char) {
  abstract val protobufFullName: String
  abstract val fileName: String
  abstract val javaName: String

  /** Represents a package that a protobuf type belongs to. */
  data class Package(val name: String, val javaPackage: String, override val fileName: String) :
    Owner('.') {
    override val protobufFullName: String
      get() = name

    override val javaName: String
      get() = javaPackage
  }

  /** Represents a message that contains nested protobuf types. */
  data class MsgRef(val message: UserDefined.Message) : Owner('$') {
    override val protobufFullName: String
      get() = message.protobufFullName
    override val fileName: String
      get() = message.owner.fileName
    override val javaName: String
      get() = message.javaName

    override fun toString(): String = "MsgRef(name=${message.protobufFullName})"
  }
}

private val SNAKE_CASE_REGEX = "_[a-zA-Z]".toRegex()

/**
 * Represents a field of a protobuf message.
 *
 * @property name name of the field as defined in the proto file, usually camel_cased.
 * @property type this property is mutable because it's not always possible specify the type
 * ```
 *      upfront. See [Unresolved] for details.
 * ```
 */
data class ProtoField(
  val name: String,
  var type: ProtobufType,
  val number: Int,
  val repeated: Boolean = false
) {
  /** Custom toString() needed to avoid stackoverflow if case of message reference cycles. */
  override fun toString(): String =
    "ProtoField(\"$name\":$number, ${
    type.let {
        when (it) {
            is UserDefined.Message -> it.protobufFullName
            else -> it.toString()
        }
    }
    })"

  val lowerCamelCaseName: String
    get() {
      return SNAKE_CASE_REGEX.replace(name) { it.value.replace("_", "").toUpperCase() }
    }

  val camelCaseName: String
    get() = lowerCamelCaseName.capitalize()
}
