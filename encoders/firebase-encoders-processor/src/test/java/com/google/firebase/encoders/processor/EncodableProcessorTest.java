// Copyright 2019 Google LLC
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

package com.google.firebase.encoders.processor;

import static com.google.common.truth.Truth.assertThat;
import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.auto.value.processor.AutoValueProcessor;
import com.google.common.truth.StringSubject;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class EncodableProcessorTest {
  @Test
  public void compile_validClass_shouldProduceValidEncoder() {
    Compilation result =
        javac()
            .withProcessors(new EncodableProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "SimpleClass",
                    "import com.google.firebase.encoders.annotations.Encodable;",
                    "@Encodable public class SimpleClass {",
                    "public int getInt() { return 0; }",
                    "public boolean isBool() { return false; }",
                    "public java.util.Map<String, Integer> getMap() { return null; }",
                    "@Encodable.Ignore public long getIgnored() { return 1; }",
                    "@Encodable.Field(name = \"foo\")",
                    "public String getField() { return null; }",
                    "int getNonPublicFieldIgnored() { return 0; }",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "TestEncoderCompile",
                    "import com.google.firebase.encoders.config.EncoderConfig;",
                    "class TestEncoderCompile{",
                    "void test(EncoderConfig<?> cfg) {",
                    "AutoSimpleClassEncoder.CONFIG.configure(cfg);",
                    "}",
                    "}"));

    String expected = "public class AutoSimpleClassEncoder implements Configurator {}";

    assertThat(result).succeededWithoutWarnings();
    assertThat(result)
        .generatedSourceFile("AutoSimpleClassEncoder")
        .hasSourceEquivalentTo(JavaFileObjects.forResource("ExpectedSimpleClassEncoder.java"));
  }

  @Test
  public void compile() {
    Compilation result =
        javac()
            .withProcessors(new EncodableProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "InvalidMap",
                    "import com.google.firebase.encoders.annotations.Encodable;",
                    "@Encodable public class InvalidMap {",
                    "public java.util.Map<Integer, Integer> getMap() { return null; }",
                    "}"));

    assertThat(result).hadErrorContaining("Cannot encode Maps with non-String keys");
  }

  @Test
  public void compileNested_shouldSucceed() {
    Compilation result =
        javac()
            .withProcessors(new EncodableProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "SimpleClass",
                    "import com.google.firebase.encoders.annotations.Encodable;",
                    "public class SimpleClass {",
                    "@Encodable public static class Nested {",
                    "public int getInt() { return 0; }",
                    "}}"));

    assertThat(result).succeededWithoutWarnings();
    assertThat(result).generatedSourceFile("AutoSimpleClassNestedEncoder");
  }

  @Test
  public void compile_withNonStaticNestedInsideGenericClass_shouldFail() {
    Compilation result =
        javac()
            .withProcessors(new EncodableProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "GenericClass",
                    "import com.google.firebase.encoders.annotations.Encodable;",
                    "public class GenericClass<T> {",
                    "@Encodable public class Nested {",
                    "public T getT() { return null; }",
                    "}",
                    "}"));

    assertThat(result).hadErrorContaining("Could not infer type");
  }

  @Test
  public void compile_withGenericClass_ShouldWarnAboutPotentialProblems() {
    Compilation result =
        javac()
            .withProcessors(new EncodableProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "GenericClass",
                    "import com.google.firebase.encoders.annotations.Encodable;",
                    "@Encodable public class GenericClass<T, U> {",
                    "public T getT() { return null; }",
                    "public U getU() { return null; }",
                    "}"));

    assertThat(result).hadWarningContaining("GenericClass<T,U> is a generic type");
    assertThat(result)
        .generatedSourceFile("AutoGenericClassEncoder")
        .hasSourceEquivalentTo(
            JavaFileObjects.forResource("ExpectedGenericsEncoderWithUnknownType.java"));
  }

  @Test
  public void compile_withMultipleGenericArgs_shouldCreateEncodersForAllKnownGenericArgs() {
    Compilation result =
        javac()
            .withProcessors(new EncodableProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "Generics",
                    "import com.google.firebase.encoders.annotations.Encodable;",
                    "@Encodable public class Generics {",
                    "public Bar<Member3> getBar3() { return null; }",
                    "public Bar<Member4> getBar4() { return null; }",
                    "public Multi<Member, Member2> getMulti() { return null; }",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "Foo", "class Foo<T>{", "public T getT() { return null; }", "}"),
                JavaFileObjects.forSourceLines(
                    "Baz", "class Baz<T>{", "public T getT() { return null; }", "}"),
                JavaFileObjects.forSourceLines(
                    "Bar", "class Bar<T> {", "public Baz<Foo<T>> getFoo() { return null; }", "}"),
                JavaFileObjects.forSourceLines(
                    "Multi",
                    "class Multi<T, U> {",
                    "public Baz<Foo<T>> getFooT() { return null; }",
                    "public Baz<Foo<U>> getFooU() { return null; }",
                    "}"),
                JavaFileObjects.forSourceLines("Member", "class Member {}"),
                JavaFileObjects.forSourceLines("Member2", "class Member2 {}"),
                JavaFileObjects.forSourceLines("Member3", "class Member3 {}"),
                JavaFileObjects.forSourceLines("Member3", "class Member4 {}"));

    assertThat(result).succeededWithoutWarnings();
    assertThat(result)
        .generatedSourceFile("AutoGenericsEncoder")
        .hasSourceEquivalentTo(JavaFileObjects.forResource("ExpectedGenericsEncoder.java"));
  }

  @Test
  public void compile_withRecursiveGenericTypes_shouldSucceed() {
    Compilation result =
        javac()
            .withProcessors(new EncodableProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "com.example.MainClass",
                    "package com.example;",
                    "import com.google.firebase.encoders.annotations.Encodable;",
                    "@Encodable public class MainClass {",
                    "public Child<String> getChild() { return null; }",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "com.example.Child",
                    "package com.example;",
                    "public class Child<T> {",
                    "public Child<String> getStringChild() { return null; }",
                    "public Child<Integer> getIntChild() { return null; }",
                    "public MainClass getMain() { return null; }",
                    "}"));
    assertThat(result).succeededWithoutWarnings();
    assertThat(result)
        .generatedSourceFile("com/example/AutoMainClassEncoder")
        .hasSourceEquivalentTo(JavaFileObjects.forResource("ExpectedRecursiveGenericEncoder.java"));
  }

  @Test
  public void compile_withOptional_shouldUseOrElseNull() {
    Compilation result =
        javac()
            .withProcessors(new EncodableProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "WithOptional",
                    "import com.google.firebase.encoders.annotations.Encodable;",
                    "import java.util.Optional;",
                    "@Encodable public class WithOptional {",
                    "@Encodable.Field(name = \"hello\")",
                    "public Optional<Member> getOptional() { return Optional.empty(); }",
                    "}"),
                JavaFileObjects.forSourceLines("Member", "public class Member {}"));

    assertThat(result)
        .generatedSourceFile("AutoWithOptionalEncoder")
        .contentsAsUtf8String()
        .contains("\"hello\", value.getOptional().orElse(null)");
  }

  @Test
  public void compile_withAutoValueInSamePackage_shouldRegisterGeneratedSubclass() {
    Compilation result =
        javac()
            .withProcessors(new AutoValueProcessor(), new EncodableProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "Foo",
                    "import com.google.firebase.encoders.annotations.Encodable;",
                    "import com.google.auto.value.AutoValue;",
                    "@Encodable @AutoValue public abstract class Foo {",
                    "public abstract String getField();",
                    "}"));

    assertThat(result).succeededWithoutWarnings();
    assertThat(result)
        .generatedSourceFile("AutoFooEncoder")
        .contentsAsUtf8String()
        .contains("cfg.registerEncoder(AutoValue_Foo.class");
  }

  @Test
  public void compile_withAutoValueInDifferentPackage_shouldRegisterGeneratedSubclass() {
    Compilation result =
        javac()
            .withProcessors(new AutoValueProcessor(), new EncodableProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "com.example.Foo",
                    "package com.example;",
                    "import com.google.firebase.encoders.annotations.Encodable;",
                    "@Encodable public class Foo {",
                    "public com.example.sub.Member getMember() { return null; }",
                    "public com.example.sub.AnotherMember getAnotherMember() { return null; }",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "com.example.sub.Member",
                    "package com.example.sub;",
                    "import com.google.auto.value.AutoValue;",
                    "@AutoValue public abstract class Member {",
                    "public abstract String getField();",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "com.example.sub.AnotherMember",
                    "package com.example.sub;",
                    "import com.google.auto.value.AutoValue;",
                    "@AutoValue public abstract class AnotherMember {",
                    "public abstract String getField();",
                    "}"));

    assertThat(result).succeededWithoutWarnings();
    assertThat(result)
        .generatedSourceFile("com/example/AutoFooEncoder")
        .contentsAsUtf8String()
        .contains(
            "cfg.registerEncoder("
                + "com.example.sub.EncodableComExampleFooMemberAutoValueSupport.TYPE,"
                + " MemberEncoder.INSTANCE)");
    assertThat(result).succeededWithoutWarnings();
    assertThat(result)
        .generatedSourceFile("com/example/AutoFooEncoder")
        .contentsAsUtf8String()
        .contains(
            "cfg.registerEncoder("
                + "com.example.sub.EncodableComExampleFooAnotherMemberAutoValueSupport.TYPE,"
                + " AnotherMemberEncoder.INSTANCE)");
    assertThat(result)
        .generatedSourceFile("com/example/sub/EncodableComExampleFooMemberAutoValueSupport")
        .contentsAsUtf8String()
        .contains("Class<? extends Member> TYPE = AutoValue_Member.class");
    assertThat(result)
        .generatedSourceFile("com/example/sub/EncodableComExampleFooAnotherMemberAutoValueSupport")
        .contentsAsUtf8String()
        .contains("Class<? extends AnotherMember> TYPE = AutoValue_AnotherMember.class");
  }

  @Test
  public void compile_withNestedAutoValueInSamePackage_shouldRegisterGeneratedSubclass() {
    Compilation result =
        javac()
            .withProcessors(new AutoValueProcessor(), new EncodableProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "Foo",
                    "import com.google.firebase.encoders.annotations.Encodable;",
                    "import com.google.auto.value.AutoValue;",
                    "@Encodable @AutoValue public abstract class Foo {",
                    "public abstract Bar getBar();",
                    "@AutoValue public abstract static class Bar {",
                    "public abstract Baz getBaz();",
                    "@AutoValue public abstract static class Baz {",
                    "public abstract String getField();",
                    "}",
                    "}",
                    "}"));

    StringSubject compiled =
        assertThat(result).generatedSourceFile("AutoFooEncoder").contentsAsUtf8String();

    compiled.contains("cfg.registerEncoder(AutoValue_Foo.class");
    compiled.contains("cfg.registerEncoder(AutoValue_Foo_Bar.class");
    compiled.contains("cfg.registerEncoder(AutoValue_Foo_Bar_Baz.class");
  }

  @Test
  public void compile_withNestedAutoValueInDifferentPackage_shouldRegisterGeneratedSubclass() {
    Compilation result =
        javac()
            .withProcessors(new AutoValueProcessor(), new EncodableProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "com.example.Foo",
                    "package com.example;",
                    "import com.google.firebase.encoders.annotations.Encodable;",
                    "@Encodable public class Foo {",
                    "public com.example.sub.Member.SubMember getSubMember() { return null; }",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "com.example.sub.Member",
                    "package com.example.sub;",
                    "import com.google.auto.value.AutoValue;",
                    "@AutoValue public abstract class Member {",
                    "public abstract SubMember getSubMember();",
                    "@AutoValue public abstract static class SubMember {",
                    "public abstract String getField();",
                    "}",
                    "}"));

    assertThat(result).succeededWithoutWarnings();
    assertThat(result)
        .generatedSourceFile("com/example/AutoFooEncoder")
        .contentsAsUtf8String()
        .contains(
            "cfg.registerEncoder("
                + "com.example.sub.EncodableComExampleFooMemberSubMemberAutoValueSupport.TYPE,"
                + " MemberSubMemberEncoder.INSTANCE)");
    assertThat(result)
        .generatedSourceFile(
            "com/example/sub/EncodableComExampleFooMemberSubMemberAutoValueSupport")
        .contentsAsUtf8String()
        .contains("Class<? extends Member.SubMember> TYPE = AutoValue_Member_SubMember.class");
  }

  @Test
  public void compile_withListOfCustomType_shouldRegisterEncoderForThatType() {
    Compilation result =
        javac()
            .withProcessors(new EncodableProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "TypeWithList",
                    "import com.google.firebase.encoders.annotations.Encodable;",
                    "import java.util.List;",
                    "@Encodable class TypeWithList {",
                    "public List<Member> getMember() { return null; }",
                    "}"),
                JavaFileObjects.forSourceLines("Member", "class Member {}"));

    assertThat(result)
        .generatedSourceFile("AutoTypeWithListEncoder")
        .hasSourceEquivalentTo(JavaFileObjects.forResource("ExpectedTypeWithListEncoder.java"));
  }

  @Test
  public void compile_withSetOfSetOfCustomType_shouldRegisterEncoderForThatType() {
    Compilation result =
        javac()
            .withProcessors(new EncodableProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "TypeWithList",
                    "import com.google.firebase.encoders.annotations.Encodable;",
                    "import java.util.Set;",
                    "@Encodable class TypeWithList {",
                    "public Set<Set<Member>> getMember() { return null; }",
                    "}"),
                JavaFileObjects.forSourceLines("Member", "class Member {}"));

    assertThat(result)
        .generatedSourceFile("AutoTypeWithListEncoder")
        .hasSourceEquivalentTo(JavaFileObjects.forResource("ExpectedTypeWithListEncoder.java"));
  }

  @Test
  public void compile_withMapOfListOfCustomType_shouldRegisterEncoderForThatType() {
    Compilation result =
        javac()
            .withProcessors(new EncodableProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "TypeWithList",
                    "import com.google.firebase.encoders.annotations.Encodable;",
                    "import java.util.List;",
                    "import java.util.Map;",
                    "@Encodable class TypeWithList {",
                    "public Map<String, List<Member>> getMember() { return null; }",
                    "}"),
                JavaFileObjects.forSourceLines("Member", "class Member {}"));

    assertThat(result)
        .generatedSourceFile("AutoTypeWithListEncoder")
        .hasSourceEquivalentTo(JavaFileObjects.forResource("ExpectedTypeWithListEncoder.java"));
  }

  @Test
  public void compile_withEnum_shouldNotGenerateEnumEncoder() {
    Compilation result =
        javac()
            .withProcessors(new EncodableProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "TypeWithEnum",
                    "import com.google.firebase.encoders.annotations.Encodable;",
                    "@Encodable class TypeWithEnum {",
                    "public MyEnum getEnum() { return null; }",
                    "}"),
                JavaFileObjects.forSourceLines("MyEnum", "enum MyEnum {VALUE;}"));
    assertThat(result)
        .generatedSourceFile("AutoTypeWithEnumEncoder")
        .contentsAsUtf8String()
        .doesNotContain("cfg.registerEncoder(MyEnum.class");
  }

  @Test
  public void compile_withInlineAnnotation_shouldGenerateInlineEncoder() {
    Compilation result =
        javac()
            .withProcessors(new EncodableProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "OuterType",
                    "import com.google.firebase.encoders.annotations.Encodable;",
                    "@Encodable class OuterType {",
                    "@Encodable.Field(inline = true)",
                    "public Member getMember() { return null; }",
                    "}"),
                JavaFileObjects.forSourceLines("Member", "class Member{}"));

    assertThat(result).succeededWithoutWarnings();
    assertThat(result)
        .generatedSourceFile("AutoOuterTypeEncoder")
        .contentsAsUtf8String()
        .contains("ctx.inline(value.getMember());");
  }

  @Test
  public void packageNameToCamelCase_withDefaultPackage_shouldReturnEmptyString() {
    assertThat(EncodableProcessor.packageNameToCamelCase("")).isEqualTo("");
  }

  @Test
  public void packageNameToCamelCase_withValidPackage_shouldSuccessfullyReturn() {
    assertThat(EncodableProcessor.packageNameToCamelCase("com.example")).isEqualTo("ComExample");
  }

  @Test
  public void packageNameToCamelCase_withInvalidPackage_shouldSuccessfullyReturn() {
    assertThat(EncodableProcessor.packageNameToCamelCase("com.example.")).isEqualTo("ComExample");
  }

  @Test
  public void packageNameToCamelCase_withInvalidPackage2_shouldSuccessfullyReturn() {
    assertThat(EncodableProcessor.packageNameToCamelCase(".example")).isEqualTo("Example");
  }

  @Test
  public void packageNameToCamelCase_withSubPackageOfOneChar_shouldSuccessfullyReturn() {
    assertThat(EncodableProcessor.packageNameToCamelCase("com.example.a")).isEqualTo("ComExampleA");
  }
}
