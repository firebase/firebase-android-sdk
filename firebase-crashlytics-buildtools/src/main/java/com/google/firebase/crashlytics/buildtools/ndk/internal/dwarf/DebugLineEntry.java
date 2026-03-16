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

/**
 * Mapping for a memory address to its file and line number.
 */
public class DebugLineEntry {

  public static final String formatString = "Address: 0x%s, File: %s, Line: %s";

  /**
   * Memory address given by DWARF data.
   */
  public final long address;

  /**
   * The file to which the address maps.
   */
  public final String file;

  /**
   * The line number in the file to which the address maps.
   */
  public final long lineNumber;

  public DebugLineEntry(long address, String file, long lineNumber) {
    this.address = address;
    this.file = file;
    this.lineNumber = lineNumber;
  }

  @Override
  public String toString() {
    return String.format(formatString, file, lineNumber, Long.toHexString(address));
  }
}
