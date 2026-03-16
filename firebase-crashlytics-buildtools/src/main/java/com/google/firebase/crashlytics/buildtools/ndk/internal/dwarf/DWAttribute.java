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

public enum DWAttribute {
  // DWARF 2
  SIBLING(0x0001, "sibling"),
  LOCATION(0x0002, "location"),
  NAME(0x0003, "name"),
  ORDERING(0x0009, "ordering"),
  SUBSCR_DATA(0x000a, "subscr_data"),
  BYTE_SIZE(0x000b, "byte_size"),
  BIT_OFFSET(0x000c, "bit_offset"),
  BIT_SIZE(0x000d, "bit_size"),
  ELEMENT_LIST(0x000f, "element_list"),
  STMT_LIST(0x0010, "stmt_list"),
  LOW_PC(0x0011, "low_pc"),
  HIGH_PC(0x0012, "high_pc"),
  LANGUAGE(0x0013, "language"),
  MEMBER(0x0014, "member"),
  DISCR(0x0015, "discr"),
  DISCR_VALUE(0x0016, "discr_value"),
  VISIBILITY(0x0017, "visibility"),
  IMPORT(0x0018, "import"),
  STRING_LENGTH(0x0019, "string_length"),
  COMMON_REFERENCE(0x001a, "common_reference"),
  COMP_DIR(0x001b, "comp_dir"),
  CONST_VALUE(0x001c, "const_value"),
  CONTAINING_TYPE(0x001d, "containing_type"),
  DEFAULT_VALUE(0x001e, "default_value"),
  INLINE(0x0020, "inline"),
  IS_OPTIONAL(0x0021, "is_optional"),
  LOWER_BOUND(0x0022, "lower_bound"),
  PRODUCER(0x0025, "producer"),
  PROTOTYPED(0x0027, "prototyped"),
  RETURN_ADDR(0x002a, "return_addr"),
  START_SCOPE(0x002c, "start_scope"),
  BIT_STRIDE(0x002e, "bit_stride"),
  UPPER_BOUND(0x002f, "upper_bound"),
  ABSTRACT_ORIGIN(0x0031, "abstract_origin"),
  ACCESSIBILITY(0x0032, "accessibility"),
  ADDRESS_CLASS(0x0033, "address_class"),
  ARTIFICIAL(0x0034, "artificial"),
  BASE_TYPES(0x0035, "base_types"),
  CALLING_CONVENTION(0x0036, "calling_convention"),
  COUNT(0x0037, "count"),
  DATA_MEMBER_LOCATION(0x0038, "data_member_location"),
  DECL_COLUMN(0x0039, "decl_column"),
  DECL_FILE(0x003a, "decl_file"),
  DECL_LINE(0x003b, "decl_line"),
  DECLARATION(0x003c, "declaration"),
  DISCR_LIST(0x003d, "discr_list"),
  ENCODING(0x003e, "encoding"),
  EXTERNAL(0x003f, "external"),
  FRAME_BASE(0x0040, "frame_base"),
  FRIEND(0x0041, "friend"),
  IDENTIFIER_CASE(0x0042, "identifier_case"),
  MACRO_INFO(0x0043, "macro_info"),
  NAMELIST_ITEM(0x0044, "namelist_item"),
  PRIORITY(0x0045, "priority"),
  SEGMENT(0x0046, "segment"),
  SPECIFICATION(0x0047, "specification"),
  STATIC_LINK(0x0048, "static_link"),
  TYPE(0x0049, "type"),
  USE_LOCATION(0x004a, "use_location"),
  VARIABLE_PARAMETER(0x004b, "variable_parameter"),
  VIRTUALITY(0x004c, "virtuality"),
  VTABLE_ELEM_LOCATION(0x004d, "vtable_elem_location"),

