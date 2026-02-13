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

package com.google.firebase.perf.plugin.sdk;

import com.google.firebase.perf.plugin.util.AsmString;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.Method;

/**
 * A helper class to generate bytecode for methods of Firebase Perf SDK
 * "com.google.firebase.perf.metrics.Trace" class.
 */
public class FirebaseTrace {

  private final AdviceAdapter adviceAdapter;
  private final Type classType;

  private int timerLocalIndex = -1;

  public FirebaseTrace(final AdviceAdapter methodVisitor) {
    adviceAdapter = methodVisitor;
    classType = Type.getObjectType(AsmString.CLASS_FIREBASE_PERF_TRACE);
  }

  /**
   * Generates bytecode for Firebase Perf SDK function
   * "com.google.firebase.perf.metrics.Trace#start()".
   */
  public void start(final String timerName) {
    timerLocalIndex = adviceAdapter.newLocal(classType);
    adviceAdapter.push(timerName);

    adviceAdapter.invokeStatic(
        Type.getObjectType(AsmString.CLASS_FIREBASE_PERFORMANCE),
        new Method(
            AsmString.METHOD_FIREBASE_PERFORMANCE_START_TRACE_WITH_STRING,
            AsmString.METHOD_DESC_FIREBASE_PERFORMANCE_START_TRACE_WITH_STRING));

    adviceAdapter.storeLocal(timerLocalIndex);
  }

  /**
   * Generates bytecode for Firebase Perf SDK function
   * "com.google.firebase.perf.metrics.Trace#stop()".
   */
  public void stop() {
    if (timerLocalIndex == -1) {
      throw new IllegalStateException(
          "FirebaseTrace.stop called without calling FirebaseTrace.start");
    }

    adviceAdapter.loadLocal(timerLocalIndex);
    adviceAdapter.invokeVirtual(
        classType,
        new Method(
            AsmString.METHOD_FIREBASE_PERF_TRACE_STOP,
            AsmString.METHOD_DESC_FIREBASE_PERF_TRACE_STOP));
  }
}
