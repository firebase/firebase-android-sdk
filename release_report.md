# Release Report
## firebase-appdistribution

* Use String.format in cases where firebase-vendor plugin transforms hardcoded strings (#8155)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8155) [commit](https://github.com/firebase/firebase-android-sdk/commit/1dfa16a54c3ef4dedd934939ac0df97cb510aa9e)  [Lee Kellogg]

## firebase-appdistribution-api

* Update CHANGELOG.md for firebase-appdistribution-api (#8161)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8161) [commit](https://github.com/firebase/firebase-android-sdk/commit/b63eda267a05ea08a800bfcf587cfa08befd54e7)  [Lee Kellogg]

## firebase-dataconnect

* dataconnect(feat): realtime query subscriptions (#8186)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8186) [commit](https://github.com/firebase/firebase-android-sdk/commit/eea8178bfb9011f416dcbe4565b0c435af419224)  [Denver Coneybeare]

* dataconnect(chore): Refactor realtime queries to use SharedFlow features (#8184)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8184) [commit](https://github.com/firebase/firebase-android-sdk/commit/f5b1aba097e39536d8f598c1514ae43e3c2c57c0)  [Denver Coneybeare]

* dataconnect(chore): improve test_execute.zsh with auto-suffix fallback and robust package detection (#8183)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8183) [commit](https://github.com/firebase/firebase-android-sdk/commit/c786a429a21eb36a743665f67df298bb6fd57d55)  [Denver Coneybeare]

* dataconnect(chore): test_execute.zsh added, to easily run tests by name from the command line (#8180)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8180) [commit](https://github.com/firebase/firebase-android-sdk/commit/698a341993921eb976b2672c3150750637409754)  [Denver Coneybeare]

* dataconnect(chore): rewrite bidirectional gRPC connection to use a custom class rather than generated stubs (#8179)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8179) [commit](https://github.com/firebase/firebase-android-sdk/commit/83da1bb4b5744662ac1095599e90dc6c2321e989)  [Denver Coneybeare]

* dataconnect(chore): RealtimeQueryManager.kt added with basic logic to multiplex connection stream (#8158)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8158) [commit](https://github.com/firebase/firebase-android-sdk/commit/bb58ac10d6dbb1e3bad2d60280686a27e270caf4)  [Denver Coneybeare]

* dataconnect: ci: upgrade data connect emulator to 3.4.8 (was 3.4.7) and firebase-tools to 15.18.0 (was 15.17.0) (#8159)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8159) [commit](https://github.com/firebase/firebase-android-sdk/commit/711698a59be3466aeccd9917614a1053d98a74aa)  [Denver Coneybeare]

* dataconnect(test): TurbineUtils.kt: minor tweaks (#8156)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8156) [commit](https://github.com/firebase/firebase-android-sdk/commit/0807bfb7aeaeb6f1f72ab9df89cc8dc541e970f4)  [Denver Coneybeare]

* dataconnect(change): Replace `SecureRandom` with process-unique ID generation (#8154)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8154) [commit](https://github.com/firebase/firebase-android-sdk/commit/9f88524642a79d9be4ef7ed5799f5585ff0c8d19)  [Denver Coneybeare]

* dataconnect(chore): factor out repeated logic for creating CoroutineScope objects (#8151)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8151) [commit](https://github.com/firebase/firebase-android-sdk/commit/ef12004b57e8a866d8c6261a3c946bb39813207e)  [Denver Coneybeare]

* dataconnect(chore): wire up RealtimeQuerySubscriptionImpl to basic realtime subscription functionality (#8144)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8144) [commit](https://github.com/firebase/firebase-android-sdk/commit/c97fcf5e3c7babe02edbe31ed5ad987f96da870e)  [Denver Coneybeare]

* dataconnect(ci): downgrade fdc emulator version to 3.4.7 (was 3.4.9) (#8153)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8153) [commit](https://github.com/firebase/firebase-android-sdk/commit/f9817b0fecaf91b13ba8d1b48b6d831155b66760)  [Denver Coneybeare]

* dataconnect(chore): refactor variable encoding and data decoding logic into a single class (#8142)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8142) [commit](https://github.com/firebase/firebase-android-sdk/commit/a214af0c111d18f4102d68b00a6df1bf71ae4ec9)  [Denver Coneybeare]

* dataconnect(change): Added internal logic: DataConnectGrpcRPCs.connect() to open a bidi streaming connection for realtime query updates (#8141)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8141) [commit](https://github.com/firebase/firebase-android-sdk/commit/5981a99e1590d5137a4f79e432ab477d9ebdc296)  [Denver Coneybeare]

* dataconnect: fix CHANGELOG entries incorrectly moved to 17.2.2 header by PR #8124 (m180 mergeback) (#8139)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8139) [commit](https://github.com/firebase/firebase-android-sdk/commit/2e2d42e4e53a3b5959f98f6a32d0129e30005772)  [Denver Coneybeare]

* dataconnect(chore): env.zsh added (#8138)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8138) [commit](https://github.com/firebase/firebase-android-sdk/commit/ab34a82a4080819bfc7e8647a6e3ffaa45740fd2)  [Denver Coneybeare]

* dataconnect(test): DataConnectGrpcClientUnitTest.kt: fix flaky tests for retry on UNAUTHENTICATED (#8129)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8129) [commit](https://github.com/firebase/firebase-android-sdk/commit/ced2ffc2054b79ba5c4090e626b6718f4ebcd46f)  [Denver Coneybeare]

* dataconnect(chore): CoroutineUtils.kt added with function createSupervisorCoroutineScope() (#8133)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8133) [commit](https://github.com/firebase/firebase-android-sdk/commit/c6f15210aea00d4e0b3c81fba02959304fe8178a)  [Denver Coneybeare]

* dataconnect(test): ConnectRPCIntegrationTest.kt: fix flaky tests due to backend deduplication of "subscribe" requests (#8131)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8131) [commit](https://github.com/firebase/firebase-android-sdk/commit/52485045e1aa62f2799bc00805f99d7620598284)  [Denver Coneybeare]

* dataconnect(build): fix CopySharedWithAndroidTestFiles.kt to delete the output directory before writing to it (#8132)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8132) [commit](https://github.com/firebase/firebase-android-sdk/commit/e2f733d2f88a02093f63d039a5b4dbe3abfbba23)  [Denver Coneybeare]

* dataconnect(test): RandomSeedTestRule: add seeded constructor, and implementation cleanup (#8130)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8130) [commit](https://github.com/firebase/firebase-android-sdk/commit/d1dfe153ca6fbe76cf65e8f8f0bc05ee49760b96)  [Denver Coneybeare]

* dataconnect(chore): DataConnectGrpcRPCs.kt: take connectorResourceName as constructor parameter (#8127)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8127) [commit](https://github.com/firebase/firebase-android-sdk/commit/d4a1a621a47b40b98f7f83c3b7ff997a399c3945)  [Denver Coneybeare]

* dataconnect(chore): DataConnectGrpcRPCs.kt: fix wasteful conversion to Struct only used for debug logging (#8126)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8126) [commit](https://github.com/firebase/firebase-android-sdk/commit/7fdcceb1480f0ef9eb1a35b22028283f6443b716)  [Denver Coneybeare]

* dataconnect(test): ConnectRPCIntegrationTest.kt finished (#8113)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8113) [commit](https://github.com/firebase/firebase-android-sdk/commit/1525d2426c95cd5b1562d1c2f9253b18db1eea23)  [Denver Coneybeare]

* dataconnect: ci: upgrade data connect emulator to 3.4.9 (was 3.4.5) and firebase-tools to 15.17.0 (was 15.15.0) (#8106)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8106) [commit](https://github.com/firebase/firebase-android-sdk/commit/10761f6d2d4ff4899ec4cd117097904e0a590033)  [Denver Coneybeare]

* dataconnect(test): ConnectRPCIntegrationTest.kt: add some more tests (#8105)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8105) [commit](https://github.com/firebase/firebase-android-sdk/commit/b53a55c2fbdb3035268fbc0f5b23827cea6a2d0b)  [Denver Coneybeare]

* dataconnect(test): ConnectRPCIntegrationTest.kt: add more tests (#8102)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8102) [commit](https://github.com/firebase/firebase-android-sdk/commit/81d9a1c33c6de95088b65a8171513cae67ee8716)  [Denver Coneybeare]

* dataconnect: add gradle plugin that enables sharing code from src/test to src/androidTest (#8098)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8098) [commit](https://github.com/firebase/firebase-android-sdk/commit/a68cdf39328c09d567caeb37a8be66111d3b83a4)  [Denver Coneybeare]

* dataconnect(test): ConnectRPCIntegrationTest.kt added with its first test (#8097)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8097) [commit](https://github.com/firebase/firebase-android-sdk/commit/534ef3ba0fe980b116adce28e79a82229d557647)  [Denver Coneybeare]

* dataconnect: testing connector added for future realtime testing (#8095)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8095) [commit](https://github.com/firebase/firebase-android-sdk/commit/607382b5f058babcf9704579a81feead0595930b)  [Denver Coneybeare]

* dataconnect: emulator.zsh: fix run_command() function to actually _run_ the command (#8090)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8090) [commit](https://github.com/firebase/firebase-android-sdk/commit/b9c7771675810ca0dd538d46b49fa855f71de076)  [Denver Coneybeare]

* dataconnect(chore): convert helper bash scripts to zsh (#8084)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8084) [commit](https://github.com/firebase/firebase-android-sdk/commit/de76cecaef5872884e691338ddc057394c8426d1)  [Denver Coneybeare]

* dataconnect(chore): add connector_stream_service.proto to the build (#8081)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8081) [commit](https://github.com/firebase/firebase-android-sdk/commit/c663292e7b4f03939bccc33be88e4ce40c8dc6ed)  [Denver Coneybeare]

## ai-logic/firebase-ai

* Adjust AI Logic testing for model deprecation (#8140)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8140) [commit](https://github.com/firebase/firebase-android-sdk/commit/87bbe13a4ed116200838353a6c374d779c572a60)  [emilypgoogle]

* [ALF] Add index adjustment for UTF-8 indices (#8056)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8056) [commit](https://github.com/firebase/firebase-android-sdk/commit/ce2ceac7a6123f7a338193b2fdc08a17b7106188)  [emilypgoogle]

* [AI] Update KDoc links to Markdown style (#8093)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8093) [commit](https://github.com/firebase/firebase-android-sdk/commit/5ed3b92ee999036cfca02f3b8fe5380c8f4a5533)  [Rodrigo Lazo]

* Deprecate Template Imagen class and the methods in it (#8085)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8085) [commit](https://github.com/firebase/firebase-android-sdk/commit/d2950a5fd2544d0bc72fd48f045b917101285cba)  [Vinay Guthal]

* [AI] Update AI SDK metadata and changelog (#8092)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8092) [commit](https://github.com/firebase/firebase-android-sdk/commit/d735cfa0b974bc6a8e6f5aa3092db832453d155e)  [Rodrigo Lazo]

* [AI] Introduce OnDeviceExtension for GenerativeModel (#8086)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8086) [commit](https://github.com/firebase/firebase-android-sdk/commit/840b3f260c76f620c9af540aa4eb48c29ede9d10)  [Rodrigo Lazo]

* [Fix] debug testResumption (#8082)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8082) [commit](https://github.com/firebase/firebase-android-sdk/commit/490a8f53cecba175e064535b5c199af1192c2c07)  [Mila]


## SDKs with changes, but no changelogs
:firebase-crashlytics  
:firebase-crashlytics-ndk  
:firebase-sessions  
:firebase-firestore  
:ai-logic:firebase-ai-ondevice  
:ai-logic:firebase-ai-ondevice-interop  
:appcheck:firebase-appcheck-recaptchaenterprise