  // DWARF 3
  ALLOCATED(0x004e, "allocated"),
  ASSOCIATED(0x004f, "associated"),
  DATA_LOCATION(0x0050, "data_location"),
  BYTE_STRIDE(0x0051, "byte_stride"),
  ENTRY_PC(0x0052, "entry_pc"),
  USE_UTF8(0x0053, "use_UTF8"),
  EXTENSION(0x0054, "extension"),
  RANGES(0x0055, "ranges"),
  TRAMPOLINE(0x0056, "trampoline"),
  CALL_COLUMN(0x0057, "call_column"),
  CALL_FILE(0x0058, "call_file"),
  CALL_LINE(0x0059, "call_line"),
  DESCRIPTION(0x005a, "description"),
  BINARY_SCALE(0x005b, "binary_scale"),
  DECIMAL_SCALE(0x005c, "decimal_scale"),
  SMALL(0x005d, "small"),
  DECIMAL_SIGN(0x005e, "decimal_sign"),
  DIGIT_COUNT(0x005f, "digit_count"),
  PICTURE_STRING(0x0060, "picture_string"),
  MUTABLE(0x0061, "mutable"),
  THREADS_SCALED(0x0062, "threads_scaled"),
  EXPLICIT(0x0063, "explicit"),
  OBJECT_POINTER(0x0064, "object_pointer"),
  ENDIANITY(0x0065, "endianity"),
  ELEMENTAL(0x0066, "elemental"),
  PURE(0x0067, "pure"),
  RECURSIVE(0x0068, "recursive"),

  // DWARF 4
  SIGNATURE(0x0069, "signature"),
  MAIN_SUBPROGRAM(0x006a, "main_subprogram"),
  DATA_BIT_OFFSET(0x006b, "data_bit_offset"),
  CONST_EXPR(0x006c, "const_expr"),
  ENUM_CLASS(0x006d, "enum_class"),
  LINKAGE_NAME(0x006e, "linkage_name"),

  // DWARF 5
  STRING_LENGTH_BIT_SIZE(0x6f, "string_length_bit_size"),
  STRING_LENGTH_BYTE_SIZE(0x70, "string_length_byte_size"),
  RANK(0x71, "rank"),
  STR_OFFSETS_BASE(0x72, "str_offsets_base"),
  ADDR_BASE(0x73, "addr_base"),
  RNGLISTS_BASE(0x74, "rnglists_base"),
  RESERVED(0x75, "Reserved"),
  DWO_NAME(0x76, "dwo_name"),
  REFERENCE(0x77, "reference"),
  RVALUE_REFERENCE(0x78, "rvalue_reference"),
  MACROS(0x79, "macros"),
  CALL_ALL_CALLS(0x7a, "call_all_calls"),
  CALL_ALL_SOURCE_CALLS(0x7b, "call_all_source_calls"),
  CALL_ALL_TAIL_CALLS(0x7c, "call_all_tail_calls"),
  CALL_RETURN_PC(0x7d, "call_return_pc"),
  CALL_VALUE(0x7e, "call_value"),
  CALL_ORIGIN(0x7f, "call_origin"),
  CALL_PARAMETER(0x80, "call_parameter"),
  CALL_PC(0x81, "call_pc"),
  CALL_TAIL_CALL(0x82, "call_tail_call"),
  CALL_TARGET(0x83, "call_target"),
  CALL_TARGET_CLOBBERED(0x84, "call_target_clobbered"),
  CALL_DATA_LOCATION(0x85, "call_data_location"),
  CALL_DATA_VALUE(0x86, "call_data_value"),
  NORETURN(0x87, "noreturn"),
  ALIGNMENT(0x88, "alignment"),
  EXPORT_SYMBOLS(0x89, "export_symbols"),
  DELETED(0x8a, "deleted"),
  DEFAULTED(0x8b, "defaulted"),
  LOCLISTS_BASE(0x8c, "loclists_base"),
  LO_USER(0x2000, "lo_user"),

  // Vendor-specific extensions

