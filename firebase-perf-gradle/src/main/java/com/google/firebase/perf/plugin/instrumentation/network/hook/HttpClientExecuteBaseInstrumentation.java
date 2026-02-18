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
 * Instrumentation for all Apache ("HttpClient") calls. This replaces HttpClient calls with a
 * relevant Firebase Perf SDK function.
 */
public abstract class HttpClientExecuteBaseInstrumentation implements NetworkObjectInstrumentation {

  private final String methodName;
  private final String methodDesc;

  protected HttpClientExecuteBaseInstrumentation(final String methodName, final String methodDesc) {
    this.methodName = methodName;
    this.methodDesc = methodDesc;
  }

  @Override
  public void injectBefore(final MethodVisitor methodVisitor) {}

  @Override
  public void injectAfter(final MethodVisitor methodVisitor) {}

  @Override
  public boolean replaceMethod(final MethodVisitor methodVisitor, final int opcode) {
    methodVisitor.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        AsmString.CLASS_FIREBASE_PERF_HTTP_CLIENT,
        methodName,
        methodDesc,
        /* isInterface */ false);

    return true;
  }
}
