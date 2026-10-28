/*
 * Copyright 2024 Google LLC
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

package com.google.firebase.perf.plugin.instrumentation;

import static com.google.common.truth.Truth.assertThat;

import com.google.firebase.perf.plugin.util.AsmString;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Unit tests for {@link InstrumentationVisitor}. */
public class InstrumentationVisitorTest {

  // LINT.IfChange(asm_api_Version)
  private static final int CURRENT_ASM_API_VERSION = Opcodes.ASM9;

  // LINT.ThenChange()

  private static final String CLASS_NAME = "com/example/StringFactory";
  private static final String METHOD = "get()Ljava/lang/String;";
  private static final String BRIDGE_METHOD = "get()Ljava/lang/Object;";

  @Test
  public void instrumentationVisitorAsmApiVersion_matchesCurrentAsmApiVersion() {
    assertThat(Instrument.ASM_API_VERSION).isEqualTo(CURRENT_ASM_API_VERSION);
  }

  @Test
  public void addTraceOnBridgeMethod_onlyInstrumentsTheBridgedMethod() {
    byte[] instrumented =
        new Instrument(getClass().getClassLoader()).instrument(newClassWithAnnotatedBridgeMethod());

    Map<String, Integer> startTraceCalls = countStartTraceCallsPerMethod(instrumented);
    assertThat(startTraceCalls).containsEntry(METHOD, 1);
    assertThat(startTraceCalls).containsEntry(BRIDGE_METHOD, 0);
  }

  /**
   * Returns the bytecode that the compiler generates for the following class:
   *
   * <pre>{@code
   * class StringFactory implements Supplier<String> {
   *   @AddTrace(name = "get")
   *   public String get() {
   *     return "value";
   *   }
   * }
   * }</pre>
   *
   * <p>This includes the synthetic {@code Object get()} bridge method. javac, and Kotlin 2.4 and
   * later, copy the annotations of the bridged method onto the bridge method.
   */
  private static byte[] newClassWithAnnotatedBridgeMethod() {
    ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
    classWriter.visit(
        Opcodes.V1_8,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
        CLASS_NAME,
        "Ljava/lang/Object;Ljava/util/function/Supplier<Ljava/lang/String;>;",
        "java/lang/Object",
        new String[] {"java/util/function/Supplier"});

    MethodVisitor constructor =
        classWriter.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
    constructor.visitCode();
    constructor.visitVarInsn(Opcodes.ALOAD, 0);
    constructor.visitMethodInsn(
        Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", /* isInterface= */ false);
    constructor.visitInsn(Opcodes.RETURN);
    constructor.visitMaxs(0, 0);
    constructor.visitEnd();

    MethodVisitor method =
        classWriter.visitMethod(Opcodes.ACC_PUBLIC, "get", "()Ljava/lang/String;", null, null);
    visitAddTraceAnnotation(method);
    method.visitCode();
    method.visitLdcInsn("value");
    method.visitInsn(Opcodes.ARETURN);
    method.visitMaxs(0, 0);
    method.visitEnd();

    MethodVisitor bridge =
        classWriter.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC,
            "get",
            "()Ljava/lang/Object;",
            null,
            null);
    visitAddTraceAnnotation(bridge);
    bridge.visitCode();
    bridge.visitVarInsn(Opcodes.ALOAD, 0);
    bridge.visitMethodInsn(
        Opcodes.INVOKEVIRTUAL, CLASS_NAME, "get", "()Ljava/lang/String;", /* isInterface= */ false);
    bridge.visitInsn(Opcodes.ARETURN);
    bridge.visitMaxs(0, 0);
    bridge.visitEnd();

    classWriter.visitEnd();
    return classWriter.toByteArray();
  }

  private static void visitAddTraceAnnotation(MethodVisitor methodVisitor) {
    AnnotationVisitor annotationVisitor =
        methodVisitor.visitAnnotation(
            AsmString.ANNOTATION_FIREBASE_PERF_ADD_TRACE, /* visible= */ false);
    annotationVisitor.visit("name", "get");
    annotationVisitor.visitEnd();
  }

  /** Returns the number of {@code FirebasePerformance.startTrace()} calls in each method. */
  private static Map<String, Integer> countStartTraceCallsPerMethod(byte[] classBytes) {
    Map<String, Integer> startTraceCalls = new HashMap<>();
    new ClassReader(classBytes)
        .accept(
            new ClassVisitor(Instrument.ASM_API_VERSION) {
              @Override
              public MethodVisitor visitMethod(
                  int access,
                  String name,
                  String descriptor,
                  String signature,
                  String[] exceptions) {
                String method = name + descriptor;
                startTraceCalls.put(method, 0);
                return new MethodVisitor(Instrument.ASM_API_VERSION) {
                  @Override
                  public void visitMethodInsn(
                      int opcode,
                      String owner,
                      String calledMethodName,
                      String calledMethodDescriptor,
                      boolean isInterface) {
                    if (owner.equals(AsmString.CLASS_FIREBASE_PERFORMANCE)
                        && calledMethodName.equals(
                            AsmString.METHOD_FIREBASE_PERFORMANCE_START_TRACE_WITH_STRING)) {
                      startTraceCalls.merge(method, 1, Integer::sum);
                    }
                  }
                };
              }
            },
            /* parsingOptions= */ 0);
    return startTraceCalls;
  }
}
