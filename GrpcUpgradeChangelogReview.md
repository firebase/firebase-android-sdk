# gRPC Upgrade (#8580) — Changelog Accuracy Review

Branch `GrpcUpgrade` @ `854af86b5`, forked from `main` @ `64737ca0d`.

## What the branch actually changes

Only 6 files change. All dependency changes flow through `gradle/libs.versions.toml`:

| Catalog key | Before | After |
|---|---|---|
| `grpc` | 1.62.2 | **1.84.0** |
| `grpcKotlin` | 1.4.1 | **1.5.0** |
| `javalite` / `protobufjavautil` / `protoc` | 3.25.5 | **4.36.1** |
| `protoGoogleCommonProtos` | 1.18.0 | **2.75.0** |
| `truthProtoExtension` | 1.0 | **1.4.5** (test-only) |
| `protobufGradlePlugin` | 0.9.6 | 0.9.6 (bumped then reverted) |

Plus three non-catalog changes:
- [firebase-perf.gradle](file:///usr/local/google/home/dconeybe/work/android/main/firebase-perf/firebase-perf.gradle) — removed `api project(":protolite-well-known-types")`
- `protolite-well-known-types/src/main/proto/google/protobuf/descriptor.proto` — deleted
- [ParsingTests.kt](file:///usr/local/google/home/dconeybe/work/android/main/encoders/protoc-gen-firebase-encoders/src/test/kotlin/com/google/firebase/encoders/proto/codegen/ParsingTests.kt) — test-only protobuf 4.x fix

## Verdict per changelog

| Module | Original entry | Verdict |
|---|---|---|
| `firebase-appdistribution-gradle` | "Updated gRPC dependencies to 1.84.0" | ❌ **Factually wrong** |
| `firebase-dataconnect` | gRPC 1.84.0 + protobuf 4.36.1 | ⚠️ Incomplete |
| `firebase-firestore` | gRPC 1.84.0 + protobuf 4.36.1 | ⚠️ Correct but thin |
| `firebase-inappmessaging` | gRPC 1.84.0 + protobuf 4.36.1 | ⚠️ Correct but thin |
| `firebase-perf` | protobuf 4.36.1 | ⚠️ Incomplete — missed a removal |
| `protolite-well-known-types` | protobuf 4.36.1 + common protos 2.75.0 | ⚠️ Incomplete |

---

## 1. `firebase-appdistribution-gradle` — entry is wrong

> [!CAUTION]
> This module is **not affected by this branch at all.**

Its gRPC dependencies are hardcoded, not from the version catalog, and are untouched by this branch ([lines 233–238](file:///usr/local/google/home/dconeybe/work/android/main/firebase-appdistribution-gradle/firebase-appdistribution-gradle.gradle#L233-L238)):

```groovy
// Pin versions of io.grpc:grpc-* libraries to avoid issues with AGP tasks
// https://github.com/firebase/firebase-android-sdk/issues/6634
implementation("io.grpc:grpc-protobuf:1.69.1")
implementation("io.grpc:grpc-core:1.69.1")
implementation("io.grpc:grpc-stub:1.69.1")
implementation("io.grpc:grpc-netty:1.69.1")
```

The **only** version-catalog references in the entire file are `libs.junit` (3 occurrences, all test configurations). Nothing in this branch touches this module. Needs a decision — see the question at the end.

## 2. `firebase-dataconnect` — missed gRPC Kotlin and its forced upgrades

This is the only module using `grpc-kotlin-stub`, so the `grpcKotlin` 1.4.1 → 1.5.0 bump applies here and nowhere else. More importantly, `grpc-kotlin-stub:1.5.0` **forces upgrades of Kotlin libraries beyond what the repo pins**, confirmed on the resolved `releaseRuntimeClasspath`:

```
+--- io.grpc:grpc-kotlin-stub:1.5.0
+--- org.jetbrains.kotlin:kotlin-stdlib:2.1.21 -> 2.2.20
+--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0 -> 1.10.2
```

> [!IMPORTANT]
> The repo pins Kotlin `2.1.21` and coroutines `1.9.0`, but Data Connect consumers will resolve stdlib **2.2.20** and coroutines **1.10.2**. That is a stdlib newer than the compiler building the SDK, and is exactly the kind of thing app developers hit as a version conflict.

## 3. & 4. `firebase-firestore` / `firebase-inappmessaging` — accurate, but the protobuf path is indirect

Neither module declares `protobuf-javalite` directly. It arrives two ways, and Gradle's conflict resolution picks the higher:

```
+--- com.google.protobuf:protobuf-javalite:3.25.9 -> 4.36.1   # from grpc-protobuf-lite:1.84.0
\--- com.google.protobuf:protobuf-javalite:4.36.1             # from :protolite-well-known-types
```

So "Updated Protocol Buffers to 4.36.1" is true, but only *because* `protolite-well-known-types` wins over the 3.25.9 that gRPC 1.84.0 itself requests. Worth stating the path explicitly.

Also unmentioned: gRPC 1.62.2 → 1.84.0 drags along several transitive bumps.

| Transitive dep | 1.62.2 | 1.84.0 |
|---|---|---|
| `com.google.guava:guava` | 32.1.3-android | **33.6.0-android** |
| `com.google.code.gson:gson` | 2.10.1 | **2.14.0** |
| `io.perfmark:perfmark-api` | 0.26.0 | 0.27.0 |
| `error_prone_annotations` | 2.23.0 | 2.50.0 |
| `animal-sniffer-annotations` | 1.23 | 1.27 |
| `com.squareup.okio:okio` | 3.4.0 | 3.4.0 (unchanged) |

Guava and Gson are the two that realistically cause app-side conflicts.

## 5. `firebase-perf` — missed a removed dependency

The entry only mentioned protobuf. But this branch also removed `api project(":protolite-well-known-types")`. Confirmed gone from the resolved runtime classpath:

```
##### firebase-perf
+--- com.google.protobuf:protobuf-javalite:4.36.1
```

No `protolite-well-known-types` line. Since it was an `api` dependency, it was previously on the **compile** classpath of anything depending on `firebase-perf`. Dropping it is user-visible and belongs in the changelog.

## 6. `protolite-well-known-types` — two undocumented content changes

**a) Common Protos 1.18.0 → 2.75.0 grows the artifact.** 49 → 65 `.proto` files, adding four brand-new generated packages:

- `com.google.apps.card.v1`
- `com.google.cloud`
- `com.google.cloud.location`
- `com.google.shopping.type`

**b) The `descriptor.proto` deletion moves `DescriptorProtos` out of this AAR.**

Verified that `protobuf-javalite:4.36.1` now bundles `com/google/protobuf/DescriptorProtos*.class`, and that neither Common Protos jar (1.18.0 or 2.75.0) ships any `google/protobuf/*.proto`. So after this change, protolite generates **no** `com.google.protobuf.*` classes at all.

> [!WARNING]
> `protobuf-javalite` is declared as `implementation` in [protolite-well-known-types.gradle:75](file:///usr/local/google/home/dconeybe/work/android/main/protolite-well-known-types/protolite-well-known-types.gradle#L75), so it lands in the published POM at **runtime** scope. `DescriptorProtos` used to be compiled into the AAR itself and was therefore on consumers' **compile** classpath. It now resolves at runtime only. Any external consumer compiling against `com.google.protobuf.DescriptorProtos` via this library would break. Probably nobody does — but it is a real compile-classpath regression, not purely an internal cleanup.

## Modules correctly left with no changelog entry

Verified these consume changed catalog entries but ship nothing new:

- `firebase-messaging`, `firebase-ml-modeldownloader`, `transport-runtime`, `transport-backend-cct` — use `libs.protoc` for codegen via the `protoc-gen-firebase-encoders` plugin; no protobuf runtime dependency is published.
- `encoders/firebase-encoders-proto`, `encoders/protoc-gen-firebase-encoders` — `truth-proto-extension` / `protobuf-java-util` changes are `testImplementation` only.
- `firebase-abt` (`grpc-testing`), `firebase-crashlytics` / `firebase-crashlytics-ndk` (`protobuf-java`) — test/androidTest only.
- `firebase-dataconnect:testutil` — not published.
