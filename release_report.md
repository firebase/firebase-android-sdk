# Release Report
## firebase-config

* Fixes #5439: Fix ConcurrentModificationException  (#8686)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8686) [commit](https://github.com/firebase/firebase-android-sdk/commit/7b511ff26a00acecaa5b59bd102694da13ed7830)  [tanyagoyan-boop]

## firebase-crashlytics

* Set up Android Test Orchestrator for Crashlytics instrumentation tests (#8666)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8666) [commit](https://github.com/firebase/firebase-android-sdk/commit/c9c9d37387cf72150bfff50ec60cdaf09b7be66f)  [Matthew Robertson]

## firebase-crashlytics-ndk

* Close the ApplicationExitInfo trace stream in Crashlytics NDK (#8657)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8657) [commit](https://github.com/firebase/firebase-android-sdk/commit/56c3d5af3bba7bac303671b4d5b4b2e0d21a65fc)  [Matthew Robertson]

## firebase-sessions

* Pin the AQS test app to same NDK version as Crashlytics NDK (#8644)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8644) [commit](https://github.com/firebase/firebase-android-sdk/commit/779937742efe428ee31f8be9681d817d5211fef6)  [Matthew Robertson]

## firebase-dataconnect

* dataconnect(fix): add proguard rules to prevent minification of GeneratedMessageLite (#8698)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8698) [commit](https://github.com/firebase/firebase-android-sdk/commit/274deea3c1d5eefead0abc1609541197989fbede)  [Denver Coneybeare]

* [Infra] Update Dataconnect dependency and Messaging changelog  (#8689)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8689) [commit](https://github.com/firebase/firebase-android-sdk/commit/5f9d2bd1785910c55f0003b78717b0622ec7e371)  [Rodrigo Lazo]

* dataconnect/demo: upgrade dependencies to fix build (#8692)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8692) [commit](https://github.com/firebase/firebase-android-sdk/commit/ea734d23e79b19b162923580b79843c77e771ae0)  [Denver Coneybeare]

* dataconnect: ci: upgrade data connect emulator to 3.4.21 (was 3.4.19) and firebase-tools to 15.32.0 (was 15.30.0) (#8650)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8650) [commit](https://github.com/firebase/firebase-android-sdk/commit/09ee73c86d802a46473e12a4abc3b3ebb2e16224)  [Denver Coneybeare]

* dataconnect(change): AnyValue.kt: disambiguate [serializer] kdoc links (#8649)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8649) [commit](https://github.com/firebase/firebase-android-sdk/commit/981ed9a12f687f668654fb1c87fc4f2da023961a)  [Denver Coneybeare]

## firebase-firestore

* feat(firestore): add DML stages, literals source, and atomic execution option (#8524)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8524) [commit](https://github.com/firebase/firebase-android-sdk/commit/3237cbd9a80db589a8f8d24ca1fbda1dcca78fd1)  [wu-hui]

## firebase-messaging

* [Infra] Update Dataconnect dependency and Messaging changelog  (#8689)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8689) [commit](https://github.com/firebase/firebase-android-sdk/commit/5f9d2bd1785910c55f0003b78717b0622ec7e371)  [Rodrigo Lazo]

* Handle non-retryable errors in TopicsSubscriber (#8662)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8662) [commit](https://github.com/firebase/firebase-android-sdk/commit/1bbd6a79622e8aee87ca5d2c9b191739699ee0ec)  [Vick Wang]

* URL-encode topic name in TopicSubscriptionClient (#8658)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8658) [commit](https://github.com/firebase/firebase-android-sdk/commit/e51192421f7e05225acd3a568bd30f1e06b8e69d)  [Vick Wang]

## firebase-messaging-directboot


## firebase-perf

* [Perf] Update firebase-perf changelog for NoSuchFieldException fix (#8700)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8700) [commit](https://github.com/firebase/firebase-android-sdk/commit/f546b3b2a69ee89fc39c12b119c0b3d79f4287cb)  [Rodrigo Lazo]

* [Perf] Add consumer ProGuard rules for protobuf-javalite (#8697)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8697) [commit](https://github.com/firebase/firebase-android-sdk/commit/f1f34fc8c402480cab71cfc93724cf00db246244)  [Rodrigo Lazo]

## ai-logic/firebase-ai

* Add thinking mode for ai logic hybrid (#8678)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8678) [commit](https://github.com/firebase/firebase-android-sdk/commit/5f8a568fe3d18e5de6f69131ee66c08b791ea56e)  [Vinay Guthal]

* [AI] Fix AI Logic changelog (#8675)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8675) [commit](https://github.com/firebase/firebase-android-sdk/commit/61d7ea4717667f5da0dd662008ac7c1699081124)  [Rodrigo Lazo]

* [AI] Address rebranding changes (#8674)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8674) [commit](https://github.com/firebase/firebase-android-sdk/commit/1df4021f956c6c042ebbf68b0765fc9dfcca6030)  [Rodrigo Lazo]

* Add support for system instructions in the ondevice sdk. (#8663)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8663) [commit](https://github.com/firebase/firebase-android-sdk/commit/46ae3a2e547a09f49507550f532a10a1e81d5a61)  [Vinay Guthal]

## ai-logic/firebase-ai-ondevice

* Add thinking mode for ai logic hybrid (#8678)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8678) [commit](https://github.com/firebase/firebase-android-sdk/commit/5f8a568fe3d18e5de6f69131ee66c08b791ea56e)  [Vinay Guthal]

* Add support for system instructions in the ondevice sdk. (#8663)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8663) [commit](https://github.com/firebase/firebase-android-sdk/commit/46ae3a2e547a09f49507550f532a10a1e81d5a61)  [Vinay Guthal]

## ai-logic/firebase-ai-ondevice-interop

* Add thinking mode for ai logic hybrid (#8678)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8678) [commit](https://github.com/firebase/firebase-android-sdk/commit/5f8a568fe3d18e5de6f69131ee66c08b791ea56e)  [Vinay Guthal]

* Add support for system instructions in the ondevice sdk. (#8663)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8663) [commit](https://github.com/firebase/firebase-android-sdk/commit/46ae3a2e547a09f49507550f532a10a1e81d5a61)  [Vinay Guthal]


## SDKs with changes, but no changelogs
:firebase-annotations  
:firebase-common  
:firebase-components  
:appcheck:firebase-appcheck  
:appcheck:firebase-appcheck-debug  
:appcheck:firebase-appcheck-debug-testing  
:appcheck:firebase-appcheck-playintegrity  
:appcheck:firebase-appcheck-recaptcha