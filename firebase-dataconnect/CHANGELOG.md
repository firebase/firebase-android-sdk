# Unreleased
* [changed] Requires Data Connect emulator version 1.6.1 or later for code generation.
* [feature] QueryRef and MutationRef gain methods copy(), withDataDeserializer(),
  and withVariablesSerializer().
  ([#6424](https://github.com/firebase/firebase-android-sdk/pull/6424))
* [feature] GeneratedConnector gains methods copy(), operations(), queries(),
  and mutations().
  ([#6424](https://github.com/firebase/firebase-android-sdk/pull/6424))
* [feature] GeneratedQuery and GeneratedMutation gain methods copy(),
  withVariablesSerializer(), and withDataDeserializer().
  ([#6424](https://github.com/firebase/firebase-android-sdk/pull/6424))
* [feature] GeneratedConnector, GeneratedQuery, and GeneratedMutation now
  must implement equals() to be a _logical_ comparsion, rather than just
  checking for _referencial_ equality using the `===` operator.
  ([#6424](https://github.com/firebase/firebase-android-sdk/pull/6424))
* [feature] ExperimentalFirebaseDataConnect annotation added, and some
  APIs have been annotated with it, requiring applications that make use of
  these experimental APIs to opt-in using
  `@OptIn(ExperimentalFirebaseDataConnect::class)` to suppress warnings or
  errors related to using these experimental APIs.
  ([#6424](https://github.com/firebase/firebase-android-sdk/pull/6424)) and
  ([#6433](https://github.com/firebase/firebase-android-sdk/pull/6433))
* [changed] Replaced java.util.Date with
  com.google.firebase.dataconnect.LocalDate.
  ([#6434](https://github.com/firebase/firebase-android-sdk/pull/6434))

# 16.0.0-beta02
* [changed] Updated protobuf dependency to `3.25.5` to fix
  [CVE-2024-7254](https://nvd.nist.gov/vuln/detail/CVE-2024-7254).

# 16.0.0-beta01
* [feature] Initial release of the Data Connect SDK (public preview). Learn how to
  [get started](https://firebase.google.com/docs/data-connect/android-sdk)
  with the SDK in your app.
* [feature] Added App Check support.
  ([#6176](https://github.com/firebase/firebase-android-sdk/pull/6176))
* [feature] Added `AnyValue` to support the `Any` custom GraphQL scalar type.
  ([#6285](https://github.com/firebase/firebase-android-sdk/pull/6285))
* [feature] Added `OrderDirection` enum support.
  ([#6307](https://github.com/firebase/firebase-android-sdk/pull/6307))
* [feature] Added ability to specify `SerializersModule` when serializing.
  ([#6297](https://github.com/firebase/firebase-android-sdk/pull/6297))
* [feature] Added `CallerSdkType`, which enables tracking of the generated SDK usage.
  ([#6298](https://github.com/firebase/firebase-android-sdk/pull/6298) and
  [#6179](https://github.com/firebase/firebase-android-sdk/pull/6179))
* [changed] Changed gRPC proto package to v1beta (was v1alpha).
  ([#6299](https://github.com/firebase/firebase-android-sdk/pull/6299))
* [changed] Added `equals` and `hashCode` methods to `GeneratedConnector`.
  ([#6177](https://github.com/firebase/firebase-android-sdk/pull/6177))

