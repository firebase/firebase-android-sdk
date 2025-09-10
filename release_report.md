# Release Report
## firebase-ai
      
* [Infra] Move all infra dependencies to the catalog (#7355)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7355) [commit](https://github.com/firebase/firebase-android-sdk/commit/acaacbd029524842558c60d0999d74c39af923a7)  [Rodrigo Lazo]

* Update change log to include the deprecation of the public constructors  (#7354)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7354) [commit](https://github.com/firebase/firebase-android-sdk/commit/ff21170a42a41930d997995888e6442cedc3a29e)  [Vinay Guthal]

* chore(firebaseai): Update changelog per release  (#7321)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7321) [commit](https://github.com/firebase/firebase-android-sdk/commit/2f93baa70bf90848c4753c963d651c3c2bd1e053)  [Daymon]

* Add support for Code Execution (#7265)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7265) [commit](https://github.com/firebase/firebase-android-sdk/commit/88f67741d7c783f527cbd9561ff0fbfb5a95d2eb)  [Vinay Guthal]

* add safety scores to image generation (#7322)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7322) [commit](https://github.com/firebase/firebase-android-sdk/commit/5822579b9357c85e3f779efa867c04df59e23411)  [David Motsonashvili]

* [AI] Ignore, and log, unknown parts. (#7333)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7333) [commit](https://github.com/firebase/firebase-android-sdk/commit/b986d2f411ac2a44861400da633fa96034397208)  [Rodrigo Lazo]

* Updated HarmCategory (#7324)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7324) [commit](https://github.com/firebase/firebase-android-sdk/commit/7b21bd6bc4746feb11c9cafac3f274942a9bee5b)  [David Motsonashvili]

* BIDI mini refactoring and new additions (#7267)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7267) [commit](https://github.com/firebase/firebase-android-sdk/commit/e35a424cf0183db83341c8b318fe45e19cd88bf9)  [Vinay Guthal]

* m169 fix more documentation (#7307)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7307) [commit](https://github.com/firebase/firebase-android-sdk/commit/f6feb928b00ed56e92bbea49a01f15405cfdfb7c)  [emilypgoogle]

* m169 doc changes (#7304)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7304) [commit](https://github.com/firebase/firebase-android-sdk/commit/4f338f1d8a9efeaa85faf5580359a1f0025ce972)  [emilypgoogle]

* [FAL] Update limited-use token docs (#7299)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7299) [commit](https://github.com/firebase/firebase-android-sdk/commit/949ecd58394f333dcca7062cdbb9e6a4784cd4fe)  [Daymon]

## firebase-dataconnect
      
* [Infra] Move all infra dependencies to the catalog (#7355)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7355) [commit](https://github.com/firebase/firebase-android-sdk/commit/acaacbd029524842558c60d0999d74c39af923a7)  [Rodrigo Lazo]

* dataconnect: replace `missingversions.py` with the `:firebase-dataconnet:updateJson` gradle task (#7345)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7345) [commit](https://github.com/firebase/firebase-android-sdk/commit/b5ad0db8e37fe3e97dc1b2c8a4d4dbf966cdce5f)  [Denver Coneybeare]

* dataconnect: fix some gradle configuration warnings, like targetSdk and kotlinOptions being deprecated (#7332)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7332) [commit](https://github.com/firebase/firebase-android-sdk/commit/db3bd7a24f294ffd86a333d32a0674cb04b70d31)  [Denver Coneybeare]

* Add Kotlindoc-only publishing and enable for Dataconnect (#7330)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7330) [commit](https://github.com/firebase/firebase-android-sdk/commit/c9657450c936506c01d90553c549f5083307b42d)  [emilypgoogle]

* dataconnect: CHANGELOG.md: format via spotlessApply (#7343)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7343) [commit](https://github.com/firebase/firebase-android-sdk/commit/3735e28f00818b45da96c0c02752af4e5906aa71)  [Denver Coneybeare]

* dataconnect: CHANGELOG.md: add PR link for "Ignore unknown fields in response data" entry (#7342)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7342) [commit](https://github.com/firebase/firebase-android-sdk/commit/b5075a2aece8b362e27280a1076d02ecaf272bfb)  [Denver Coneybeare]

* dataconnect: fix typo 'of of' in kdoc comments (#7331)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7331) [commit](https://github.com/firebase/firebase-android-sdk/commit/88f4327fb40b828955a9187b6a1afebf45cf5bff)  [Denver Coneybeare]

* dataconnect: upgrade data connect emulator to 2.12.0 (#7311)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7311) [commit](https://github.com/firebase/firebase-android-sdk/commit/9babb035534ad93c2b635880cc15badb99b6f63f)  [Denver Coneybeare]

* dataconnect: ignore unknown keys in response data instead of throwing an exception (#7314)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7314) [commit](https://github.com/firebase/firebase-android-sdk/commit/baa0456e4ecc9f839b805cd8292d309f9fdaaa82)  [Denver Coneybeare]

## firebase-firestore
      
* [Infra] Move all infra dependencies to the catalog (#7355)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7355) [commit](https://github.com/firebase/firebase-android-sdk/commit/acaacbd029524842558c60d0999d74c39af923a7)  [Rodrigo Lazo]

* firestore: fix slow queries when a collection has many NO_DOCUMENT tombstones (#7301)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7301) [commit](https://github.com/firebase/firebase-android-sdk/commit/5c7a9937cb4131c82f842f38947bf4a35e37f6d6)  [Denver Coneybeare]

## firebase-perf
      
* [Infra] Move all infra dependencies to the catalog (#7355)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7355) [commit](https://github.com/firebase/firebase-android-sdk/commit/acaacbd029524842558c60d0999d74c39af923a7)  [Rodrigo Lazo]

* Remove API checks that are always true/false. (#7356)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7356) [commit](https://github.com/firebase/firebase-android-sdk/commit/79324e18fdc2b06c63dd29cf5cda70fbd86da4d9)  [Tejas Deshpande]

* Refactor background start detection in app start traces (#7281)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7281) [commit](https://github.com/firebase/firebase-android-sdk/commit/287d9623d3e470a6fc4330f2d18534b67eb81271)  [Tejas Deshpande]


## SDKs with changes, but no changelogs
:firebase-abt  
:firebase-appdistribution  
:firebase-appdistribution-api  
:firebase-common  
:firebase-components  
:firebase-config  
:firebase-crashlytics  
:firebase-crashlytics-ndk  
:firebase-sessions  
:firebase-database  
:firebase-datatransport  
:firebase-functions  
:firebase-inappmessaging  
:firebase-inappmessaging-display  
:firebase-installations  
:firebase-installations-interop  
:firebase-messaging  
:firebase-messaging-directboot  
:firebase-ml-modeldownloader  
:firebase-storage  
:appcheck:firebase-appcheck  
:appcheck:firebase-appcheck-debug  
:appcheck:firebase-appcheck-debug-testing  
:appcheck:firebase-appcheck-playintegrity  
:appcheck:firebase-appcheck-recaptchaenterprise