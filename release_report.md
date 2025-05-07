# Release Report
## firebase-ai
      
* Add headers to be able to track bidi usage  (#6939)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6939) [commit](https://github.com/firebase/firebase-android-sdk/commit/4c29e0b467d2bf1318714c75ff011b22995eea16)  [Vinay Guthal]

* [Ai] Fix test code to point to the right resource dir (#6932)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6932) [commit](https://github.com/firebase/firebase-android-sdk/commit/bbe3de1925e4f964854a0f30ef7392d64d4c70f9)  [Rodrigo Lazo]

* Fix AI builders for Java consumers (#6930)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6930) [commit](https://github.com/firebase/firebase-android-sdk/commit/24dd7c48441b684bd2e99f260a5d0d5eb9ba1ef2)  [emilypgoogle]

* [Ai] Add workaround for invalid SafetyRating from the backend. (#6925)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6925) [commit](https://github.com/firebase/firebase-android-sdk/commit/0a880cc7a35a5cdcd012eaf70c2b00f7c4503569)  [Rodrigo Lazo]

* Add Java VertexAI bidi compile tests (#6903)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6903) [commit](https://github.com/firebase/firebase-android-sdk/commit/4b12b337c1291de22fe35322c9575bdadbc74129)  [emilypgoogle]

* Align LiveSeverMessage related protos (#6910)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6910) [commit](https://github.com/firebase/firebase-android-sdk/commit/2b2388713889db915ab76d94f672350952e4be8d)  [Daymon]

* Fix AI test timeout (#6917)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6917) [commit](https://github.com/firebase/firebase-android-sdk/commit/7b7e11ea5eafbcda1fcaa8ab1752b7cfaf11e425)  [Daymon]

* Add ResponseModality support to GenerationConfig (#6921)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6921) [commit](https://github.com/firebase/firebase-android-sdk/commit/cbd963688b1e121cde93b6c02384d1b8dbdf4465)  [Daymon]

* Add inlineDataParts helper property (#6922)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6922) [commit](https://github.com/firebase/firebase-android-sdk/commit/a87935401e6cf0dececaaae977bfa44bda49b430)  [Daymon]

* [ai] Use .json for unary tests instead of .txt (#6916)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6916) [commit](https://github.com/firebase/firebase-android-sdk/commit/79248138c9320c127d34fb7197f24981441f3960)  [Rodrigo Lazo]

* Davidmotson.firebase ai (#6911)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6911) [commit](https://github.com/firebase/firebase-android-sdk/commit/7cbb80b9762cee4b3f1c934b83251fb8c9a189f5)  [David Motsonashvili]

## firebase-config
      
* Fix NetworkOnMainThreadException for API levels below 26 (#6940)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6940) [commit](https://github.com/firebase/firebase-android-sdk/commit/548dc2886be128260a2366cb5363d555124b7875)  [Tushar Khandelwal]

* [RC] Remove package declaration from testapp manifest (#6875)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6875) [commit](https://github.com/firebase/firebase-android-sdk/commit/5aff679ee3e0fa22c1ff7876f08eb9088acd41c3)  [Rodrigo Lazo]

* Disconnect from Remote Config real-time server when app is backgrounded. (#6816)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6816) [commit](https://github.com/firebase/firebase-android-sdk/commit/5081e7c978c4f9ff7d358f4b8713ef15a57c1058)  [Tushar Khandelwal]

## firebase-config/ktx
      

## firebase-crashlytics
      
* fix more strict mode violation (#6937)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6937) [commit](https://github.com/firebase/firebase-android-sdk/commit/f5ec0a6e1828c395946538c2baf71f55ea86cc8f)  [themiswang]

* fix: avoid android.os.strictmode.UnbufferedIoViolation (resubmission of PR #6565) (#6822)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6822) [commit](https://github.com/firebase/firebase-android-sdk/commit/19c8c7f822c130698cf2db8e9ee3b9f11fa91b9e)  [Angel Leon]

## firebase-crashlytics-ndk
      
* Upgrade to Android ndk r27c and update Crashpad to latest commit (#6814)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6814) [commit](https://github.com/firebase/firebase-android-sdk/commit/cf2b7a86ff988ce9d37d7390520547f54a36381c)  [Matthew Robertson]

* Updated internal Crashpad version (#6797)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6797) [commit](https://github.com/firebase/firebase-android-sdk/commit/5ff9d9576741c6a8fbcdf0507d53da8b08a4f146)  [Matthew Robertson]

## firebase-sessions
      
* Changes in the Session Test App to verify behaviour with Fireperf #no-changelog (#6809)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6809) [commit](https://github.com/firebase/firebase-android-sdk/commit/278e437a7dd02c9046186eda707a020e63e214d4)  [Tejas Deshpande]

## firebase-crashlytics/ktx
      

## firebase-dataconnect
      
* dataconnect: include relevant logcat snippets in github actions logs when androidTest tests fail (#6902)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6902) [commit](https://github.com/firebase/firebase-android-sdk/commit/88f50d5744a90a165c00329991d630a1a7071cd1)  [Denver Coneybeare]

* FirebaseDataConnectImpl.kt: use MutableStateFlow to store state, rather than mutexes (#6861)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6861) [commit](https://github.com/firebase/firebase-android-sdk/commit/534cc539ff919ad6078021832007cbec0ec1258b)  [Denver Coneybeare]

* dataconnect: use firebase-tools to launch fdc emulator instead of launching it directly (#6896)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6896) [commit](https://github.com/firebase/firebase-android-sdk/commit/fd6553969b374f325d1340698504ecbf3f58ecfa)  [Denver Coneybeare]

* dataconnect: create python script to post comments about scheduled runs (#6880)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6880) [commit](https://github.com/firebase/firebase-android-sdk/commit/bd2cb5f1d6cef61fd88845e5ca29b229188d3ad5)  [Denver Coneybeare]

* dataconnect: Improve usage of MutableStateFlow to improve readability (#6840)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6840) [commit](https://github.com/firebase/firebase-android-sdk/commit/51b4a1c3c03608ad40555113e1189b64dd12505c)  [Denver Coneybeare]

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
      
* Handle error when stream was cancelled prior to calling halfClose. (#6894)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6894) [commit](https://github.com/firebase/firebase-android-sdk/commit/fda3351722f16582024249bb7d336ca46f2d4068)  [Tom Andersen]

* fix: Improve efficiency of online/offline composite index tests. (#6868)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6868) [commit](https://github.com/firebase/firebase-android-sdk/commit/24fba9b55f2ba7c4a821bd2fa835cd1e0a97d8d4)  [Ehsan]

* fix: remove null value inclusion from `whereNotEqualTo` and `whereNotIn` filter results  (#6859)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6859) [commit](https://github.com/firebase/firebase-android-sdk/commit/edcea54ea570af828f31cf5f54e952ad6c32ff61)  [Mila]

* Improve the integration test coverage for online vs offline comparisons. (#6841)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6841) [commit](https://github.com/firebase/firebase-android-sdk/commit/70c8e89dbc6698346a612d926da8f6bf1ddf6797)  [Ehsan]

## firebase-firestore/ktx
      

## firebase-storage
      
* [Storage] Migrate from Robolectric.flushForegroundThreadScheduler to ShadowLooper.runToEndOfTasks (#6927)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6927) [commit](https://github.com/firebase/firebase-android-sdk/commit/4c4c7c93b08bb0ba78b3e99cb00a50469c6eab24)  [Rodrigo Lazo]

## firebase-storage/ktx
      

## firebase-vertexai
      
* Prepare VertexAI SDK for release (#6941)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6941) [commit](https://github.com/firebase/firebase-android-sdk/commit/7b1855d1b60dfa886bd5e54b7793c008dee361b5)  [Daymon]

* Add Java VertexAI bidi compile tests (#6903)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6903) [commit](https://github.com/firebase/firebase-android-sdk/commit/4b12b337c1291de22fe35322c9575bdadbc74129)  [emilypgoogle]

* Fix AI test timeout (#6917)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6917) [commit](https://github.com/firebase/firebase-android-sdk/commit/7b7e11ea5eafbcda1fcaa8ab1752b7cfaf11e425)  [Daymon]

* encodeBitmapToBase64Png -> encodeBitmapToBase64Jpeg (#6912)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6912) [commit](https://github.com/firebase/firebase-android-sdk/commit/12127e6ec6a5e0e1264e248c5ba7fe7e7711595f)  [David Motsonashvili]

* Migrate LiveContentResponse.Status to properties (#6906)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6906) [commit](https://github.com/firebase/firebase-android-sdk/commit/4fb4dfd197832035675b28320e09fb9105aa3639)  [Daymon]

* Refactor: Rename encodeBitmapToBase64Png to encodeBitmapToBase64Jpeg (#6905)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6905) [commit](https://github.com/firebase/firebase-android-sdk/commit/d568e844fc79853a8a6c5b1a3819ef27d150f597)  [Rodrigo Lazo]

* Enable multimodal response generation in android (#6901)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6901) [commit](https://github.com/firebase/firebase-android-sdk/commit/e9ef4799062b76b8a533c3ae0fe25d6d6b29a59d)  [Vinay Guthal]

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
:protolite-well-known-types