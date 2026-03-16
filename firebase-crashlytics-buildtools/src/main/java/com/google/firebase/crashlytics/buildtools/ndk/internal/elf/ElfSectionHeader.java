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
 * Contains data read from a single section header within the section header table of an ELF file.
 * Fields and constants taken from the Linux C header <code>elf.h</code>, <code>typedef elf32_shdr</code>.
 */
public class ElfSectionHeader {

  /**
   * Section type: No associated section (inactive entry).
   */
  public static final int SHT_NULL = 0;

  /**
   * Section type: Program-defined contents.
   */
  public static final int SHT_PROGBITS = 1;

  /**
   * Section type: Symbol table.
   */
  public static final int SHT_SYMTAB = 2;

  /**
   * Section type: String table.
   */
  public static final int SHT_STRTAB = 3;

  /**
   * Section type: Relocation entries; explicit addends.
   */
  public static final int SHT_RELA = 4;

  /**
   * Section type: Symbol hash table.
   */
  public static final int SHT_HASH = 5;

  /**
   * Section type: Information for dynamic linking.
   */
  public static final int SHT_DYNAMIC = 6;

  /**
   * Section type: Information about the file.
   */
  public static final int SHT_NOTE = 7;

  /**
   * Section type: Data occupies no space in the file.
   */
  public static final int SHT_NOBITS = 8;

  /**
   * Section type: Relocation entries; no explicit addends.
   */
  public static final int SHT_REL = 9;

  /**
   * Section type: Reserved.
   */
  public static final int SHT_SHLIB = 10;

  /**
   * Section type: Symbol table.
   */
  public static final int SHT_DYNSYM = 11;

  /**
   * Offset in the string table of the section header name.
   */
  public int shName;

  /**
   * Section type (SHT constants).
   */
  public int shType;

  /**
   * Section flags.
   */
  public long shFlags;

  /**
   * Address of the section if the section is to be loaded into memory.
   */
  public long shAddr;

  /**
   * File offset to the raw data of the section.
   */
  public long shOffset;

  /**
   * Size of the section.
   */
  public long shSize;

  /**
   * Link to the index of another section header. If type is SHT_SYMTAB,
   * this is the index of the section header for the symbol table.
   */
  public int shLink;

  public int shInfo;

  /**
   * Alignment requirement of the section.
   */
  public long shAddrAlign;

  /**
   * Size for each entry in sections that contains fixed-sized entries, such as symbol tables.
   */
  public long shEntSize;

  /**
   * Section header name (as read from the string table).
   */
  public String shNameString;
}
