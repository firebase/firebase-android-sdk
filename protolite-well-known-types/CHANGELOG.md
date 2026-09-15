# Unreleased

- [changed] Updated Protocol Buffers to 4.36.1 (was 3.25.5) and Google Common Protos to 2.75.0
  (was 1.18.0). The Google Common Protos update adds generated classes for the
  `com.google.apps.card.v1`, `com.google.cloud`, `com.google.cloud.location`, and
  `com.google.shopping.type` packages. (#8580)
- [changed] Removed the vendored copy of `descriptor.proto`. The
  `com.google.protobuf.DescriptorProtos` classes are no longer bundled in this library and are
  instead provided by `protobuf-javalite` 4.36.1, which this library depends on. (#8580)

# 18.0.1

- [changed] Updated protobuf dependency to `3.25.5` to fix
  [CVE-2024-7254](https://github.com/advisories/GHSA-735f-pc8j-v9w8).

## Kotlin

The Kotlin extensions library transitively includes the updated `protolite-well-known-types`
library. The Kotlin extensions library has no additional updates.
