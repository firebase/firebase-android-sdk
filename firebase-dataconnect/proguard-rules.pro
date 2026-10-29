# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# protobuf-javalite has a bug that requires this workaround rule
# https://github.com/protocolbuffers/protobuf/issues/6463#issuecomment-553183215
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
  <fields>;
}
