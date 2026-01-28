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

package com.google.firebase.crashlytics.buildtools.ndk.internal.elf;

/**
 * Contains data read from a single entry in a symbol table within an ELF file.
 * Fields and constants taken from the Linux C header <code>elf.h</code>, <code>typedef elf32_sym</code>.
 */
public class ElfSymbol {

  /**
   * Symbol is local to the file.
   */
  public static final int STB_LOCAL = 0;

  /**
   * Symbol is visible to all object files.
   */
  public static final int STB_GLOBAL = 1;

  /**
   * Symbol is global with lower precedence.
   */
  public static final int STB_WEAK = 2;

  /**
   * Symbol has no type.
   */
  public static final int STT_NOTYPE = 0;

  /**
   * Symbol is a data object (a variable)
   */
  public static final int STT_OBJECT = 1;

  /**
   * Symbol is a function.
   */
  public static final int STT_FUNC = 2;

  /**
   * Symbol is a section name.
   */
  public static final int STT_SECTION = 3;

  /**
   * Symbol is the file name.
   */
  public static final int STT_FILE = 4;

  /**
   * Offset in the string table of the symbol name.
   */
  public int stName;

  /**
   * The memory address mapping for the symbol.
   */
  public long stValue;

  /**
   * The size (in bytes) of the symbol's data.
   */
  public long stSize;

  /**
   * Upper four bits define the binding of the symbol: (STB constants).
   * Lower four bits define the type of the symbol (STT constants).
   */
  public byte stInfo;

  /**
   * Currently unused.
   */
  public byte stOther;

  /**
   * Index of the section where the symbol is defined.
   */
  public short stShndx;

  /**
   * Symbol name (as read from the string table).
   */
  public String stNameString;
}
