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
 * The registers for the <code>DebugLineStateMachine</code>.
 */
public class DebugLineRegisters {

  private boolean _defaultIsStatement;

  public long address;
  public int opIndex;
  public int file;
  public long line;
  public long column;
  public boolean isStatement;
  public boolean isBasicBlock;
  public boolean isEndSequence;
  public boolean isPrologueEnd;
  public boolean isEpilogueBegin;
  public long isa;
  public long discriminator;

  /**
   * Create a new instance of the state machine registers.
   * @param defaultIsStatement the default value for the <code>is_stmt</code> register.
   */
  public DebugLineRegisters(boolean defaultIsStatement) {
    _defaultIsStatement = defaultIsStatement;
    reset();
  }

  /**
   * Reset all registers to their default values.
   */
  public void reset() {
    address = 0;
    opIndex = 0;
    file = 1;
    line = 1;
    column = 0;
    isStatement = _defaultIsStatement;
    isBasicBlock = false;
    isEndSequence = false;
    isPrologueEnd = false;
    isEpilogueBegin = false;
    isa = 0;
    discriminator = 0;
  }
}