  // MIPS
  MIPS_FDE(0x2001, "MIPS_fde"),
  MIPS_LOOP_BEGIN(0x2002, "MIPS_loop_begin"),
  MIPS_TAIL_LOOP_BEGIN(0x2003, "MIPS_tail_loop_begin"),
  MIPS_EPILOG_BEGIN(0x2004, "MIPS_epilog_begin"),
  MIPS_LOOP_UNROLL_FACTOR(0x2005, "MIPS_loop_unroll_factor"),
  MIPS_SOFTWARE_PIPELINE_DEPTH(0x2006, "MIPS_software_pipeline_depth"),
  MIPS_LINKAGE_NAME(0x2007, "MIPS_linkage_name"),
  MIPS_STRIDE(0x2008, "MIPS_stride"),
  MIPS_ABSTRACT_NAME(0x2009, "MIPS_abstract_name"),
  MIPS_CLONE_ORIGIN(0x200a, "MIPS_clone_origin"),
  MIPS_HAS_INLINES(0x200b, "MIPS_has_inlines"),
  MIPS_STRIDE_BYTE(0x200c, "MIPS_stride_byte"),
  MIPS_STRIDE_ELEM(0x200d, "MIPS_stride_elem"),
  MIPS_PTR_DOPETYPE(0x200e, "MIPS_ptr_dopetype"),
  MIPS_ALLOCATABLE_DOPETYPE(0x200f, "MIPS_allocatable_dopetype"),
  MIPS_ASSUMED_SHAPE_DOPETYPE(0x2010, "MIPS_assumed_shape_dopetype"),

  // HP
  HP_ACTUALS_STMT_LIST(0x2010, "HP_actuals_stmt_list"),
  HP_PROC_PER_SECTION(0x2011, "HP_proc_per_section"),
  HP_RAW_DATA_PTR(0x2012, "HP_raw_data_ptr"),
  HP_PASS_BY_REFERENCE(0x2013, "HP_pass_by_reference"),
  HP_OPT_LEVEL(0x2014, "HP_opt_level"),
  HP_PROF_VERSION_ID(0x2015, "HP_prof_version_id"),
  HP_OPT_FLAGS(0x2016, "HP_opt_flags"),
  HP_COLD_REGION_LOW_PC(0x2017, "HP_cold_region_low_pc"),
  HP_COLD_REGION_HIGH_PC(0x2018, "HP_cold_region_high_pc"),
  HP_ALL_VARIABLES_MODIFIABLE(0x2019, "HP_all_variables_modifiable"),
  HP_LINKAGE_NAME(0x201a, "HP_linkage_name"),
  HP_PROF_FLAGS(0x201b, "HP_prof_flags"),
  HP_UNIT_NAME(0x201f, "HP_unit_name"),
  HP_UNIT_SIZE(0x2020, "HP_unit_size"),
  HP_WIDENED_BYTE_SIZE(0x2021, "HP_widened_byte_size"),
  HP_DEFINITION_POINTS(0x2022, "HP_definition_points"),
  HP_DEFAULT_LOCATION(0x2023, "HP_default_location"),
  HP_IS_RESULT_PARAM(0x2029, "HP_is_result_param"),

  // Intel
  INTEL_OTHER_ENDIAN(0x2026, "INTEL_other_endian"),

