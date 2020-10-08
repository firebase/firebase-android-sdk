#include <android/log.h>
#include <dlfcn.h>
#include <stdlib.h>

// The first argument passed to the trampoline is the name of the native library
// exporting the symbol `CrashpadHandlerMain`. The remaining arguments are the
// same as for `HandlerMain()`.
int main(int argc, char* argv[])
{
  static constexpr char kTag[] = "libcrashlytics-trampoline";

  if (argc < 2) {
    __android_log_print(ANDROID_LOG_FATAL, kTag, "usage: %s <path>", argv[0]);
    return EXIT_FAILURE;
  }

  void* handle = dlopen(argv[1], RTLD_LAZY | RTLD_GLOBAL);
  if (!handle) {
    __android_log_print(ANDROID_LOG_FATAL, kTag, "dlopen: %s", dlerror());
    return EXIT_FAILURE;
  }

  using MainType = int (*)(int, char*[]);
  MainType crashpad_main =
      reinterpret_cast<MainType>(dlsym(handle, "CrashpadHandlerMain"));
  if (!crashpad_main) {
    __android_log_print(ANDROID_LOG_FATAL, kTag, "dlsym: %s", dlerror());
    return EXIT_FAILURE;
  }

  return crashpad_main(argc - 1, argv + 1);
}
