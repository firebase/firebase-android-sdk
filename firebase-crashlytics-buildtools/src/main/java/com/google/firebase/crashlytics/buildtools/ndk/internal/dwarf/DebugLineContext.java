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

import java.util.ArrayList;
import java.util.List;

/**
 * Maintains the current context and state of the <code>DebugLineStateMachine</code> while it is
 * running.
 */
public class DebugLineContext {

  private final List<String> _directories;
  private final List<DebugLineFileInfo> _files;

  public final DebugLineHeader header;
  public final DebugLineRegisters reg;
  public final int offsetSize;

  public DebugLineContext(
      DebugLineHeader header, DebugLineRegisters initialRegisters, int offsetSize) {
    // Directory 0 is current directory.
    _directories =
        new ArrayList<String>() {
          {
            add("");
          }
        };
    // Files list is 1-based, so add a dummy file info at 0.
    _files =
        new ArrayList<DebugLineFileInfo>() {
          {
            add(new DebugLineFileInfo("", "", 0, 0));
          }
        };

    this.header = header;
    this.reg = initialRegisters;
    this.offsetSize = offsetSize;
  }

  public void defineDirectory(String directory) {
    _directories.add(directory);
  }

  public void defineFile(String fileName, int directoryIndex, int modTime, int length) {
    String directoryName = _directories.get(directoryIndex);
    DebugLineFileInfo fileInfo = new DebugLineFileInfo(fileName, directoryName, modTime, length);
    _files.add(fileInfo);
  }

  public DebugLineFileInfo getFileInfo(int fileIndex) {
    return _files.get(fileIndex);
  }
}
