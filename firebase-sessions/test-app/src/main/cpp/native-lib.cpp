#include <jni.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/mman.h>

#include <android/log.h>
#include <string.h>

void* arr[] = {nullptr, nullptr};

int64_t total_device_memory() {
    long page_size = sysconf(_SC_PAGESIZE);
    long phys_pages = sysconf(_SC_PHYS_PAGES);

    return page_size == -1 || phys_pages == -1 ? -1
        : static_cast<int64_t>(page_size) * static_cast<int64_t>(phys_pages);
}

extern "C" JNIEXPORT void JNICALL
Java_com_google_firebase_testing_sessions_FirstFragment_createNativeLeak(
    JNIEnv* env,
    jobject /* this */) {

    const char* tag = "App";

    __android_log_print(ANDROID_LOG_INFO, tag, "About to cause a leak... [pid=%d]", getpid());

    int64_t total_ram = total_device_memory();
    int64_t tenth = total_ram / 10;

    __android_log_print(ANDROID_LOG_INFO, tag, "  Total RAM: %lld (%lld MiBs)",
                        total_ram, total_ram / 1024 / 1024);

    bool funk = false;

    for (unsigned int i = 0; i < 10 * 2; ++i) {
        void* mem = mmap(nullptr, tenth,
                         PROT_READ | PROT_WRITE,
                         MAP_ANONYMOUS | MAP_PRIVATE, -1, 0);

        if (mem == MAP_FAILED) {
            __android_log_print(ANDROID_LOG_INFO, tag, "    Failed to allocate tenth [%u]", i);
            break;
        }

        memset(mem, i, tenth);
        arr[static_cast<unsigned int>(funk)] = mem;
        funk = !funk;

        __android_log_print(ANDROID_LOG_INFO, tag, "    Allocated 1/10th [%u]", i);
        sleep(1);
    }
}
