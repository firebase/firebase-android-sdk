# Unreleased

- [changed] **Breaking Change**: Updated minSdkVersion to API level 24 or higher. (#8638)
- [changed] **Breaking change:** Updated Protocol Buffers dependency to
  `4.36.1` and Google Common Protos to `2.75.0`.
  ([#8580](https://github.com/firebase/firebase-android-sdk/pull/8580))
- [changed] Removed the vendored copy of `descriptor.proto`. The
  `DescriptorProtos` classes are no longer bundled in this library and are
  instead provided by `protobuf-javalite`. (#8580)

# 18.0.1

- [changed] Updated protobuf dependency to `3.25.5` to fix
  [CVE-2024-7254](https://github.com/advisories/GHSA-735f-pc8j-v9w8).

## Kotlin

The Kotlin extensions library transitively includes the updated `protolite-well-known-types`
library. The Kotlin extensions library has no additional updates.
