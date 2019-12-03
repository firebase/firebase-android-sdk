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

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

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
}
