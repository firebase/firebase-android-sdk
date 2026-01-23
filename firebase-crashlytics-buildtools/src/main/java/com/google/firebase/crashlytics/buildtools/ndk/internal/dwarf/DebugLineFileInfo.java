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
 * File information read from either a compilation unit header
 * or the DW_LNE_define_file instruction of the state machine.
 */
public class DebugLineFileInfo {
  /**
   * The name of the file.
   */
  public final String name;

  /**
   * The directory from which the file was compiled.
   */
  public final String directory;

  /**
   * The last modification time of the file.
   */
  public final int modificationTime;

  /**
   * The length of the file.
   */
  public final int length;

  public DebugLineFileInfo(String name, String directory, int modificationTime, int length) {
    this.name = name;
    this.directory = directory;
    this.modificationTime = modificationTime;
    this.length = length;
  }
}
