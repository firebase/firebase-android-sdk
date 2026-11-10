# Release Report
## firebase-config

* Update change log correctly to reflect changes. (#8717)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8717) [commit](https://github.com/firebase/firebase-android-sdk/commit/f71ec348f8fe73abe630a7813a94e5636eb81983)  [Vinay Guthal]

## firebase-crashlytics

* Use long-form string for `trigger_type` parameter (#8750)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8750) [commit](https://github.com/firebase/firebase-android-sdk/commit/1bc5b9e192e18218a885548ec7cb03381e603aa5)  [Kai Bolay]

* Only send heap dumps on non-metered networks (#8712)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8712) [commit](https://github.com/firebase/firebase-android-sdk/commit/8751525a554060f29670b31e654263be8173943d)  [Konstantin Mandrika]

* Use hard-coded, shared Crashlytics API key for heapdump uploads (#8744)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8744) [commit](https://github.com/firebase/firebase-android-sdk/commit/3bc9a12eebd921b7b4be152ac619a7726d0a33b2)  [Kai Bolay]

* Better logging (#8738)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8738) [commit](https://github.com/firebase/firebase-android-sdk/commit/dc0ce88130e7f9621506471af919054cb2453aae)  [Kai Bolay]

* Implement out-of-band heap dump uploads (#8661)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8661) [commit](https://github.com/firebase/firebase-android-sdk/commit/aedf423dd48a353cab50777e715e1a644e8b15f8)  [Konstantin Mandrika]

## firebase-crashlytics-ndk


## firebase-sessions

* fix(sessions): handle settings data store read failures (#8767)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8767) [commit](https://github.com/firebase/firebase-android-sdk/commit/bfde87a6d214c31d2adf238e926d4ecc3187c39f)  [Paul Beusterien]

* Make app usable for Crashlytics OOM heapdump testing. (#8727)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8727) [commit](https://github.com/firebase/firebase-android-sdk/commit/cd2ed058249e8371063d6a8889ccb35c1322f835)  [Kai Bolay]

* Remove multidex config from sessions test app (#8731)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8731) [commit](https://github.com/firebase/firebase-android-sdk/commit/a43f74a913974c7ea9e19487eb5928f1410fc9cb)  [Matthew Robertson]

* Implement out-of-band heap dump uploads (#8661)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8661) [commit](https://github.com/firebase/firebase-android-sdk/commit/aedf423dd48a353cab50777e715e1a644e8b15f8)  [Konstantin Mandrika]

## firebase-firestore

* feat(firestore): pipeline window functions (#8639)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8639) [commit](https://github.com/firebase/firebase-android-sdk/commit/5f2d6602ecdf84a2e4adec9d11eeaede7f194998)  [Mark Duckworth]

* [Firestore] Make errorprone annotations compileOnly (#8772)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8772) [commit](https://github.com/firebase/firebase-android-sdk/commit/a878c85b5d8965b338aa2332392ee4232be290ce)  [Rodrigo Lazo]

* fix(firestore): graduate pipeline search from preview to GA (#8751)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8751) [commit](https://github.com/firebase/firebase-android-sdk/commit/750d47ab98b5f205b18c88d9c57e230d84caeaeb)  [Mark Duckworth]

* [Docs] Update documentation and Javadocs across Firebase AI and Firestore (#8746)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8746) [commit](https://github.com/firebase/firebase-android-sdk/commit/ee683fe3d43aff8b0eab6f5ca342ec7280a3fdd3)  [Rodrigo Lazo]

* [Firestore] Fix format (#8736)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8736) [commit](https://github.com/firebase/firebase-android-sdk/commit/133a7b95816277e26b4279bb85f1b4fdb37d99c3)  [Rodrigo Lazo]

* Add change log to firestore (#8724)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8724) [commit](https://github.com/firebase/firebase-android-sdk/commit/0bb74f5e25a354f843d4afdf5dc298fc1d00fe5b)  [Vinay Guthal]

* fix(firestore): revert testFixtures change (#8705)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8705) [commit](https://github.com/firebase/firebase-android-sdk/commit/a67daaecc8e86d727e308ea135f50dc100580ffe)  [Daniel La Rocque]

## firebase-messaging

* Restore FirebaseInstanceId token request deduplication in legacy mode (#8732)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8732) [commit](https://github.com/firebase/firebase-android-sdk/commit/ac604a0d02da2096dcb876e1b65ac88f4166a5c1)  [welishr]

* Retry operations on HTTP 429 in TopicsSubscriber (#8676)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8676) [commit](https://github.com/firebase/firebase-android-sdk/commit/b462e1b9120ede2c92f9bc68b41fdd544cb9d4b1)  [Vick Wang]

## firebase-messaging-directboot


## ai-logic/firebase-ai

* [AI] Add server-side voice activity detection support to LiveSession (#8800)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8800) [commit](https://github.com/firebase/firebase-android-sdk/commit/59a1d584e447e561041dc037f7933f90ba934f9f)  [Rodrigo Lazo]

* Function calling support for on device sdk. (#8758)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8758) [commit](https://github.com/firebase/firebase-android-sdk/commit/752de105d517bd0ef4b87be64020a1b2cadfa32c)  [Vinay Guthal]

* [AI] Support multiple parts in on-device content generation requests  (#8754)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8754) [commit](https://github.com/firebase/firebase-android-sdk/commit/a34288c65c52be4394541d59f262b7f98fce6b9a)  [Rodrigo Lazo]

* [Docs] Update documentation and Javadocs across Firebase AI and Firestore (#8746)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8746) [commit](https://github.com/firebase/firebase-android-sdk/commit/ee683fe3d43aff8b0eab6f5ca342ec7280a3fdd3)  [Rodrigo Lazo]

* [AI] Fix `testRealtime_speechConfig` integration test in firebase-ai (#8714)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8714) [commit](https://github.com/firebase/firebase-android-sdk/commit/dcc8d0eb52ea95d7822bb8e0fb2f45c161804e90)  [Rodrigo Lazo]

* [AI] Remove unused InternalSerializationApi opt-ins (#8713)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8713) [commit](https://github.com/firebase/firebase-android-sdk/commit/805f8654f4595b958454995108e124dcd3dce76f)  [Rodrigo Lazo]

* [AI] Add SpeechMetadata support to TextPart (#8690)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8690) [commit](https://github.com/firebase/firebase-android-sdk/commit/e4ea07128cc23e8991c20febc7d393a497305bf2)  [Rodrigo Lazo]

## ai-logic/firebase-ai-ondevice

* Function calling support for on device sdk. (#8758)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8758) [commit](https://github.com/firebase/firebase-android-sdk/commit/752de105d517bd0ef4b87be64020a1b2cadfa32c)  [Vinay Guthal]

* [AI] Support multiple parts in on-device content generation requests  (#8754)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8754) [commit](https://github.com/firebase/firebase-android-sdk/commit/a34288c65c52be4394541d59f262b7f98fce6b9a)  [Rodrigo Lazo]

## ai-logic/firebase-ai-ondevice-interop

* Function calling support for on device sdk. (#8758)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8758) [commit](https://github.com/firebase/firebase-android-sdk/commit/752de105d517bd0ef4b87be64020a1b2cadfa32c)  [Vinay Guthal]

* [AI] Support multiple parts in on-device content generation requests  (#8754)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8754) [commit](https://github.com/firebase/firebase-android-sdk/commit/a34288c65c52be4394541d59f262b7f98fce6b9a)  [Rodrigo Lazo]

## appcheck/firebase-appcheck

* feat(appcheck): support CLI-provisioned debug tokens and 403 recovery hints (#8791)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8791) [commit](https://github.com/firebase/firebase-android-sdk/commit/b5fb81375d0c57da83e23aea645d50729e6bec77)  [Austin Benoit]

## appcheck/firebase-appcheck-debug

* feat(appcheck): support CLI-provisioned debug tokens and 403 recovery hints (#8791)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8791) [commit](https://github.com/firebase/firebase-android-sdk/commit/b5fb81375d0c57da83e23aea645d50729e6bec77)  [Austin Benoit]

## appcheck/firebase-appcheck-debug-testing

* feat(appcheck): support CLI-provisioned debug tokens and 403 recovery hints (#8791)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8791) [commit](https://github.com/firebase/firebase-android-sdk/commit/b5fb81375d0c57da83e23aea645d50729e6bec77)  [Austin Benoit]

## appcheck/firebase-appcheck-playintegrity

* feat(appcheck): support CLI-provisioned debug tokens and 403 recovery hints (#8791)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8791) [commit](https://github.com/firebase/firebase-android-sdk/commit/b5fb81375d0c57da83e23aea645d50729e6bec77)  [Austin Benoit]

## appcheck/firebase-appcheck-recaptcha

* feat(appcheck): support CLI-provisioned debug tokens and 403 recovery hints (#8791)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8791) [commit](https://github.com/firebase/firebase-android-sdk/commit/b5fb81375d0c57da83e23aea645d50729e6bec77)  [Austin Benoit]


## SDKs with changes, but no changelogs
:firebase-common  
:firebase-dataconnect  
:protolite-well-known-types