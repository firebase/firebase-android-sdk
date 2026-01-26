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
 * Processes the <code>DW_LNS_set_negate_stmt</code> standard opcode for the line number state machine.
 */
public class StandardOpcodeNegateStatement implements DebugLineOpcode {

  /**
   * Takes no arguments. Set the <code>is_stmt</code> register of the state machine to the logical negation
   * of its current value.
   */
  @Override
  public boolean process(DebugLineContext context, ByteReader dataReader) throws IOException {
    context.reg.isStatement = !context.reg.isStatement;
    return false;
  }
}
