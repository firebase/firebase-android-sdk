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

import java.util.HashMap;
import java.util.Map;

public enum DWForm {
  ADDR(0x01, "addr"),
  BLOCK2(0x03, "block2"),
  BLOCK4(0x04, "block4"),
  DATA2(0x05, "data2"),
  DATA4(0x06, "data4"),
  DATA8(0x07, "data8"),
  STRING(0x08, "string"),
  BLOCK(0x09, "block"),
  BLOCK1(0x0a, "block1"),
  DATA1(0x0b, "data1"),
  FLAG(0x0c, "flag"),
  SDATA(0x0d, "sdata"),
  STRP(0x0e, "strp"),
  UDATA(0x0f, "udata"),
  REF_ADDR(0x10, "ref_addr"),
  REF1(0x11, "ref1"),
  REF2(0x12, "ref2"),
  REF4(0x13, "ref4"),
  REF8(0x14, "ref8"),
  REF_UDATA(0x15, "ref_udata"),
  INDIRECT(0x16, "indirect"),
  SEC_OFFSET(0x17, "sec_offset"),
  EXPRLOC(0x18, "exprloc"),
  FLAG_PRESENT(0x19, "flag_present"),
  STRX(0x1a, "strx"),
  ADDRX(0x1b, "addrx"),
  REF_SUP4(0x1c, "ref_sup4"),
  STRP_SUP(0x1d, "strp_sup"),
  DATA16(0x1e, "data16"),
  LINE_STRP(0x1f, "line_strp"),
  REF_SIG8(0x20, "ref_sig8"),
  IMPLICIT_CONST(0x21, "implicit_const"),
  LOCLISTX(0x22, "loclistx"),
  RNGLISTX(0x23, "rnglistx"),
  REF_SUP8(0x24, "ref_sup8"),
  STRX1(0x25, "strx1"),
  STRX2(0x26, "strx2"),
  STRX3(0x27, "strx3"),
  STRX4(0x28, "strx4"),
  ADDRX1(0x29, "addrx1"),
  ADDRX2(0x2a, "addrx2"),
  ADDRX3(0x2b, "addrx3"),
  ADDRX4(0x2c, "addrx4");

  private static final String PREFIX = "DW_FORM_";
  private static final Map<Integer, DWForm> LOOKUP = new HashMap<Integer, DWForm>();

  static {
    for (DWForm f : DWForm.values()) {
      LOOKUP.put(f._value, f);
    }
  }

  private final int _value;
  private final String _name;
  private final String _fullName;

  DWForm(int value, String name) {
    this._value = value;
    this._name = name;
    this._fullName = PREFIX + _name;
  }

  @Override
  public String toString() {
    return _fullName;
  }

  public static DWForm fromValue(int value) {
    return LOOKUP.get(value);
  }
}
