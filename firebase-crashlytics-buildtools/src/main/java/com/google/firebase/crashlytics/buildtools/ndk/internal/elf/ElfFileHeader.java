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
 * Contains data read from the file header of an ELF file.
 */
public final class ElfFileHeader {

  private ElfFileIdent _ident;

  /**
   * The file type: relocatable: 1, executable: 2
   */
  public int eType;

  /**
   * Target architecture.
   *
   * @see EMachine
   */
  public int eMachine;

  /**
   * Object file version. Current ELF version is 1.
   */
  public long eVersion;

  /**
   * Program entry address.
   */
  public long eEntry;

  /**
   * Program header table offset.
   */
  public long ePhoff;

  /**
   * Section header table offset.
   */
  public long eShoff;

  /**
   * Reserved for future use.
   */
  public long eFlags;

  /**
   * Size of the ELF header.
   */
  public int eEhsize;

  /**
   * Size of each entry in program header table.
   */
  public int ePhentsize;

  /**
   * Number of entries in the program header table.
   */
  public int ePhnum;

  /**
   * Size of each entry in the section header table.
   */
  public int eShentsize;

  /**
   * Number of entries in the section header table.
   */
  public int eShnum;

  /**
   * Index of the section header referencing the string table for the section names.
   */
  public int eShstrndx;

  public ElfFileHeader(ElfFileIdent fileIdent) {
    _ident = fileIdent;
  }

  public ElfFileIdent getElfFileIdent() {
    return _ident;
  }
}
