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

package com.google.firebase.crashlytics.buildtools.ndk.internal.dwarf;

import com.google.firebase.crashlytics.buildtools.utils.io.ByteReader;
import java.io.IOException;

/**
 * Processes the <code>DW_LNS_fixed_advance_pc</code> standard opcode for the line number state machine.
 */
public class StandardOpcodeFixedAdvancePC implements DebugLineOpcode {

  /**
   * Takes a single uhalf operand. Add to the <code>address</code> register of the state machine the value
   * of the (unencoded) operand. This is the only extended opcode that takes an argument that
   * is not a variable length number.
   */
  @Override
  public boolean process(DebugLineContext context, ByteReader dataReader) throws IOException {
    int inc = dataReader.readInt(2);
    context.reg.address += inc;
    return false;
  }
}
