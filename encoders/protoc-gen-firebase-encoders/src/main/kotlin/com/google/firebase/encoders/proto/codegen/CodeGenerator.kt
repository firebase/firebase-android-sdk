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
import com.google.firebase.encoders.proto.codegen.UserDefined.Message
import com.google.firebase.encoders.proto.codegen.UserDefined.ProtoEnum
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import com.squareup.javapoet.AnnotationSpec
import com.squareup.javapoet.ArrayTypeName
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject
import javax.lang.model.element.Modifier

private val ENCODER_CLASS = ClassName.get("com.google.firebase.encoders.proto", "ProtobufEncoder")
private val ENCODABLE_ANNOTATION =
  ClassName.get("com.google.firebase.encoders.annotations", "Encodable")
private val PROTOBUF_ANNOTATION = ClassName.get("com.google.firebase.encoders.proto", "Protobuf")
private val FIELD_ANNOTATION = ENCODABLE_ANNOTATION.nestedClass("Field")
private val IGNORE_ANNOTATION = ENCODABLE_ANNOTATION.nestedClass("Ignore")

internal class Gen(
  private val messages: Collection<UserDefined>,
  private val vendorPackage: String
) {

  private val rootEncoder = ClassName.get(vendorPackage, "ProtoEncoderDoNotUse")
  private val index = mutableMapOf<UserDefined, TypeSpec.Builder>()
  val rootClasses = mutableMapOf<UserDefined, TypeSpec.Builder>()

  fun generate() {
    if (messages.isEmpty()) {
      return
    }

    rootClasses[
      Message(
        owner = Owner.Package(vendorPackage, vendorPackage, "unknown"),
        name = "ProtoEncoderDoNotUse",
        fields = listOf()
      )] = protoEncoder()

    for (message in messages) {
      generate(message)
    }

    // Messages generated above are "shallow". Meaning that they don't include their nested
    // message types. Below we are addressing that by nesting messages within their respective
    // containing classes.
    for (type in index.keys) {
      (type.owner as? Owner.MsgRef)?.let { index[it.message]!!.addType(index[type]!!.build()) }
    }
  }

  /**
   * Generates the "root" `@Encodable` class.
   *
   * This class is the only `@Encodable`-annotated class in the codegen. This ensures that we
   * generate an encoder exactly once per message and there is no code duplication.
   *
   * All "included" messages(as per plugin config) delegate to this class to implement encoding.
   */
  private fun protoEncoder(): TypeSpec.Builder {
    return TypeSpec.classBuilder(rootEncoder)
      .addAnnotation(ENCODABLE_ANNOTATION)
      .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
      .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build())
      .addField(
        FieldSpec.builder(
            ENCODER_CLASS,
            "ENCODER",
            Modifier.PRIVATE,
            Modifier.STATIC,
            Modifier.FINAL
          )
          .initializer(
            "\$T.builder().configureWith(AutoProtoEncoderDoNotUseEncoder.CONFIG).build()",
            ENCODER_CLASS
          )
          .build()
      )
      .addMethod(
        MethodSpec.methodBuilder("encode")
          .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
          .returns(ArrayTypeName.of(TypeName.BYTE))
          .addParameter(Any::class.java, "value")
          .addCode("return ENCODER.encode(value);\n")
          .build()
      )
      .addMethod(
        MethodSpec.methodBuilder("encode")
          .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
          .addException(IOException::class.java)
          .addParameter(Any::class.java, "value")
          .addParameter(OutputStream::class.java, "output")
          .addCode("ENCODER.encode(value, output);\n")
          .build()
      )
      .apply {
        for (message in messages) {
          addMethod(
            MethodSpec.methodBuilder("get${message.name.capitalize()}")
              .returns(message.toTypeName())
              .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
              .build()
          )
        }
      }
  }

  private fun generate(type: UserDefined) {
    if (type in index) return

    index[type] = TypeSpec.classBuilder("Dummy")

    val builder =
      when (type) {
        is ProtoEnum -> generateEnum(type)
        is Message -> {
          val classBuilder = generateClass(type)
          classBuilder.addType(generateBuilder(type))

          for (field in type.fields) {
            (field.type as? UserDefined)?.let { generate(it) }
          }
          classBuilder
        }
      }
    if (type.owner is Owner.Package) {
      rootClasses[type] = builder
    }
    index[type] = builder
  }

  /**
   * Generates an enum.
   *
   * Example generated enum:
   *
   * ```java
   * import com.google.firebase.encoders.proto.ProtoEnum;
   *
   * public enum Foo implements ProtoEnum {
   *   DEFAULT(0),
   *   EXAMPLE(1);
   *
   *   private final int number_;
   *   Foo(int number_) {
   *     this.number_ = number_;
   *   }
   *
   *   @Override
   *   public int getNumber() {
   *     return number_;
   *   }
   * }
   * ```
   */
  private fun generateEnum(type: ProtoEnum): TypeSpec.Builder {
    val builder =
      TypeSpec.enumBuilder(type.name).apply {
        addModifiers(Modifier.PUBLIC)
        this.addSuperinterface(ClassName.get("com.google.firebase.encoders.proto", "ProtoEnum"))
        for (value in type.values) {
          addEnumConstant("${value.name}(${value.value})")
        }
      }
    builder.addField(
      FieldSpec.builder(TypeName.INT, "number_", Modifier.PRIVATE, Modifier.FINAL).build()
    )
    builder.addMethod(
      MethodSpec.constructorBuilder()
        .addModifiers(Modifier.PRIVATE)
        .addParameter(TypeName.INT, "number_")
        .addCode("this.number_ = number_;\n")
        .build()
    )
    builder.addMethod(
      MethodSpec.methodBuilder("getNumber")
        .addModifiers(Modifier.PUBLIC)
        .addAnnotation(Override::class.java)
        .returns(TypeName.INT)
        .addCode("return number_;\n")
        .build()
    )
    return builder
  }

  /**
   * Generates a class that corresponds to a proto message.
   *
   * Example class:
   * ```java
   * public final class Foo {
   *   private final int field_;
   *   private final List<String> str_;
   *   private Foo(int field_, List<String> str_) {
   *     this.field_ = field_;
   *     this.str_ = str_;
   *   }
   *
   *   @Protobuf(tag = 1)
   *   public int getField() { return field_; }
   *
   *   @Protobuf(tag = 2)
   *   @Field(name = "str")
   *   public List<String> getStrList() { return field_; }
   *
   *   // see generateBuilder() below
   *   public static class Builder {}
   *   public static Builder newBuilder() ;
   *
   *   public static Foo getDefaultInstance();
   *
   *   // these are generated only for types explicitly specified in the plugin config.
   *   public void writeTo(OutputStream output) throws IOException;
   *   public byte[] toByteArray();
   *
   * }
   * ```
   */
  // TODO(vkryachko): generate equals() and hashCode()
  private fun generateClass(type: Message): TypeSpec.Builder {
    val messageClass =
      if (type.owner is Owner.Package) {
        TypeSpec.classBuilder(type.name).addModifiers(Modifier.PUBLIC, Modifier.FINAL)
      } else {
        TypeSpec.classBuilder(type.name)
          .addModifiers(Modifier.PUBLIC, Modifier.FINAL, Modifier.STATIC)
      }
    if (type in messages) {
      messageClass
        .addMethod(
          MethodSpec.methodBuilder("toByteArray")
            .addModifiers(Modifier.PUBLIC)
            .returns(ArrayTypeName.of(TypeName.BYTE))
            .addCode("return \$T.encode(this);\n", rootEncoder)
            .build()
        )
        .addMethod(
          MethodSpec.methodBuilder("writeTo")
            .addModifiers(Modifier.PUBLIC)
            .addException(IOException::class.java)
            .addParameter(OutputStream::class.java, "output")
            .addCode("\$T.encode(this, output);\n", rootEncoder)
            .build()
        )
    }
    val messageTypeName = ClassName.bestGuess(type.name)

    val constructor = MethodSpec.constructorBuilder()
    messageClass.addMethod(
      MethodSpec.methodBuilder("newBuilder")
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .returns(ClassName.bestGuess("Builder"))
        .addCode("return new Builder();\n")
        .build()
    )

    for (field in type.fields) {
      messageClass.addField(
        FieldSpec.builder(field.typeName, "${field.name}_", Modifier.PRIVATE, Modifier.FINAL)
          .build()
      )
      constructor.addParameter(field.typeName, "${field.name}_")
      constructor.addCode("this.${field.name}_ = ${field.name}_;\n")

      if (field.repeated || field.type !is Message) {
        messageClass.addMethod(
          MethodSpec.methodBuilder("get${field.camelCaseName}${if (field.repeated) "List" else ""}")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(
              AnnotationSpec.builder(PROTOBUF_ANNOTATION)
                .addMember("tag", "${field.number}")
                .apply { field.type.intEncoding?.let { addMember("intEncoding", it) } }
                .build()
            )
            .apply {
              if (field.repeated) {
                addAnnotation(
                  AnnotationSpec.builder(FIELD_ANNOTATION)
                    .addMember("name", "\$S", field.lowerCamelCaseName)
                    .build()
                )
              }
            }
            .returns(field.typeName)
            .addCode("return ${field.name}_;\n")
            .build()
        )
      } else {
        // this method is for use as public API, it never returns null and falls back to
        // returning default instances.
        messageClass.addMethod(
          MethodSpec.methodBuilder("get${field.camelCaseName}")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(AnnotationSpec.builder(IGNORE_ANNOTATION).build())
            .returns(field.typeName)
            .addCode(
              "return ${field.name}_ == null ? \$T.getDefaultInstance() : ${field.name}_;\n",
              field.typeName
            )
            .build()
        )
        // this method can return null and is needed by the encoder to make sure we don't
        // try to encode default instances, which is:
        // 1. inefficient
        // 2. can lead to infinite recursion in case of self-referential types.
        messageClass.addMethod(
          MethodSpec.methodBuilder("get${field.camelCaseName}Internal")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(
              AnnotationSpec.builder(PROTOBUF_ANNOTATION)
                .addMember("tag", "${field.number}")
                .build()
            )
            .addAnnotation(
              AnnotationSpec.builder(FIELD_ANNOTATION)
                .addMember("name", "\$S", field.lowerCamelCaseName)
                .build()
            )
            .returns(field.typeName)
            .addCode("return ${field.name}_;\n")
            .build()
        )
      }
    }
    messageClass.addMethod(constructor.build())
    messageClass.addField(
      FieldSpec.builder(
          messageTypeName,
          "DEFAULT_INSTANCE",
          Modifier.PRIVATE,
          Modifier.STATIC,
          Modifier.FINAL
        )
        .initializer("new Builder().build()", messageTypeName)
        .build()
    )
    messageClass.addMethod(
      MethodSpec.methodBuilder("getDefaultInstance")
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .returns(messageTypeName)
        .addCode("return DEFAULT_INSTANCE;\n")
        .build()
    )
    return messageClass
  }

  /**
   * Generates a builder for a proto message.
   *
   * Example builder:
   * ```java
   * public static final class Builder {
   *   public Foo build();
   *   public Builder setField(int value);
   *   public Builder setStrList(List<String> value);
   *   public Builder addStr(String value);
   * }
   * ```
   */
  // TODO(vkryachko): oneof setters should clear other oneof cases.
  private fun generateBuilder(type: Message): TypeSpec {
    val messageTypeName = ClassName.bestGuess(type.name)

    val builder =
      TypeSpec.classBuilder("Builder")
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)

    val buildMethodArgs =
      type.fields.joinToString(", ") {
        if (it.repeated) "java.util.Collections.unmodifiableList(${it.name}_)" else "${it.name}_"
      }
    builder.addMethod(
      MethodSpec.methodBuilder("build")
        .addModifiers(Modifier.PUBLIC)
        .returns(messageTypeName)
        .addCode("return new \$T(\$L);\n", messageTypeName, buildMethodArgs)
        .build()
    )

    val builderConstructor = MethodSpec.constructorBuilder()

    for (field in type.fields) {
      builder.addField(
        FieldSpec.builder(field.typeName, "${field.name}_", Modifier.PRIVATE).build()
      )
      builderConstructor
        .addCode("this.${field.name}_ = ")
        .apply {
          field.type.let { t ->
            when (t) {
              is Message ->
                when {
                  field.repeated -> addCode("new \$T<>()", ClassName.get(ArrayList::class.java))
                  else -> addCode("null")
                }
              is Primitive ->
                if (field.repeated) addCode("new \$T<>()", ClassName.get(ArrayList::class.java))
                else addCode(t.defaultValue)
              is ProtoEnum ->
                if (field.repeated) addCode("new \$T<>()", ClassName.get(ArrayList::class.java))
                else addCode(t.defaultValue.replace("$", "."))
              is Unresolved -> TODO()
            }
          }
        }
        .addCode(";\n")
      if (field.repeated) {
        builder.addMethod(
          MethodSpec.methodBuilder("add${field.camelCaseName}")
            .addModifiers(Modifier.PUBLIC)
            .returns(ClassName.bestGuess("Builder"))
            .addParameter(field.type.toTypeName(), "${field.name}_")
            .addCode("this.${field.name}_.add(${field.name}_);\n")
            .addCode("return this;\n")
            .build()
        )
      }
      builder.addMethod(
        MethodSpec.methodBuilder("set${field.camelCaseName}${if (field.repeated) "List" else ""}")
          .addModifiers(Modifier.PUBLIC)
          .returns(ClassName.bestGuess("Builder"))
          .addParameter(field.typeName, "${field.name}_")
          .addCode("this.${field.name}_ = ${field.name}_;\n")
          .addCode("return this;\n")
          .build()
      )
    }
    builder.addMethod(builderConstructor.build())
    return builder.build()
  }
}

