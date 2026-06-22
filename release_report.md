# Release Report
## firebase-dataconnect

* dataconnect(chore): rename internal class AuthUidChangedException to FirebaseUserChangedException (#8324)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8324) [commit](https://github.com/firebase/firebase-android-sdk/commit/1f4bdf8c01fdc8b98ef0fd86177a27973bd62c4c)  [Denver Coneybeare]

* dataconnect(fix): fix potential infinite loop when Auth and/or App Check tokens refreshed (#8319)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8319) [commit](https://github.com/firebase/firebase-android-sdk/commit/c9224d10324a92844c05e92d12a03805519bdf35)  [Denver Coneybeare]

* dataconnect(test): person_ops.gql: fix illegal `@check` directive outside of a transaction (#8314)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8314) [commit](https://github.com/firebase/firebase-android-sdk/commit/fdbf6f779a65207babebc56753c58141104cd4da)  [Denver Coneybeare]

* dataconnect(chore): headless_android_emulator_run.zsh minor improvements (#8313)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8313) [commit](https://github.com/firebase/firebase-android-sdk/commit/9ddc6c3d101fb7c9aee018fee8005358d9f05cf7)  [Denver Coneybeare]

* dataconnect(test): add tests for auth token changes concurrent with reconnects (#8305)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8305) [commit](https://github.com/firebase/firebase-android-sdk/commit/7ec63ec5ae4c7968d5cceaf547f08e90c5029f60)  [Denver Coneybeare]

* dataconnect(fix): fix abruptly failure in realtime query subscriptions if the auth token changed (#8312)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8312) [commit](https://github.com/firebase/firebase-android-sdk/commit/688492364145fe37f0bdbd09f5c053aa115aa022)  [Denver Coneybeare]

* dataconnect(test): unit tests for auth and app check tokens on realtime initial connection and reconnection (#8300)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8300) [commit](https://github.com/firebase/firebase-android-sdk/commit/b735b6d8a844e0ebb25752b681a08ce64588c23f)  [Denver Coneybeare]

* dataconnect: ci: upgrade data connect emulator to 3.4.11 (was 3.4.10) and firebase-tools to 15.20.0 (was 15.19.1) (#8294)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8294) [commit](https://github.com/firebase/firebase-android-sdk/commit/f9800d1fdebd05e4aa0711732e39ee05e9622030)  [Denver Coneybeare]

* dataconnect(test): QuerySubscriptionImplUnitTest.kt: add test: `flow fails with AuthUidChangedException if auth uid changes during reconnection` (#8291)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8291) [commit](https://github.com/firebase/firebase-android-sdk/commit/be71901ecedb249c54ea8978b8bc6dac37c1b409)  [Denver Coneybeare]

* build(deps): bump pytest from 8.3.5 to 9.0.3 in /firebase-dataconnect/ci (#8035)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8035) [commit](https://github.com/firebase/firebase-android-sdk/commit/414271b620796063b1ea6cf938d6af8ee52420c4)  [dependabot[bot]]

* dataconnect(fix): Throw exception when AuthUid changes in realtime query subscriptions instead of silently just going silent (#8283)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8283) [commit](https://github.com/firebase/firebase-android-sdk/commit/0d5bf8169bef154bcade9b0c584eebbbbb1e401d)  [Denver Coneybeare]

## firebase-messaging

* Version update to 25.1.0 (#8293)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8293) [commit](https://github.com/firebase/firebase-android-sdk/commit/77eb9511c69997c2741cdb4dc30a3d2722e00976)  [Eldhose M Babu]

## firebase-messaging-directboot

* Version update to 25.1.0 (#8293)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8293) [commit](https://github.com/firebase/firebase-android-sdk/commit/77eb9511c69997c2741cdb4dc30a3d2722e00976)  [Eldhose M Babu]

* Add ChangeLog for release (AILogicOndeviceinterop and messaging directboot) (#8285)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8285) [commit](https://github.com/firebase/firebase-android-sdk/commit/df801c8c3945dc5a4ca9cd996b7c1f029e29d7d9)  [Vinay Guthal]

## firebase-perf

* Feature/jrc  8103.fix.api.34.heuristic (#8326)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8326) [commit](https://github.com/firebase/firebase-android-sdk/commit/95136b61a11170b0a0bd6411cce217f5b7bf06dc)  [Joseph Rodiz]

## ai-logic/firebase-ai-ondevice-interop

* Add ChangeLog for release (AILogicOndeviceinterop and messaging directboot) (#8285)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8285) [commit](https://github.com/firebase/firebase-android-sdk/commit/df801c8c3945dc5a4ca9cd996b7c1f029e29d7d9)  [Vinay Guthal]

## appcheck/firebase-appcheck

* [AppCheck] Bump Firebase App Check version to 19.2.0 (#8295)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8295) [commit](https://github.com/firebase/firebase-android-sdk/commit/20ec6fb21a0ed7528628681de83b8da3537f1a4a)  [Rodrigo Lazo]

* Add Change log for appcheck (#8287)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8287) [commit](https://github.com/firebase/firebase-android-sdk/commit/5886da2426c281152628d4c46f844aafc4f9720f)  [Vinay Guthal]

## appcheck/firebase-appcheck-debug

* Update changelogs and gradle file to enable javadoc generation (#8299)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8299) [commit](https://github.com/firebase/firebase-android-sdk/commit/2e4da0ba9fe830be9e948883b7e47efc613bd97d)  [Vinay Guthal]

* [AppCheck] Bump Firebase App Check version to 19.2.0 (#8295)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8295) [commit](https://github.com/firebase/firebase-android-sdk/commit/20ec6fb21a0ed7528628681de83b8da3537f1a4a)  [Rodrigo Lazo]

## appcheck/firebase-appcheck-debug-testing

* Update changelogs and gradle file to enable javadoc generation (#8299)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8299) [commit](https://github.com/firebase/firebase-android-sdk/commit/2e4da0ba9fe830be9e948883b7e47efc613bd97d)  [Vinay Guthal]

* [AppCheck] Bump Firebase App Check version to 19.2.0 (#8295)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8295) [commit](https://github.com/firebase/firebase-android-sdk/commit/20ec6fb21a0ed7528628681de83b8da3537f1a4a)  [Rodrigo Lazo]

## appcheck/firebase-appcheck-playintegrity

* Update changelogs and gradle file to enable javadoc generation (#8299)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8299) [commit](https://github.com/firebase/firebase-android-sdk/commit/2e4da0ba9fe830be9e948883b7e47efc613bd97d)  [Vinay Guthal]

* [AppCheck] Bump Firebase App Check version to 19.2.0 (#8295)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8295) [commit](https://github.com/firebase/firebase-android-sdk/commit/20ec6fb21a0ed7528628681de83b8da3537f1a4a)  [Rodrigo Lazo]

## appcheck/firebase-appcheck-recaptcha

* [AppCheck] Bump recaptcha version to 19.0.0 (#8303)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8303) [commit](https://github.com/firebase/firebase-android-sdk/commit/6b2d69d5caa61be7b6f378ff4a84e0e0db48dc71)  [Rodrigo Lazo]

* Update changelogs and gradle file to enable javadoc generation (#8299)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8299) [commit](https://github.com/firebase/firebase-android-sdk/commit/2e4da0ba9fe830be9e948883b7e47efc613bd97d)  [Vinay Guthal]

* [AppCheck] Fix reCAPTCHA refdoc (#8297)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8297) [commit](https://github.com/firebase/firebase-android-sdk/commit/feaae645c1e868b2491ea481a1754df58ccd54ac)  [Vinay Guthal]


## SDKs with changes, but no changelogs
:firebase-common  
:firebase-crashlytics  
:firebase-crashlytics-ndk  
:firebase-sessions  
:ai-logic:firebase-ai