# protobuf-javalite has a bug that requires this workaround rule
# https://github.com/protocolbuffers/protobuf/issues/6463#issuecomment-553183215
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
  <fields>;
}
