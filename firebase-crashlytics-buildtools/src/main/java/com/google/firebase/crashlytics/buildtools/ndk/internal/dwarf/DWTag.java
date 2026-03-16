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

public enum DWTag {
  ARRAY_TYPE(0x01, "array_type"),
  CLASS_TYPE(0x02, "class_type"),
  ENTRY_POINT(0x03, "entry_point"),
  ENUMERATION_TYPE(0x04, "enumeration_type"),
  FORMAL_PARAMETER(0x05, "formal_parameter"),
  IMPORTED_DECLARATION(0x08, "imported_declaration"),
  LABEL(0x0a, "label"),
  LEXICAL_BLOCK(0x0b, "lexical_block"),
  MEMBER(0x0d, "member"),
  POINTER_TYPE(0x0f, "pointer_type"),
  REFERENCE_TYPE(0x10, "reference_type"),
  COMPILE_UNIT(0x11, "compile_unit"),
  STRING_TYPE(0x12, "string_type"),
  STRUCTURE_TYPE(0x13, "structure_type"),
  SUBROUTINE_TYPE(0x15, "subroutine_type"),
  TYPEDEF(0x16, "typedef"),
  UNION_TYPE(0x17, "union_type"),
  UNSPECIFIED_PARAMETERS(0x18, "unspecified_parameters"),
  VARIANT(0x19, "variant"),
  COMMON_BLOCK(0x1a, "common_block"),
  COMMON_INCLUSION(0x1b, "common_inclusion"),
  INHERITANCE(0x1c, "inheritance"),
  INLINED_SUBROUTINE(0x1d, "inlined_subroutine"),
  MODULE(0x1e, "module"),
  PTR_TO_MEMBER_TYPE(0x1f, "ptr_to_member_type"),
  SET_TYPE(0x20, "set_type"),
  SUBRANGE_TYPE(0x21, "subrange_type"),
  WITH_STMT(0x22, "with_stmt"),
  ACCESS_DECLARATION(0x23, "access_declaration"),
  BASE_TYPE(0x24, "base_type"),
  CATCH_BLOCK(0x25, "catch_block"),
  CONST_TYPE(0x26, "const_type"),
  CONSTANT(0x27, "constant"),
  ENUMERATOR(0x28, "enumerator"),
  FILE_TYPE(0x29, "file_type"),
  FRIEND(0x2a, "friend"),
  NAMELIST(0x2b, "namelist"),
  NAMELIST_ITEM(0x2c, "namelist_item"),
  PACKED_TYPE(0x2d, "packed_type"),
  SUBPROGRAM(0x2e, "subprogram"),
  TEMPLATE_TYPE_PARAMETER(0x2f, "template_type_parameter"),
  TEMPLATE_VALUE_PARAMETER(0x30, "template_value_parameter"),
  THROWN_TYPE(0x31, "thrown_type"),
  TRY_BLOCK(0x32, "try_block"),
  VARIANT_PART(0x33, "variant_part"),
  VARIABLE(0x34, "variable"),
  VOLATILE_TYPE(0x35, "volatile_type"),
  DWARF_PROCEDURE(0x36, "dwarf_procedure"),
  RESTRICT_TYPE(0x37, "restrict_type"),
  INTERFACE_TYPE(0x38, "interface_type"),
  NAMESPACE(0x39, "namespace"),
  IMPORTED_MODULE(0x3a, "imported_module"),
  UNSPECIFIED_TYPE(0x3b, "unspecified_type"),
  PARTIAL_UNIT(0x3c, "partial_unit"),
  IMPORTED_UNIT(0x3d, "imported_unit"),
  CONDITION(0x3f, "condition"),
  SHARED_TYPE(0x40, "shared_type"),
  TYPE_UNIT(0x41, "type_unit"),
  RVALUE_REFERENCE_TYPE(0x42, "rvalue_reference_type"),
  TEMPLATE_ALIAS(0x43, "template_alias"),
  COARRAY_TYPE(0x44, "coarray_type"),
  GENERIC_SUBRANGE(0x45, "generic_subrange"),
  DYNAMIC_TYPE(0x46, "dynamic_type"),
  ATOMIC_TYPE(0x47, "atomic_type"),
  CALL_SITE(0x48, "call_site"),
  CALL_SITE_PARAMETER(0x49, "call_site_parameter"),
  SKELETON_UNIT(0x4a, "skeleton_unit"),
  IMMUTABLE_TYPE(0x4b, "immutable_type"),
  LO_USER(0x4080, "lo_user"),

  // Vendor-specific extensions

  // MIPS
  MIPS_LOOP(0x4081, "MIPS_loop"),

  // GNU
  FORMAT_LABEL(0x4101, "format_label"),
  FUNCTION_TEMPLATE(0x4102, "function_template"),
  CLASS_TEMPLATE(0x4103, "class_template"),
  GNU_TEMPLATE_TEMPLATE_PARAM(0x4106, "GNU_template_template_param"),
  GNU_TEMPLATE_PARAMETER_PACK(0x4107, "GNU_template_parameter_pack"),
  GNU_FORMAL_PARAMETER_PACK(0x4108, "GNU_formal_parameter_pack"),
  GNU_CALL_SITE(0x4109, "GNU_call_site"),
  GNU_CALL_SITE_PARAMETER(0x410a, "GNU_call_site_parameter"),

  // Apple
  APPLE_PROPERTY(0x4200, "APPLE_property"),

  // Borland
  BORLAND_PROPERTY(0xb000, "BORLAND_property"),
  BORLAND_DELPHI_STRING(0xb001, "BORLAND_Delphi_string"),
  BORLAND_DELPHI_DYNAMIC_ARRAY(0xb002, "BORLAND_Delphi_dynamic_array"),
  BORLAND_DELPHI_SET(0xb003, "BORLAND_Delphi_set"),
  BORLAND_DELPHI_VARIANT(0xb004, "BORLAND_Delphi_variant"),

  HI_USER(0xffff, "hi_user");

  private static final String PREFIX = "DW_TAG_";
  private static final Map<Integer, DWTag> LOOKUP = new HashMap<Integer, DWTag>();

  static {
    for (DWTag t : DWTag.values()) {
      LOOKUP.put(t._value, t);
    }
  }

  private final int _value;
  private final String _name;
  private final String _fullName;

  DWTag(int value, String name) {
    this._value = value;
    this._name = name;
    this._fullName = PREFIX + _name;
  }

  @Override
  public String toString() {
    return _fullName;
  }

  public static DWTag fromValue(int value) {
    return LOOKUP.get(value);
  }
}
