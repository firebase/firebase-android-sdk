#ifndef CRASHPAD_UTIL_EXECVPE_H
#define CRASHPAD_UTIL_EXECVPE_H

#if __ANDROID_API__ < 21
extern "C" {
int execvpe(const char *__file, char *const *__argv, char *const *__envp);
}
#endif /* __ANDROID_API__ < 21 */

#endif //CRASHPAD_UTIL_EXECVPE_H
