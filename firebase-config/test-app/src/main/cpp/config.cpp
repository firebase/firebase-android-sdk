#include <jni.h>
#include <string>
#include<unistd.h>

extern "C" JNIEXPORT jstring JNICALL
Java_com_google_firebase_testing_config_MainActivity_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_google_firebase_testing_config_MainActivity_nativeCrash(
        JNIEnv *env,
        jobject /* this */) {
    int *i = nullptr;
    *i = 7;
}
extern "C"
JNIEXPORT void JNICALL
Java_com_google_firebase_testing_config_MainActivity_nativeAnr(
        JNIEnv *env,
        jobject /* this */) {
    for (;;) {
        sleep(1);
    }
}
