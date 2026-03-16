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
 * Processes the <code>DW_LNS_copy</code> standard opcode for the line number state machine.
 */
public class StandardOpcodeCopy implements DebugLineOpcode {

  /**
   * Takes no arguments. Append a row to the matrix using the current values of the state machine
   * registers. Then set the <code>basic_block</code> register to <code>false</code>.
   */
  @Override
  public boolean process(DebugLineContext context, ByteReader dataReader) throws IOException {
    // The values being reset here are not written to the matrix, so it's safe to reset them and
    // then report back.
    // DWARF 4 also specifies to reset 'discriminator', 'prologue_end', and 'epilogue_begin'.
    context.reg.discriminator = 0;
    context.reg.isBasicBlock = false;
    context.reg.isPrologueEnd = false;
    context.reg.isEpilogueBegin = false;
    return true;
  }
}
