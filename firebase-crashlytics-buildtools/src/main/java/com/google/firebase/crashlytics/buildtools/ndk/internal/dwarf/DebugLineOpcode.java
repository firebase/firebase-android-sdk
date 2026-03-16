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
 * Interface for processing an opcode in the statement program encoded
 * in the DWARF <code>debug_line</code> section of an object file.
 */
public interface DebugLineOpcode {
  /**
   * Process the opcode using the current state of the given DwarfLineContext and ByteReader.
   * @param context the current context for processing opcodes in the state machine.
   * @param dataReader the ByteReader providing the opcode arguments/operands.
   * @return a boolean value describing whether or not to add a new entry to the data matrix.
   * @throws IOException if there is a problem reading from the given ByteReader.
   */
  boolean process(DebugLineContext context, ByteReader dataReader) throws IOException;
}
