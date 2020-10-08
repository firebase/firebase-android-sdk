LOCAL_PATH := $(call my-dir)

THIRD_PARTY_PATH := ../../../../third_party

include $(CLEAR_VARS)

LOCAL_MODULE := crashpad_util
LOCAL_C_INCLUDES := \
    $(LOCAL_PATH)/$(THIRD_PARTY_PATH)/.. \
    $(LOCAL_PATH)/$(THIRD_PARTY_PATH)/crashpad \

LOCAL_CPPFLAGS := \
    -D_FILE_OFFSET_BITS=64 \
    -DZLIB_CONST \
    -DCRASHPAD_ZLIB_SOURCE_SYSTEM \
    -DCRASHPAD_LSS_SOURCE_EXTERNAL \
    -std=c++17 \
    -Wall \
    -Os \
    -flto \
    -fvisibility=hidden \

LOCAL_SRC_FILES := \
    $(THIRD_PARTY_PATH)/crashpad/util/file/delimited_file_reader.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/file/directory_reader_posix.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/file/file_helper.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/file/file_io.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/file/file_io_posix.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/file/file_reader.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/file/file_seeker.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/file/filesystem_posix.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/file/file_writer.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/file/output_stream_file_writer.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/file/scoped_remove_file.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/file/string_file.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/linux/auxiliary_vector.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/linux/direct_ptrace_connection.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/linux/exception_handler_client.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/linux/exception_handler_protocol.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/linux/initial_signal_dispositions.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/linux/memory_map.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/linux/proc_stat_reader.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/linux/proc_task_reader.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/linux/ptrace_broker.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/linux/ptrace_client.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/linux/ptracer.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/linux/scoped_pr_set_dumpable.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/linux/scoped_pr_set_ptracer.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/linux/scoped_ptrace_attach.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/linux/socket.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/linux/thread_info.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/misc/capture_context_linux.S \
    $(THIRD_PARTY_PATH)/crashpad/util/misc/clock_posix.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/misc/initialization_state_dcheck.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/misc/lexing.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/misc/metrics.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/misc/paths_linux.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/misc/pdb_structures.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/misc/random_string.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/misc/range_set.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/misc/reinterpret_bytes.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/misc/scoped_forbid_return.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/misc/time.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/misc/time_linux.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/misc/uuid.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/misc/zlib.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/net/http_body.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/net/http_body_gzip.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/net/http_multipart_builder.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/net/http_transport.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/net/url.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/numeric/checked_address_range.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/posix/close_multiple.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/posix/close_stdio.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/posix/drop_privileges.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/posix/double_fork_and_exec.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/posix/process_info_linux.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/posix/scoped_dir.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/posix/scoped_mmap.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/posix/signals.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/posix/symbolic_constants_posix.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/process/process_memory.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/process/process_memory_linux.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/process/process_memory_range.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/stdlib/aligned_allocator.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/stdlib/string_number_conversion.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/stdlib/strlcpy.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/stdlib/strnlen.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/stream/base94_output_stream.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/stream/file_encoder.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/stream/file_output_stream.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/stream/log_output_stream.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/stream/zlib_output_stream.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/string/split_string.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/synchronization/semaphore_posix.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/thread/thread.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/thread/thread_log_messages.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/thread/thread_posix.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/thread/worker_thread.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/net/http_transport_socket.cc \
    $(THIRD_PARTY_PATH)/crashpad/util/process/process_memory_sanitized.cc \

LOCAL_STATIC_LIBRARIES := mini_chromium_base

include $(BUILD_STATIC_LIBRARY)
