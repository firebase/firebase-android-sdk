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
 * Contains data read from the identification section (the first 16 bytes) of an ELF file.
 */
public final class ElfFileIdent {

  /**
   * The length (in bytes) of the ELF header identification data.
   */
  public static final int EI_NIDENT = 16;

  /**
   * Unknown processor architecture.
   *
   * @see ElfFileIdent#getElfClass()
   */
  public static final int ELFCLASSNONE = 0;

  /**
   * 32-bit processor architecture.
   *
   * @see ElfFileIdent#getElfClass()
   */
  public static final int ELFCLASS32 = 1;

  /**
   * 64-bit processor architecture.
   *
   * @see ElfFileIdent#getElfClass()
   */
  public static final int ELFCLASS64 = 2;

  /**
   * Unknown endianness.
   *
   * @see ElfFileIdent#getDataEncoding()
   */
  public static final int ELFDATANONE = 0;

  /**
   * Little-endian.
   *
   * @see ElfFileIdent#getDataEncoding()
   */
  public static final int ELFDATA2LSB = 1;

  /**
   * Big-endian.
   *
   * @see ElfFileIdent#getDataEncoding()
   */
  public static final int ELFDATA2MSB = 2;

  private static final byte[] EI_MAG = {127, 'E', 'L', 'F'};

  private static final int EI_CLASS = 4;
  private static final int EI_DATA = 5;
  private static final int EI_VERSION = 6;
  private static final int EI_OSABI = 7;
  private static final int EI_ABIVERSION = 8;

  private final byte[] _identBuffer;

  public ElfFileIdent(byte[] identBuffer) {
    _identBuffer = identBuffer;
  }

  /**
   * Get the class of this ELF file (32-bit or 64-bit).
   *
   * @return an integer value specifying whether this is a 32-bit or 64-bit ELF file (ELFCLASS constants).
   */
  public int getElfClass() {
    return (int) _identBuffer[EI_CLASS];
  }

  /**
   * Get the data encoding (endianness) of this ELF file.
   *
   * @return an integer value specifying whether this is a big-endian or little-endian ELF file (ELFDATA constants).
   */
  public int getDataEncoding() {
    return (int) _identBuffer[EI_DATA];
  }

  /**
   * Get the ELF version of this file.
   *
   * @return the ELF version of this file.
   */
  public int getElfVersion() {
    return (int) _identBuffer[EI_VERSION];
  }

  /**
   * Get the OS ABI of this ELF file. Is often set to 0 regardless of platform.
   *
   * @return the OS ABI of this ELF file.
   */
  public int getOSABI() {
    return (int) _identBuffer[EI_OSABI];
  }

  /**
   * Further specification of ABI version. Its value depends on the OS ABI.
   *
   * @return
   */
  public int getABIVersion() {
    return (int) _identBuffer[EI_ABIVERSION];
  }

  /**
   * Determine whether this is a valid ELF file.
   *
   * @return <code>true</code> if this is an identifier for a valid ELF file, <code>false otherwise</code>.
   */
  public boolean isElf() {
    for (int i = 0; i < EI_MAG.length; ++i) {
      if (EI_MAG[i] != _identBuffer[i]) {
        return false;
      }
    }
    return true;
  }
}