private fun ProtobufType.toTypeName(): TypeName {
  return when (this) {
    Primitive.INT32 -> TypeName.INT
    Primitive.SINT32 -> TypeName.INT
    Primitive.FIXED32 -> TypeName.INT
    Primitive.SFIXED32 -> TypeName.INT
    Primitive.FLOAT -> TypeName.FLOAT
    Primitive.INT64 -> TypeName.LONG
    Primitive.SINT64 -> TypeName.LONG
    Primitive.FIXED64 -> TypeName.LONG
    Primitive.SFIXED64 -> TypeName.LONG
    Primitive.DOUBLE -> TypeName.DOUBLE
    Primitive.BOOLEAN -> TypeName.BOOLEAN
    Primitive.STRING -> ClassName.get(String::class.java)
    Primitive.BYTES -> ArrayTypeName.of(TypeName.BYTE)
    is Message -> {
      return when (owner) {
        is Owner.Package -> ClassName.get(owner.javaName, name)
        is Owner.MsgRef -> (owner.message.toTypeName() as ClassName).nestedClass(name)
      }
    }
    is ProtoEnum -> {
      return when (owner) {
        is Owner.Package -> ClassName.get(owner.javaName, name)
        is Owner.MsgRef -> (owner.message.toTypeName() as ClassName).nestedClass(name)
      }
    }
    is Unresolved -> throw AssertionError("Impossible!")
  }
}

