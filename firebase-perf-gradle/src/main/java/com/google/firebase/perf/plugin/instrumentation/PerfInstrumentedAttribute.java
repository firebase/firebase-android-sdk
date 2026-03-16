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

import org.objectweb.asm.Attribute;
import org.objectweb.asm.ByteVector;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;

/** Attribute for instrumented classes. */
public class PerfInstrumentedAttribute extends Attribute {

  private final String extra;

  public PerfInstrumentedAttribute(final String extra) {
    super(PerfInstrumentedAttribute.class.getSimpleName());

    this.extra = extra;
  }

  @Override
  public boolean isUnknown() {
    return false;
  }

  @Override
  public boolean isCodeAttribute() {
    return false;
  }

  @Override
  protected Attribute read(
      final ClassReader cr,
      final int off,
      final int len,
      final char[] buf,
      final int codeOff,
      final Label[] labels) {

    return new PerfInstrumentedAttribute(cr.readUTF8(off, buf));
  }

  @Override
  protected ByteVector write(
      final ClassWriter cw,
      final byte[] code,
      final int len,
      final int maxStack,
      final int maxLocals) {

    return new ByteVector().putShort(cw.newUTF8(extra));
  }
}
