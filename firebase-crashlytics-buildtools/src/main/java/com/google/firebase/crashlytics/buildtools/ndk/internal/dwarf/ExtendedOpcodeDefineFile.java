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

import com.google.common.base.Charsets;
import com.google.firebase.crashlytics.buildtools.utils.io.ByteReader;
import java.io.IOException;

/**
 * Processes the <code>DW_LNE_define_file extended opcode</code> for the line number state machine.
 */
public class ExtendedOpcodeDefineFile implements DebugLineOpcode {

  /**
   * <p>Takes 4 arguments. The first is a null terminated string containing a source file name. The
   * second is an unsigned LEB128 number representing the directory index of the directory in
   * which the file was found. The third is an unsigned LEB128 number representing the time
   * of last modification of the file. The fourth is an unsigned LEB128 number representing the
   * length in bytes of the file. The time and length fields may contain LEB128(0) if the
   * information is not available.</p>
   *
   * <p>The directory index represents an entry in the include_directories section of the
   * statement program prologue. The index is LEB128(0) if the file was found in the current
   * directory of the compilation, LEB128(1) if it was found in the first directory in the
   * include_directories section, and so on. The directory index is ignored for file
   * names that represent full path names.</p>
   *
   * <p>The files are numbered, starting at 1, in the order in which they appear; the names in the
   * prologue come before names defined by the <code>DW_LNE_define_file</code> instruction. These
   * numbers are used in the the file register of the state machine.</p>
   */
  @Override
  public boolean process(DebugLineContext context, ByteReader dataReader) throws IOException {
    String fileName = dataReader.readNullTerminatedString(Charsets.UTF_8);
    int dirIndex = dataReader.readULEB128(); // Directory index
    int modTime = dataReader.readULEB128(); // Modification time.
    int fileLength = dataReader.readULEB128(); // file length.
    context.defineFile(fileName, dirIndex, modTime, fileLength);
    return false;
  }
}
