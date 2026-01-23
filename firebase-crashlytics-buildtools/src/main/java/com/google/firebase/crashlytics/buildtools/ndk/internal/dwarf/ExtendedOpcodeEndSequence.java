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
 * Processes the <code>DW_LNE_end_sequence</code> extended opcode for the line number state machine.
 */
public class ExtendedOpcodeEndSequence implements DebugLineOpcode {

  /**
   * Set the <code>end_sequence</code> register of the state machine to <code>true</code> and append
   * a row to the matrix using the current values of the state-machine registers. Then reset the
   * registers to the initial values specified above.
   *
   * <p>Every statement program sequence must end with a <code>DW_LNE_end_sequence</code>
   * instruction which creates a row whose address is that of the byte after the last target machine
   * instruction of the sequence.
   */
  @Override
  public boolean process(DebugLineContext context, ByteReader dataReader) throws IOException {
    context.reg.isEndSequence = true;
    // The spec says to add a row to the matrix, but this is not necessary for what we need.
    // It's specifying the end (+1) address of the instructions for the previous line, which is
    // unnecessary for our purposes.
    return false;
  }
}
