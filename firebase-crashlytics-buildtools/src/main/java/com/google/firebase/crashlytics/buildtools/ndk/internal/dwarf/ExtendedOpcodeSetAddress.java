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
 * Processes the <code>DW_LNE_set_address</code> extended opcode for the line number state machine.
 */
public class ExtendedOpcodeSetAddress implements DebugLineOpcode {

  /**
   * Takes a single relocatable address as an operand. The size of the operand is the size
   * appropriate to hold an address on the target machine. Set the address register to the
   * value given by the relocatable address.
   */
  @Override
  public boolean process(DebugLineContext context, ByteReader dataReader) throws IOException {
    long address = dataReader.readLong(context.offsetSize);
    context.reg.address = address;
    return false;
  }
}
