// Copyright 2020 Google LLC
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
public class ExtraPropertyProcessorTest {
  @Test
  public void test() {
    Compilation result =
        javac()
            .withProcessors(new ExtraPropertyProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "com.example.EmptyAnnotation",
                    "package com.example;",
                    "import com.google.firebase.encoders.annotations.ExtraProperty;",
                    "@ExtraProperty",
                    "public @interface EmptyAnnotation {}"));

    assertThat(result).succeededWithoutWarnings();
    assertThat(result)
        .generatedSourceFile("com/example/AtEmptyAnnotation")
        .hasSourceEquivalentTo(JavaFileObjects.forResource("ExpectedAtEmptyAnnotation.java"));
  }

  @Test
  public void test2() {
    Compilation result =
        javac()
            .withProcessors(new ExtraPropertyProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "com.example.MyAnnotation",
                    "package com.example;",
                    "import com.google.firebase.encoders.annotations.ExtraProperty;",
                    "@ExtraProperty",
                    "public @interface MyAnnotation {",
                    "int intVal();",
                    "long longVal();",
                    "boolean boolVal();",
                    "short shortVal();",
                    "float floatVal();",
                    "double doubleVal();",
                    "double[] doubleArrayVal();",
                    "String strVal() default \"default\";",
                    "MyEnum enumVal() default MyEnum.VALUE1;",
                    "enum MyEnum { VALUE1, VALUE2 }",
                    "}"));

    assertThat(result).succeededWithoutWarnings();
    assertThat(result)
        .generatedSourceFile("com/example/AtMyAnnotation")
        .hasSourceEquivalentTo(JavaFileObjects.forResource("ExpectedAtMyAnnotation.java"));
  }
}
