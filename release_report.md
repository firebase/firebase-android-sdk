# Release Report
## firebase-appdistribution

* Make app-distribution InstallActivity exported=false and validate input (#8229)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8229) [commit](https://github.com/firebase/firebase-android-sdk/commit/82e036a601c8c48d032cc983da0e8136e1f77883)  [Lee Kellogg]

* Fix some lint issues in firebase-appdistribution/test-app (#8234)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8234) [commit](https://github.com/firebase/firebase-android-sdk/commit/97f53c9c7b585a9fbbe4832d7308c96868b66cf9)  [Lee Kellogg]

## firebase-appdistribution-api

* Make app-distribution InstallActivity exported=false and validate input (#8229)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8229) [commit](https://github.com/firebase/firebase-android-sdk/commit/82e036a601c8c48d032cc983da0e8136e1f77883)  [Lee Kellogg]

## firebase-common

* Add reCAPTCHA site key support to FirebaseOptions (#8216)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8216) [commit](https://github.com/firebase/firebase-android-sdk/commit/87b6032f995ab38b93d0f3298782e094dada385b)  [Rodrigo Lazo]

* fix(common): Resolve thread deadlock in HeartBeatInfoStorage (#8182)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8182) [commit](https://github.com/firebase/firebase-android-sdk/commit/db94adb124062cd3051f665469723aa0b41b364b)  [Mila]

## firebase-dataconnect

* dataconnect(fix): refresh auth token during realtime query subscription (#8278)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8278) [commit](https://github.com/firebase/firebase-android-sdk/commit/6161b2904cf96ec8be50b77170906aa7422f9f86)  [Denver Coneybeare]

* dataconnect(chore): add `token` StateFlow to observe token changes (#8273)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8273) [commit](https://github.com/firebase/firebase-android-sdk/commit/8e331cb82ed2dbdd14d1c56b1384be9b9e18cc77)  [Denver Coneybeare]

* dataconnect: ci: upgrade data connect emulator to 3.4.10 (was 3.4.8) and firebase-tools to 15.19.1 (was 15.18.0) (#8263)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8263) [commit](https://github.com/firebase/firebase-android-sdk/commit/35a4f1bd1f884c8756cd821611a33fef5d917105)  [Denver Coneybeare]

* dataconnect(fix): Fix duplicate cache/server events from QuerySubscription.flow (#8240)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8240) [commit](https://github.com/firebase/firebase-android-sdk/commit/1101aa4d90fa4e8a24e50bc694405f445bbc3513)  [Denver Coneybeare]

* dataconnect(chore) more refactoring in preparating for realtime query result deduplication fix (#8260)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8260) [commit](https://github.com/firebase/firebase-android-sdk/commit/a1a58a76e02eae389ea6b98f6bc5a178f72eeed8)  [Denver Coneybeare]

* dataconnect: fix CHANGELOG entries incorrectly moved to 17.3.0 header by PR #8235 (m181 mergeback) (#8256)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8256) [commit](https://github.com/firebase/firebase-android-sdk/commit/3c1273b19c61f9714e05b4c7b80c0490ff0bd2b6)  [Denver Coneybeare]

* dataconnect(chore): realtime refactor in preparation for deduplicating cached and server results (#8236)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8236) [commit](https://github.com/firebase/firebase-android-sdk/commit/1589cf5e537e703238f3a30f94794b608fd20336)  [Denver Coneybeare]

* m181 mergeback (#8235)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8235) [commit](https://github.com/firebase/firebase-android-sdk/commit/daadb6e64157d6c55e7558988ebd27b2dbc6a2a6)  [Google Open Source Bot]

* dataconnect(docs): Fix BoM version noted in the docs for when realtime support was added to 34.14.0 (was 34.13.0) (#8237)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8237) [commit](https://github.com/firebase/firebase-android-sdk/commit/c80152fe3ada799c684117837ad86467ede0af73)  [Denver Coneybeare]

* dataconnect(chore): add `last_update_sequence_number` to cache db (#8226)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8226) [commit](https://github.com/firebase/firebase-android-sdk/commit/2fd9dc60c8340261a70858d016c452c11733c019)  [Denver Coneybeare]

* dataconnect(feat): Realtime query results now update the local cache (#8220)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8220) [commit](https://github.com/firebase/firebase-android-sdk/commit/dbb523f36be14b404670f4561d10b9c87886d071)  [Denver Coneybeare]

* dataconnect(chore): headless_android_emulator_run.zsh: pass args after `--` to the emulator command linea (#8215)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8215) [commit](https://github.com/firebase/firebase-android-sdk/commit/e79be6479de08a1cfb289ebc379552a39f94fc35)  [Denver Coneybeare]

* dataconnect(chore): refactor cache database management logic out of network layer (#8211)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8211) [commit](https://github.com/firebase/firebase-android-sdk/commit/83d0dda51d87dbc59bcbce1462520bc5875d29f4)  [Denver Coneybeare]

* dataconnect(fix): CACHE_ONLY was incorrectly ignored when cache was not enabled (#8214)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8214) [commit](https://github.com/firebase/firebase-android-sdk/commit/e313666eb179d257af6414cd84a668da748c39ff)  [Denver Coneybeare]

* dataconnect(chore): Add QueryId value type and use it instead of passing around raw byte arrays (#8207)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8207) [commit](https://github.com/firebase/firebase-android-sdk/commit/c7094fafdd21b0b1cae5b748e9910a5ea3c9b154)  [Denver Coneybeare]

* dataconnect(chore): Create AuthUid value class to add some type safety compared to raw strings (#8198)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8198) [commit](https://github.com/firebase/firebase-android-sdk/commit/85ee4b452a4a788c8b100ef0275739613ce2759c)  [Denver Coneybeare]

* dataconnect(ci): move firebase-tools version out of the yml workflow files (#8197)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8197) [commit](https://github.com/firebase/firebase-android-sdk/commit/f343cd9f9c4a3506cb2f6be6f1cebd51f973ff16)  [Denver Coneybeare]

* dataconnect(docs): QueryRef/QuerySubscription: kdoc improvements, especially adding BoM version (#8196)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8196) [commit](https://github.com/firebase/firebase-android-sdk/commit/b61c8cfa1c822c722c5f72eb5a09f4645fe6fd0a)  [Denver Coneybeare]

* dataconnect(chore): convert gemini-cli commands to agent "skills" (#8187)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8187) [commit](https://github.com/firebase/firebase-android-sdk/commit/13eb0f61e731c03a403556798c5bc28797093466)  [Denver Coneybeare]

## firebase-firestore

* feat(firestore): added minimum and maximum FieldValue operations (#8101)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8101) [commit](https://github.com/firebase/firebase-android-sdk/commit/36b3e5b32a79cebf70dccc3f6c047d3abf0e243e)  [Mark Duckworth]

## firebase-installations

* Fix fidchangelistener behavior (#8193)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8193) [commit](https://github.com/firebase/firebase-android-sdk/commit/f23afff04bf02c165486b1fdc3044b5d727be4a9)  [Eldhose M Babu]

## firebase-messaging

* FCM V1 API Migration initial commit. (#8087)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8087) [commit](https://github.com/firebase/firebase-android-sdk/commit/3f01b025b5b651d084902c96168fea89bdb0f50d)  [Eldhose M Babu]

## firebase-messaging-directboot


## ai-logic/firebase-ai

* [AI] Support SpeechConfig in Text-To-Speech (#8100)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8100) [commit](https://github.com/firebase/firebase-android-sdk/commit/8ec2f39ef4a4ad90c7fa5f715f5c237f58965f04)  [Mila]

* Add model version to GenerateContent Output (#8227)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8227) [commit](https://github.com/firebase/firebase-android-sdk/commit/21831fd9cf065f39f7869b8b9010f38139dd6918)  [Vinay Guthal]

* [AI] Return `retrievalConfig` entry to the right changelog section (#8253)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8253) [commit](https://github.com/firebase/firebase-android-sdk/commit/232c864fc9e49f4996dcfa1b4c63983df2315dc2)  [Rodrigo Lazo]

* [AI] Update App Check dependencies in firebase-ai (#8225)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8225) [commit](https://github.com/firebase/firebase-android-sdk/commit/31ca8b76376264ce99f8b4fb63fea8249108219c)  [Rodrigo Lazo]

* m181 mergeback (#8235)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8235) [commit](https://github.com/firebase/firebase-android-sdk/commit/daadb6e64157d6c55e7558988ebd27b2dbc6a2a6)  [Google Open Source Bot]

* Add Gemini 3.5 flash models to testing (#8213)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8213) [commit](https://github.com/firebase/firebase-android-sdk/commit/de675d9d6c676219070b4e62dea55d6da0f02cd3)  [emilypgoogle]

* Add missing construction to TemplateTool that is present in Tool (#8188)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8188) [commit](https://github.com/firebase/firebase-android-sdk/commit/1ac16c8912af22fcb7f8019096aa780e80a75f4f)  [emilypgoogle]

## ai-logic/firebase-ai-ondevice

* Add model version to GenerateContent Output (#8227)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8227) [commit](https://github.com/firebase/firebase-android-sdk/commit/21831fd9cf065f39f7869b8b9010f38139dd6918)  [Vinay Guthal]

## appcheck/firebase-appcheck

* m181 mergeback (#8235)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8235) [commit](https://github.com/firebase/firebase-android-sdk/commit/daadb6e64157d6c55e7558988ebd27b2dbc6a2a6)  [Google Open Source Bot]

* [AppCheck] Rename reCAPTCHA App Check provider (#8224)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8224) [commit](https://github.com/firebase/firebase-android-sdk/commit/df09c49b3fcf54e427724786cf583da4635dab6d)  [Rodrigo Lazo]

* [AppCheck] Add getLimitedUseToken support to AppCheckProvider (#8204)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8204) [commit](https://github.com/firebase/firebase-android-sdk/commit/f2dc87362681e99bda2c0ffcaecbebe4fcdc0508)  [Rodrigo Lazo]

## appcheck/firebase-appcheck-debug

* m181 mergeback (#8235)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8235) [commit](https://github.com/firebase/firebase-android-sdk/commit/daadb6e64157d6c55e7558988ebd27b2dbc6a2a6)  [Google Open Source Bot]

* [AppCheck] Add getLimitedUseToken support to AppCheckProvider (#8204)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8204) [commit](https://github.com/firebase/firebase-android-sdk/commit/f2dc87362681e99bda2c0ffcaecbebe4fcdc0508)  [Rodrigo Lazo]

## appcheck/firebase-appcheck-debug-testing

* m181 mergeback (#8235)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8235) [commit](https://github.com/firebase/firebase-android-sdk/commit/daadb6e64157d6c55e7558988ebd27b2dbc6a2a6)  [Google Open Source Bot]

* [AppCheck] Add getLimitedUseToken support to AppCheckProvider (#8204)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8204) [commit](https://github.com/firebase/firebase-android-sdk/commit/f2dc87362681e99bda2c0ffcaecbebe4fcdc0508)  [Rodrigo Lazo]

## appcheck/firebase-appcheck-playintegrity

* m181 mergeback (#8235)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8235) [commit](https://github.com/firebase/firebase-android-sdk/commit/daadb6e64157d6c55e7558988ebd27b2dbc6a2a6)  [Google Open Source Bot]

* [AppCheck] Add getLimitedUseToken support to AppCheckProvider (#8204)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8204) [commit](https://github.com/firebase/firebase-android-sdk/commit/f2dc87362681e99bda2c0ffcaecbebe4fcdc0508)  [Rodrigo Lazo]

## appcheck/firebase-appcheck-recaptcha

* [AppCheck] Promote Firebase App Check Recaptcha to GA (#8275)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8275) [commit](https://github.com/firebase/firebase-android-sdk/commit/eb643b8cd36ae311f3da6da1eb3c2797258a3dcb)  [Rodrigo Lazo]

* [AppCheck] Revert changed key in ExchangeRecaptchaTokenRequest (#8230)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8230) [commit](https://github.com/firebase/firebase-android-sdk/commit/4c914b486ed1a1dcbd3310d8e22ea195815b8b1b)  [Rodrigo Lazo]

* [AppCheck] Rename reCAPTCHA App Check provider (#8224)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8224) [commit](https://github.com/firebase/firebase-android-sdk/commit/df09c49b3fcf54e427724786cf583da4635dab6d)  [Rodrigo Lazo]


## SDKs with changes, but no changelogs
:firebase-perf  
:ai-logic:firebase-ai-ondevice-interop