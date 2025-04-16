# Release Report
## firebase-config
      
* [RC] Remove package declaration from testapp manifest (#6875)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6875) [commit](https://github.com/firebase/firebase-android-sdk/commit/5aff679ee3e0fa22c1ff7876f08eb9088acd41c3)  [Rodrigo Lazo]

* Disconnect from Remote Config real-time server when app is backgrounded. (#6816)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6816) [commit](https://github.com/firebase/firebase-android-sdk/commit/5081e7c978c4f9ff7d358f4b8713ef15a57c1058)  [Tushar Khandelwal]

## firebase-config/ktx
      

## firebase-crashlytics
      
* fix: avoid android.os.strictmode.UnbufferedIoViolation (resubmission of PR #6565) (#6822)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6822) [commit](https://github.com/firebase/firebase-android-sdk/commit/19c8c7f822c130698cf2db8e9ee3b9f11fa91b9e)  [Angel Leon]

## firebase-sessions
      
* Changes in the Session Test App to verify behaviour with Fireperf #no-changelog (#6809)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6809) [commit](https://github.com/firebase/firebase-android-sdk/commit/278e437a7dd02c9046186eda707a020e63e214d4)  [Tejas Deshpande]

## firebase-crashlytics-ndk
      
* Upgrade to Android ndk r27c and update Crashpad to latest commit (#6814)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6814) [commit](https://github.com/firebase/firebase-android-sdk/commit/cf2b7a86ff988ce9d37d7390520547f54a36381c)  [Matthew Robertson]

* Updated internal Crashpad version (#6797)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6797) [commit](https://github.com/firebase/firebase-android-sdk/commit/5ff9d9576741c6a8fbcdf0507d53da8b08a4f146)  [Matthew Robertson]

## firebase-crashlytics/ktx
      

## firebase-dataconnect
      
* dataconnect: AuthIntegrationTest.kt: add missing import for FirebaseDataConnectInternal (#6854)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6854) [commit](https://github.com/firebase/firebase-android-sdk/commit/210dc0279ee974fac677f2aa7cc50d26586aea7d)  [Denver Coneybeare]

* dataconnect: auth code internal cleanup (#6836)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6836) [commit](https://github.com/firebase/firebase-android-sdk/commit/dbf5d014ab08cf3d86b6e31d2650afad451fdb3c)  [Denver Coneybeare]

* dataconnect: fix two flaky tests due to their failure to wait for FirebaseAuth to be initialized (#6835)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6835) [commit](https://github.com/firebase/firebase-android-sdk/commit/f826b40e84d1d58e895fc882a5131de9a9859042)  [Denver Coneybeare]

* dataconnect: fix flaky test that ensures deserialize() throws IllegalArgumentException on invalid input (#6839)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6839) [commit](https://github.com/firebase/firebase-android-sdk/commit/8af38c9599656d022b701b2304537a47a7bf6f35)  [Denver Coneybeare]

* dataconnect: QuerySubscriptionIntegrationTest.kt: fix flaky test using backgroundScope (#6827)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6827) [commit](https://github.com/firebase/firebase-android-sdk/commit/75cdf963195c873151a6fe0ece181543c01cea5f)  [Denver Coneybeare]

* Update changelog file (#6825)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6825) [commit](https://github.com/firebase/firebase-android-sdk/commit/2cdac31dc2d9510c158596ebec4ce7ddba327eb5)  [Rodrigo Lazo]

## firebase-firestore
      
* fix: Improve efficiency of online/offline composite index tests. (#6868)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6868) [commit](https://github.com/firebase/firebase-android-sdk/commit/24fba9b55f2ba7c4a821bd2fa835cd1e0a97d8d4)  [Ehsan]

* fix: remove null value inclusion from `whereNotEqualTo` and `whereNotIn` filter results  (#6859)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6859) [commit](https://github.com/firebase/firebase-android-sdk/commit/edcea54ea570af828f31cf5f54e952ad6c32ff61)  [Mila]

* Improve the integration test coverage for online vs offline comparisons. (#6841)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6841) [commit](https://github.com/firebase/firebase-android-sdk/commit/70c8e89dbc6698346a612d926da8f6bf1ddf6797)  [Ehsan]

## firebase-firestore/ktx
      

## firebase-vertexai
      
* Refactor live bidi (#6870)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6870) [commit](https://github.com/firebase/firebase-android-sdk/commit/40b7637c6c4e246113b781ba5be3f1a7356678de)  [Daymon]

* Update LiveModelFutures to return LiveSessionFutures instead of LiveSession (#6834)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6834) [commit](https://github.com/firebase/firebase-android-sdk/commit/47e37b5802a1be2038bc392dadb9a88c4cec8dbe)  [Vinay Guthal]

* update javadocs (#6848)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6848) [commit](https://github.com/firebase/firebase-android-sdk/commit/33e989f2f162348a2b75c19cbc8ce962e3d14264)  [Vinay Guthal]

* upate release notes (#6837)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6837) [commit](https://github.com/firebase/firebase-android-sdk/commit/d7a56a1e56699b8ea0ccd8bb752b702be2b39cf1)  [Vinay Guthal]

* Add support for HarmBlockThreshold.OFF (#6843)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6843) [commit](https://github.com/firebase/firebase-android-sdk/commit/9e89b289be79a8d7892db08be16d77e6a81eea45)  [Daymon]

* use bytestream instead of bytearray (#6847)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6847) [commit](https://github.com/firebase/firebase-android-sdk/commit/6e3be7893e600014e855d8ca2b511c3681bbd7c2)  [Vinay Guthal]

* fix emulator listening to itself (#6823)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6823) [commit](https://github.com/firebase/firebase-android-sdk/commit/395e9dd51107bfb1057c220a847b6f420a250beb)  [Vinay Guthal]

* Bidirectional Streaming Android (#6759)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6759) [commit](https://github.com/firebase/firebase-android-sdk/commit/d2e72df5231cfe0a8067e139ce60aa108992972d)  [Vinay Guthal]

* Add basic Vertex Java compilation tests (#6810)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6810) [commit](https://github.com/firebase/firebase-android-sdk/commit/dbeecd4df3803d14e885b011e5998129c87cfd87)  [emilypgoogle]


## SDKs with changes, but no changelogs
:firebase-functions  
:firebase-functions:ktx  
:firebase-perf  
:firebase-perf:ktx