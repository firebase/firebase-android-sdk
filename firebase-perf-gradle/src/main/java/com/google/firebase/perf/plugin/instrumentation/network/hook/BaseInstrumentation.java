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
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Base Instrumentation for Oracle Java ("URL" and "URLConnection") and OkHttp ( "Call") functions.
 * This will get the network object that is created.
 */
public class BaseInstrumentation implements NetworkObjectInstrumentation {

  private final String owner;
  private final String name;
  private final String desc;

  public BaseInstrumentation(final String owner, final String name, final String desc) {
    this.owner = owner;
    this.name = name;
    this.desc = desc;
  }

  @Override
  public void injectBefore(final MethodVisitor methodVisitor) {}

  @Override
  public void injectAfter(final MethodVisitor methodVisitor) {}

  @Override
  public boolean replaceMethod(final MethodVisitor methodVisitor, final int opcode) {
    methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, owner, name, desc, /* isInterface */ false);
    return true;
  }

  @Override
  public String toString() {
    return "[" + getClass().getSimpleName() + " : " + owner + " : " + name + " : " + desc + "]";
  }
}