  // GNU
  SF_NAMES(0x2101, "sf_names"),
  SRC_INFO(0x2102, "src_info"),
  MAC_INFO(0x2103, "mac_info"),
  SRC_COORDS(0x2104, "src_coords"),
  BODY_BEGIN(0x2105, "body_begin"),
  BODY_END(0x2106, "body_end"),
  GNU_VECTOR(0x2107, "GNU_vector"),
  GNU_GUARDED_BY(0x2108, "GNU_guarded_by"),
  GNU_PT_GUARDED_BY(0x2109, "GNU_pt_guarded_by"),
  GNU_GUARDED(0x210a, "GNU_guarded"),
  GNU_PT_GUARDED(0x210b, "GNU_pt_guarded"),
  GNU_LOCKS_EXCLUDED(0x210c, "GNU_locks_excluded"),
  GNU_EXCLUSIVE_LOCKS_REQUIRED(0x210d, "GNU_exclusive_locks_required"),
  GNU_SHARED_LOCKS_REQUIRED(0x210e, "GNU_shared_locks_required"),
  GNU_ODR_SIGNATURE(0x210f, "GNU_odr_signature"),
  GNU_TEMPLATE_NAME(0x2110, "GNU_template_name"),
  GNU_CALL_SITE_VALUE(0x2111, "GNU_call_site_value"),
  GNU_CALL_SITE_DATA_VALUE(0x2112, "GNU_call_site_data_value"),
  GNU_CALL_SITE_TARGET(0x2113, "GNU_call_site_target"),
  GNU_CALL_SITE_TARGET_CLOBBERED(0x2114, "GNU_call_site_target_clobbered"),
  GNU_TAIL_CALL(0x2115, "GNU_tail_call"),
  GNU_ALL_TAIL_CALL_SITES(0x2116, "GNU_all_tail_call_sites"),
  GNU_ALL_CALL_SITES(0x2117, "GNU_all_call_sites"),
  GNU_ALL_SOURCE_CALL_SITES(0x2118, "GNU_all_source_call_sites"),
  GNU_MACROS(0x2119, "GNU_macros"),
  GNU_DELETED(0x211a, "GNU_deleted"),
  GNU_DWO_NAME(0x2130, "GNU_dwo_name"),
  GNU_DWO_ID(0x2131, "GNU_dwo_id"),
  GNU_RANGES_BASE(0x2132, "GNU_ranges_base"),
  GNU_ADDR_BASE(0x2133, "GNU_addr_base"),
  GNU_PUBNAMES(0x2134, "GNU_pubnames"),
  GNU_PUBTYPES(0x2135, "GNU_pubtypes"),
  GNU_DISCRIMINATOR(0x2136, "GNU_discriminator"),
  GNU_LOCVIEWS(0x2137, "GNU_locviews"),
  GNU_ENTRY_VIEW(0x2138, "GNU_entry_view"),

  // VMS
  VMS_RTNBEG_PD_ADDRESS(0x2201, "VMS_rtnbeg_pd_address"),

  // GNAT
  USE_GNAT_DESCRIPTIVE_TYPE(0x2301, "use_GNAT_descriptive_type"),
  GNAT_DESCRIPTIVE_TYPE(0x2302, "GNAT_descriptive_type"),

  // UPC
  UPC_THREADS_SCALED(0x3210, "upc_threads_scaled"),

  // PGI
  PGI_LBASE(0x3a00, "PGI_lbase"),
  PGI_SOFFSET(0x3a01, "PGI_soffset"),
  PGI_LSTRIDE(0x3a02, "PGI_lstride"),

  // Apple
  APPLE_OPTIMIZED(0x3fe1, "APPLE_optimized"),
  APPLE_FLAGS(0x3fe2, "APPLE_flags"),
  APPLE_ISA(0x3fe3, "APPLE_isa"),
  APPLE_BLOCK(0x3fe4, "APPLE_block"),
  APPLE_MAJOR_RUNTIME_VERS(0x3fe5, "APPLE_major_runtime_vers"),
  APPLE_RUNTIME_CLASS(0x3fe6, "APPLE_runtime_class"),
  APPLE_OMIT_FRAME_PTR(0x3fe7, "APPLE_omit_frame_ptr"),
  APPLE_PROPERTY_NAME(0x3fe8, "APPLE_property_name"),
  APPLE_PROPERTY_GETTER(0x3fe9, "APPLE_property_getter"),
  APPLE_PROPERTY_SETTER(0x3fea, "APPLE_property_setter"),
  APPLE_PROPERTY_ATTRIBUTE(0x3feb, "APPLE_property_attribute"),
  APPLE_OBJC_COMPLETE_TYPE(0x3fec, "APPLE_objc_complete_type"),
  APPLE_PROPERTY(0x3fed, "APPLE_property"),

  HI_USER(0x3fff, "hi_user");

  private static final String PREFIX = "DW_AT_";
  private static final Map<Integer, DWAttribute> LOOKUP = new HashMap<Integer, DWAttribute>();

  static {
    for (DWAttribute a : DWAttribute.values()) {
      LOOKUP.put(a._value, a);
    }
  }

  private final int _value;
  private final String _name;
  private final String _fullName;

  DWAttribute(int value, String name) {
    this._value = value;
    this._name = name;
    this._fullName = PREFIX + _name;
  }

  @Override
  public String toString() {
    return _fullName;
  }

  public static DWAttribute fromValue(int value) {
    return LOOKUP.get(value);
  }
}
