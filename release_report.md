# Release Report
## firebase-config

* Detect changes in AB Testing (ABT) experiment metadata during config fetch (#8002)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8002) [commit](https://github.com/firebase/firebase-android-sdk/commit/d40d90b652489115144b36f6481bb64d42aa4b38)  [Athira M]

## firebase-dataconnect

* dataconnect: fix CHANGELOG entries incorrectly moved to 17.2.1 header by PR #8070 (m179 mergeback) (#8073)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8073) [commit](https://github.com/firebase/firebase-android-sdk/commit/3cebbd9e116dc14d425f71bb8975b681c27f0b64)  [Denver Coneybeare]

* dataconnect: ci: upgrade data connect emulator to 3.4.5 (was 3.3.13) and firebase-tools to 15.15.0 (was 15.13.0) (#8057)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8057) [commit](https://github.com/firebase/firebase-android-sdk/commit/8b65c208d2c638d482ada65f34653b1f1aaa6b19)  [Denver Coneybeare]

* dataconnect(chore): use token objects instead of strings to improve type safety (#8027)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8027) [commit](https://github.com/firebase/firebase-android-sdk/commit/554fe26a4f0c71ace1f707fc1c62c11a401c29ed)  [Denver Coneybeare]

* dataconnect: ci: upgrade data connect emulator to 3.3.13 (was 3.3.1) and firebase-tools to 15.13.0 (was 15.12.0) (#8026)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8026) [commit](https://github.com/firebase/firebase-android-sdk/commit/6dc16c6ed78c1a495538d242a9685ea5b181272f)  [Denver Coneybeare]

* dataconnect(change): internal refactor of connectorResourceName variable names (#8025)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8025) [commit](https://github.com/firebase/firebase-android-sdk/commit/1a32c9cbda003bb81e83f09bd623b8ee3abab73d)  [Denver Coneybeare]

* dataconnect(change): refactor toCompactString() for future code reuse (#8024)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8024) [commit](https://github.com/firebase/firebase-android-sdk/commit/7cf6ce93ffd9281b7f298c2feb0823fc2ddcdedf)  [Denver Coneybeare]

* dataconnect(test): fix test flakiness in AuthIntegrationTest.kt (#8023)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8023) [commit](https://github.com/firebase/firebase-android-sdk/commit/7a7408bd19439756fcb0c159a9e386e4d3ffb91d)  [Denver Coneybeare]

## firebase-firestore

* feat(firestore): Added search stage support for languageCode, offset, limit, and retrievalDepth (#8066)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8066) [commit](https://github.com/firebase/firebase-android-sdk/commit/b78f1c6797991420e9ce739383cbc828f1911786)  [Mark Duckworth]

* test(firestore): Add tests for `forceIndex` with `"primary"` (#8036)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8036) [commit](https://github.com/firebase/firebase-android-sdk/commit/e163a9b90f0977cc522af4796f9ed4646aced4df)  [Daniel La Rocque]

* feat(firestore): add array expressions (#7989)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7989) [commit](https://github.com/firebase/firebase-android-sdk/commit/2eca2cfea6a4274ecbc539682f52cebf0c372220)  [Mila]

* docs: improve Pipeline API documentation formatting (#8017)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8017) [commit](https://github.com/firebase/firebase-android-sdk/commit/619471e4ad64af4e7e26a90e696c97feed9f0dd8)  [wu-hui]

## firebase-messaging

* [FCM] Fix ANR in SharedPreferencesQueue by reducing lock contention (#8068)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8068) [commit](https://github.com/firebase/firebase-android-sdk/commit/b396cfbf2fcb564733ede73f06501429caaa5506)  [Xiaohe Gong]

## firebase-messaging-directboot


## firebase-crashlytics


## firebase-crashlytics-ndk


## firebase-sessions

* Replace Mutex with CountDownLatch to fix ANR on cold start (#7996)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7996) [commit](https://github.com/firebase/firebase-android-sdk/commit/7f0a4f27c94b34f23dc35c8fe056170171be2ace)  [Joseph Rodiz]

## ai-logic/firebase-ai

* [AI] Add configurable model generation for AI On-Device  (#8043)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8043) [commit](https://github.com/firebase/firebase-android-sdk/commit/25e26e06189b55b9b921ecb78ae9ca34e511a626)  [Rodrigo Lazo]

* [AI] ImageConfig and FinishReasons (#8020)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8020) [commit](https://github.com/firebase/firebase-android-sdk/commit/2ce20e452bba7f6a41cc1f6637448824354b545d)  [Paul Beusterien]

* [AI] Improve SessionResumptionConfig constructors (#8069)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8069) [commit](https://github.com/firebase/firebase-android-sdk/commit/5742b348e00b23f23b1ffb55d174726ac241a7e4)  [Rodrigo Lazo]

* [AI] Add Java-friendly wrapper for TemplateChat (#8065)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8065) [commit](https://github.com/firebase/firebase-android-sdk/commit/2a33ae000bee36416d0b890668c528f5b5b3d8d6)  [Rodrigo Lazo]

* [AI] Attach X-Firebase-AppCheck header on Live API WebSocket handshake (#8063)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8063) [commit](https://github.com/firebase/firebase-android-sdk/commit/81f561420ad1396f9cbd1c9007dc6f5f0d1298dd)  [Jackson E J]

* Session resumption support (#8067)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8067) [commit](https://github.com/firebase/firebase-android-sdk/commit/47545737869a51a79f42fa1cea1fa6ed298dee8d)  [emilypgoogle]

* Add LiveSession testing (#8009)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8009) [commit](https://github.com/firebase/firebase-android-sdk/commit/a5e0041d2a0259429a9e39ba698cbd44658254c5)  [emilypgoogle]

* Implement Maps Grounding (#7983)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7983) [commit](https://github.com/firebase/firebase-android-sdk/commit/cc4edd65790c9e165a0ef03a3dd2fd40bebc1f33)  [emilypgoogle]

* [AI] Remove references to 1.5 in tests (#8028)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8028) [commit](https://github.com/firebase/firebase-android-sdk/commit/3f45c66300830e5845fa2070fdfecba17e820e97)  [Rodrigo Lazo]

* Fix the AI Logic link to console (#8018)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8018) [commit](https://github.com/firebase/firebase-android-sdk/commit/98b8e48283a838d35251a8daaa2bb1105f0939a2)  [lehcar09]

* Add turnComplete to API (#8014)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8014) [commit](https://github.com/firebase/firebase-android-sdk/commit/7b743b642c4f5b283087a55a7f14cce7206ed2da)  [emilypgoogle]

## ai-logic/firebase-ai-ondevice

* [AI] Add configurable model generation for AI On-Device  (#8043)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8043) [commit](https://github.com/firebase/firebase-android-sdk/commit/25e26e06189b55b9b921ecb78ae9ca34e511a626)  [Rodrigo Lazo]

* [Infra] Upgrade to Kotlin 2.1 compiler and remove image downsizing (#7896)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7896) [commit](https://github.com/firebase/firebase-android-sdk/commit/4191e8e60e0e9f2662ec9e557b2d954777763c7f)  [Rodrigo Lazo]

## ai-logic/firebase-ai-ondevice-interop

* [AI] Add configurable model generation for AI On-Device  (#8043)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8043) [commit](https://github.com/firebase/firebase-android-sdk/commit/25e26e06189b55b9b921ecb78ae9ca34e511a626)  [Rodrigo Lazo]


## SDKs with changes, but no changelogs
:ai-logic:firebase-ai-ksp-processor