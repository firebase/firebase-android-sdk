LOCAL_PATH := $(call my-dir)

THIRD_PARTY_PATH := ../../../../third_party

include $(CLEAR_VARS)

LOCAL_MODULE := crashpad_minidump
LOCAL_C_INCLUDES := $(LOCAL_PATH)/$(THIRD_PARTY_PATH)/crashpad
LOCAL_CPPFLAGS := \
    -D_FILE_OFFSET_BITS=64 \
    -std=c++17 \
    -Wall \
    -Os \
    -flto \
    -fvisibility=hidden \

LOCAL_SRC_FILES := \
    $(THIRD_PARTY_PATH)/crashpad/minidump/minidump_annotation_writer.cc \
    $(THIRD_PARTY_PATH)/crashpad/minidump/minidump_byte_array_writer.cc \
    $(THIRD_PARTY_PATH)/crashpad/minidump/minidump_context_writer.cc \
    $(THIRD_PARTY_PATH)/crashpad/minidump/minidump_crashpad_info_writer.cc \
    $(THIRD_PARTY_PATH)/crashpad/minidump/minidump_exception_writer.cc \
    $(THIRD_PARTY_PATH)/crashpad/minidump/minidump_extensions.cc \
    $(THIRD_PARTY_PATH)/crashpad/minidump/minidump_file_writer.cc \
    $(THIRD_PARTY_PATH)/crashpad/minidump/minidump_handle_writer.cc \
    $(THIRD_PARTY_PATH)/crashpad/minidump/minidump_memory_info_writer.cc \
    $(THIRD_PARTY_PATH)/crashpad/minidump/minidump_memory_writer.cc \
    $(THIRD_PARTY_PATH)/crashpad/minidump/minidump_misc_info_writer.cc \
    $(THIRD_PARTY_PATH)/crashpad/minidump/minidump_module_crashpad_info_writer.cc \
    $(THIRD_PARTY_PATH)/crashpad/minidump/minidump_module_writer.cc \
    $(THIRD_PARTY_PATH)/crashpad/minidump/minidump_rva_list_writer.cc \
    $(THIRD_PARTY_PATH)/crashpad/minidump/minidump_simple_string_dictionary_writer.cc \
    $(THIRD_PARTY_PATH)/crashpad/minidump/minidump_stream_writer.cc \
    $(THIRD_PARTY_PATH)/crashpad/minidump/minidump_string_writer.cc \
    $(THIRD_PARTY_PATH)/crashpad/minidump/minidump_system_info_writer.cc \
    $(THIRD_PARTY_PATH)/crashpad/minidump/minidump_thread_id_map.cc \
    $(THIRD_PARTY_PATH)/crashpad/minidump/minidump_thread_writer.cc \
    $(THIRD_PARTY_PATH)/crashpad/minidump/minidump_unloaded_module_writer.cc \
    $(THIRD_PARTY_PATH)/crashpad/minidump/minidump_user_extension_stream_data_source.cc \
    $(THIRD_PARTY_PATH)/crashpad/minidump/minidump_user_stream_writer.cc \
    $(THIRD_PARTY_PATH)/crashpad/minidump/minidump_writable.cc \
    $(THIRD_PARTY_PATH)/crashpad/minidump/minidump_writer_util.cc \

LOCAL_STATIC_LIBRARIES := crashpad_compat crashpad_util mini_chromium_base

include $(BUILD_STATIC_LIBRARY)
