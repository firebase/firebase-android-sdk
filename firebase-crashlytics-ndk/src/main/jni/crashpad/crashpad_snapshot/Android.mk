LOCAL_PATH := $(call my-dir)

THIRD_PARTY_PATH := ../../../../third_party

include $(CLEAR_VARS)

LOCAL_MODULE := crashpad_snapshot
LOCAL_C_INCLUDES := $(LOCAL_PATH)/$(THIRD_PARTY_PATH)/crashpad
LOCAL_CPPFLAGS := \
    -D_FILE_OFFSET_BITS=64 \
    -std=c++17 \
    -Wall \
    -Os \
    -flto \
    -fvisibility=hidden \

LOCAL_SRC_FILES := \
    $(THIRD_PARTY_PATH)/crashpad/snapshot/annotation_snapshot.cc \
    $(THIRD_PARTY_PATH)/crashpad/snapshot/cpu_context.cc \
    $(THIRD_PARTY_PATH)/crashpad/snapshot/crashpad_info_client_options.cc \
    $(THIRD_PARTY_PATH)/crashpad/snapshot/crashpad_types/crashpad_info_reader.cc \
    $(THIRD_PARTY_PATH)/crashpad/snapshot/crashpad_types/image_annotation_reader.cc \
    $(THIRD_PARTY_PATH)/crashpad/snapshot/elf/elf_dynamic_array_reader.cc \
    $(THIRD_PARTY_PATH)/crashpad/snapshot/elf/elf_image_reader.cc \
    $(THIRD_PARTY_PATH)/crashpad/snapshot/elf/elf_symbol_table_reader.cc \
    $(THIRD_PARTY_PATH)/crashpad/snapshot/elf/module_snapshot_elf.cc \
    $(THIRD_PARTY_PATH)/crashpad/snapshot/handle_snapshot.cc \
    $(THIRD_PARTY_PATH)/crashpad/snapshot/linux/cpu_context_linux.cc \
    $(THIRD_PARTY_PATH)/crashpad/snapshot/linux/debug_rendezvous.cc \
    $(THIRD_PARTY_PATH)/crashpad/snapshot/linux/exception_snapshot_linux.cc \
    $(THIRD_PARTY_PATH)/crashpad/snapshot/linux/process_reader_linux.cc \
    $(THIRD_PARTY_PATH)/crashpad/snapshot/linux/process_snapshot_linux.cc \
    $(THIRD_PARTY_PATH)/crashpad/snapshot/linux/system_snapshot_linux.cc \
    $(THIRD_PARTY_PATH)/crashpad/snapshot/linux/thread_snapshot_linux.cc \
    $(THIRD_PARTY_PATH)/crashpad/snapshot/memory_snapshot.cc \
    $(THIRD_PARTY_PATH)/crashpad/snapshot/minidump/minidump_annotation_reader.cc \
    $(THIRD_PARTY_PATH)/crashpad/snapshot/minidump/minidump_context_converter.cc \
    $(THIRD_PARTY_PATH)/crashpad/snapshot/minidump/minidump_simple_string_dictionary_reader.cc \
    $(THIRD_PARTY_PATH)/crashpad/snapshot/minidump/minidump_string_list_reader.cc \
    $(THIRD_PARTY_PATH)/crashpad/snapshot/minidump/minidump_string_reader.cc \
    $(THIRD_PARTY_PATH)/crashpad/snapshot/minidump/exception_snapshot_minidump.cc \
    $(THIRD_PARTY_PATH)/crashpad/snapshot/minidump/memory_snapshot_minidump.cc \
    $(THIRD_PARTY_PATH)/crashpad/snapshot/minidump/module_snapshot_minidump.cc \
    $(THIRD_PARTY_PATH)/crashpad/snapshot/minidump/process_snapshot_minidump.cc \
    $(THIRD_PARTY_PATH)/crashpad/snapshot/minidump/system_snapshot_minidump.cc \
    $(THIRD_PARTY_PATH)/crashpad/snapshot/minidump/thread_snapshot_minidump.cc \
    $(THIRD_PARTY_PATH)/crashpad/snapshot/posix/timezone.cc \
    $(THIRD_PARTY_PATH)/crashpad/snapshot/sanitized/memory_snapshot_sanitized.cc \
    $(THIRD_PARTY_PATH)/crashpad/snapshot/sanitized/module_snapshot_sanitized.cc \
    $(THIRD_PARTY_PATH)/crashpad/snapshot/sanitized/process_snapshot_sanitized.cc \
    $(THIRD_PARTY_PATH)/crashpad/snapshot/sanitized/sanitization_information.cc \
    $(THIRD_PARTY_PATH)/crashpad/snapshot/sanitized/thread_snapshot_sanitized.cc \
    $(THIRD_PARTY_PATH)/crashpad/snapshot/unloaded_module_snapshot.cc \
    $(THIRD_PARTY_PATH)/crashpad/snapshot/x86/cpuid_reader.cc \

LOCAL_STATIC_LIBRARIES := crashpad_client crashpad_compat

include $(BUILD_STATIC_LIBRARY)
