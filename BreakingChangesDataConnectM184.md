# Breaking Changes for Data Connect SDK in M184 Release

This document keeps track of planned breaking changes to the Data Connect Android SDK planned for
the M184 release in July 2026.

* Breaking Releaes Name: M184
* Code Freeze: Tue Jul 21, 2026
* Release: Tue Jul 28, 2026
* Release Details: [go/firebase-sdk-dates](http://go/firebase-sdk-dates)

## Graduate `@ExperimentalFirebaseDataConnect` APIs

Several public APIs are annotated with `@ExperimentalFirebaseDataConnect`, which was done as a way
to bypass API council for seemingly-useful public APIs. These APIs are most definitely stable and
warrant graduation to bona fide APIs.

This will require an API proposal and approval by the API council.

Here are some of the APIs that are candidate for inclusion:

* [OperationRef.copy()](https://github.com/firebase/firebase-android-sdk/blob/13eb0f61e731c03a403556798c5bc28797093466/firebase-dataconnect/src/main/kotlin/com/google/firebase/dataconnect/OperationRef.kt#L187-L196)
* [OperationRef.withVariablesSerializer()](https://github.com/firebase/firebase-android-sdk/blob/13eb0f61e731c03a403556798c5bc28797093466/firebase-dataconnect/src/main/kotlin/com/google/firebase/dataconnect/OperationRef.kt#L198-L210)
* [OperationRef.withDataDeserializer()](https://github.com/firebase/firebase-android-sdk/blob/13eb0f61e731c03a403556798c5bc28797093466/firebase-dataconnect/src/main/kotlin/com/google/firebase/dataconnect/OperationRef.kt#L212-L223)
* The same methods from OperationRef in [MutationRef](https://github.com/firebase/firebase-android-sdk/blob/13eb0f61e731c03a403556798c5bc28797093466/firebase-dataconnect/src/main/kotlin/com/google/firebase/dataconnect/MutationRef.kt#L39-L62)
* The same methods from OperationRef in [QueryRef](https://github.com/firebase/firebase-android-sdk/blob/13eb0f61e731c03a403556798c5bc28797093466/firebase-dataconnect/src/main/kotlin/com/google/firebase/dataconnect/QueryRef.kt#L93-L116)
* The same methods from OperationRef in [GeneratedOperation](https://github.com/firebase/firebase-android-sdk/blob/13eb0f61e731c03a403556798c5bc28797093466/firebase-dataconnect/src/main/kotlin/com/google/firebase/dataconnect/generated/GeneratedOperation.kt#L69-L109)
* The same methods from OperationRef in [GeneratedMutation](https://github.com/firebase/firebase-android-sdk/blob/13eb0f61e731c03a403556798c5bc28797093466/firebase-dataconnect/src/main/kotlin/com/google/firebase/dataconnect/generated/GeneratedMutation.kt#L52-L68)
* The same methods from OperationRef in [GeneratedQuery](https://github.com/firebase/firebase-android-sdk/blob/13eb0f61e731c03a403556798c5bc28797093466/firebase-dataconnect/src/main/kotlin/com/google/firebase/dataconnect/generated/GeneratedQuery.kt#L52-L68)
* [GeneratedConnector.copy()](https://github.com/firebase/firebase-android-sdk/blob/13eb0f61e731c03a403556798c5bc28797093466/firebase-dataconnect/src/main/kotlin/com/google/firebase/dataconnect/generated/GeneratedConnector.kt#L42-L52)
* [GeneratedConnector.operations()](https://github.com/firebase/firebase-android-sdk/blob/13eb0f61e731c03a403556798c5bc28797093466/firebase-dataconnect/src/main/kotlin/com/google/firebase/dataconnect/generated/GeneratedConnector.kt#L54-L64)
* [GeneratedConnector.queries()](https://github.com/firebase/firebase-android-sdk/blob/13eb0f61e731c03a403556798c5bc28797093466/firebase-dataconnect/src/main/kotlin/com/google/firebase/dataconnect/generated/GeneratedConnector.kt#L66-L73)
* [GeneratedConnector.mutations()](https://github.com/firebase/firebase-android-sdk/blob/13eb0f61e731c03a403556798c5bc28797093466/firebase-dataconnect/src/main/kotlin/com/google/firebase/dataconnect/generated/GeneratedConnector.kt#L75-L82)

## Add `@SubclassOptInRequired` to interfaces

https://github.com/Kotlin/KEEP/issues/320

https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-subclass-opt-in-required/

Possibly update the Android SDK's "breaking change" detection to _not_ be triggered for additions
to interfaces marked with this annotation.

## QueryRef cachePolicy default parameter value

Currently QueryRef uses overloads to provide the default parameter value of
fetchPolicy=PREFER_CACHE. The natural way to express this is with a default argument; however,
that would have been a breaking change. Now is the time to make the breaking change.

https://github.com/firebase/firebase-android-sdk/blob/13eb0f61e731c03a403556798c5bc28797093466/firebase-dataconnect/src/main/kotlin/com/google/firebase/dataconnect/QueryRef.kt#L42-L43

## DataConnectSettings default parameter values

Same thing happened in DataConnectSettings when cacheSettings was added.
A new constructor overload was added as well as a new copy() overload.
Both could have been done with a new constructor/method parameter with a default value,
which would have been a binary breaking change.

https://github.com/firebase/firebase-android-sdk/blob/13eb0f61e731c03a403556798c5bc28797093466/firebase-dataconnect/src/main/kotlin/com/google/firebase/dataconnect/DataConnectSettings.kt#L43-L44

https://github.com/firebase/firebase-android-sdk/blob/13eb0f61e731c03a403556798c5bc28797093466/firebase-dataconnect/src/main/kotlin/com/google/firebase/dataconnect/DataConnectSettings.kt#L92-L93

## QueryRef Flow instead of QuerySubscription

The QuerySubscription class really only has one property of interest: `flow`.

This property should be moved to QueryRef and QuerySubscription should be retired.

This isn't necessarily a breaking change since I'd recommend leaving QuerySubscription
around for at least another year, then mark it deprecated, then delete it in 2 years.
But seems like an appropriate thing to include in a major version bump.

## Dependency Upgrades

The breaking change release would be a good chance to bump some core dependencies to their latest versions, such as

androidGradlePlugin
* kotlin
* kotlinx-coroutines-core
* kotlinx-serialization-core
* okhttp
* grpc-android
* protobuf-java

Rodrigo says: Hey Denver, we are going to push kotlin to 2.1 and update any kotlin dependency to
their last version which also depended on 2.1. protobuf & grpc are still open questions.

https://chat.google.com/room/AAAAlocx6vc/Ir33EH3Bxks/Ir33EH3Bxks?cls=10

Also, grpc provides a BoM `grpc-bom` that we should use instead of manual grpc version management.

On May 26, 2026 a customer reported issue requests a gRPC upgrade: https://github.com/firebase/firebase-ios-sdk/issues/16203

## FirebaseDataConnect.close() refactor

Re-evaluate the public APIs of FirebaseDataConnect.close().

Currently there are two funtions:

* `override fun close()`
* `override suspend fun suspendingClose()`

And `FirebaseDataConnect` implements `AutoCloseable` which possibly doesn't make much sense.

Consider changing `close()` to a suspend function.