val ProtoField.typeName: TypeName
  get() {
    if (!repeated) {
      return type.toTypeName()
    }
    return ParameterizedTypeName.get(ClassName.get(List::class.java), type.boxed)
  }

private val ProtobufType.boxed: TypeName
  get() {
    return when (this) {
      Primitive.INT32,
      Primitive.SINT32,
      Primitive.FIXED32,
      Primitive.SFIXED32 -> ClassName.get("java.lang", "Integer")
      Primitive.INT64,
      Primitive.SINT64,
      Primitive.FIXED64,
      Primitive.SFIXED64 -> ClassName.get("java.lang", "Long")
      Primitive.FLOAT -> ClassName.get("java.lang", "Float")
      Primitive.DOUBLE -> ClassName.get("java.lang", "Double")
      Primitive.BOOLEAN -> ClassName.get("java.lang", "Boolean")
      Primitive.STRING -> ClassName.get("java.lang", "String")
      Primitive.BYTES -> ArrayTypeName.of(TypeName.BYTE)
      else -> toTypeName()
    }
  }

val ProtobufType.intEncoding: String?
  get() {
    return when (this) {
      is Primitive.SINT32,
      Primitive.SINT64 -> "com.google.firebase.encoders.proto.Protobuf.IntEncoding.SIGNED"
      is Primitive.FIXED32,
      Primitive.FIXED64,
      Primitive.SFIXED32,
      Primitive.SFIXED64 -> "com.google.firebase.encoders.proto.Protobuf.IntEncoding.FIXED"
      else -> null
    }
  }

val ProtoEnum.defaultValue: String
  get() = "$javaName.${values.find { it.value == 0 }?.name}"

class CodeGenerator @Inject constructor(private val config: CodeGenConfig) {

  fun generate(messages: Collection<UserDefined>): CodeGeneratorResponse {
    val gen = Gen(messages, config.vendorPackage)
    gen.generate()

    val responseBuilder = CodeGeneratorResponse.newBuilder()
    for ((type, typeBuilder) in gen.rootClasses.entries) {
      val typeSpec = typeBuilder.build()
      val packageName = type.owner.javaName
      val out = StringBuilder()
      val file = JavaFile.builder(packageName, typeSpec).build()
      file.writeTo(out)
      val qualifiedName =
        if (packageName.isEmpty()) typeSpec.name else "$packageName.${typeSpec.name}"
      val fileName = "${qualifiedName.replace('.', '/')}.java"
      responseBuilder.addFile(
        CodeGeneratorResponse.File.newBuilder().setContent(out.toString()).setName(fileName)
      )
    }

    return responseBuilder.build()
  }
}
