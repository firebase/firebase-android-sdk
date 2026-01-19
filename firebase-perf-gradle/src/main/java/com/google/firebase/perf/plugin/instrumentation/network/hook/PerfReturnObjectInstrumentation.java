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

package com.google.firebase.perf.plugin.instrumentation.network.hook;

import com.google.firebase.perf.plugin.instrumentation.network.NetworkObjectInstrumentation;
import com.google.firebase.perf.plugin.util.AsmString;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Base Instrumentation for Oracle Java "URLConnection" functions.
 *
 * <p>This will inject a call to Firebase Perf SDK function
 * "FirebasePerfUrlConnection#instrument(Object)" after a UrlConnection function.
 */
public class PerfReturnObjectInstrumentation implements NetworkObjectInstrumentation, Opcodes {

  private final String returnType;

  public PerfReturnObjectInstrumentation(final String returnType) {
    this.returnType = returnType;
  }

  @Override
  public void injectBefore(final MethodVisitor methodVisitor) {}

  @Override
  public void injectAfter(final MethodVisitor methodVisitor) {
    // Call FirebasePerfUrlConnection.instrument(Object) and cast the instrumented object to
    // the expected type
    methodVisitor.visitMethodInsn(
        INVOKESTATIC,
        AsmString.CLASS_FIREBASE_PERF_URL_CONNECTION,
        AsmString.METHOD_FIREBASE_PERF_URL_CONNECTION_INSTRUMENT_WITH_OBJECT,
        AsmString.METHOD_DESC_FIREBASE_PERF_URL_CONNECTION_INSTRUMENT_WITH_OBJECT,
        /* isInterface */ false);

    methodVisitor.visitTypeInsn(CHECKCAST, returnType);
  }

  @Override
  public boolean replaceMethod(final MethodVisitor methodVisitor, final int opcode) {
    return false;
  }
}
