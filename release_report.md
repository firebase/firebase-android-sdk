# Release Report
## firebase-crashlytics
      
* Fix inefficiency in the setCustomKeys Kotlin extension (#6536)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6536) [commit](https://github.com/firebase/firebase-android-sdk/commit/0743d5a399d1fcef159b02fe203fa83fb4e147db)  [Matthew Robertson]

* [crashlytics] Specify the executor when calling onFailureListener (#6535)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6535) [commit](https://github.com/firebase/firebase-android-sdk/commit/c00de5a38247eafe6b920c31583707cd286d0a07)  [Rodrigo Lazo]

## firebase-crashlytics-ndk
      

## firebase-sessions
      
* Prevent whole session events from logging to logcat (#6551)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6551) [commit](https://github.com/firebase/firebase-android-sdk/commit/56282124ec274789d85274a710ebcf8d63012ff6)  [David Motsonashvili]

## firebase-crashlytics/ktx
      

## firebase-dataconnect
      
* dataconnect: remove unused extension function: DataConnectArb.pathSegmentType() (#6520)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6520) [commit](https://github.com/firebase/firebase-android-sdk/commit/866e6bd246fde0e54fdeb5f5d0c043632edf4161)  [Denver Coneybeare]

* dataconnect: tests: increase tag size from 20 to 50 characters, to reduce the chance of collisions (#6521)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6521) [commit](https://github.com/firebase/firebase-android-sdk/commit/4bc552f8f93871a459477842d4553b47324d1bcc)  [Denver Coneybeare]

* dataconnect: Add JavaTimeLocalDateSerializer and KotlinxDatetimeLocalDateSerializer (#6519)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6519) [commit](https://github.com/firebase/firebase-android-sdk/commit/39e7c927399e17902659bd5e6eba1821b4bb06f2)  [Denver Coneybeare]

* dataconnect: DateScalarIntegrationTest.kt rewritten (#6515)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6515) [commit](https://github.com/firebase/firebase-android-sdk/commit/9299c6da023c579f11f5885ec8a3b3ac8d0ce65f)  [Denver Coneybeare]

* dataconnect: `DateSerializer` removed, as it is superceded by `LocalDateSerializer` (#6513)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6513) [commit](https://github.com/firebase/firebase-android-sdk/commit/c87f10341090733ab8cfcb7997960544950c7f89)  [Denver Coneybeare]

* dataconnect: Remove obsolete "alpha" release notes from the kdoc comments of FirebaseDataConnect (#6514)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6514) [commit](https://github.com/firebase/firebase-android-sdk/commit/46cf77c01388c66591b3f332918e738a7416ad78)  [Denver Coneybeare]

* dataconnect: DataConnectExecutableVersions.json updated with version 1.7.0 (#6512)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6512) [commit](https://github.com/firebase/firebase-android-sdk/commit/b61a095863c71d7c69069ce2f30e223a64ee0ae2)  [Denver Coneybeare]

* dataconnect: internal convenience script updates (#6509)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6509) [commit](https://github.com/firebase/firebase-android-sdk/commit/848650b83f46f272b2b8aed8a5b5f504b2841d57)  [Denver Coneybeare]

* dataconnect: relax variance of the NullableReference type parameter to covariant (was invariant) (#6511)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6511) [commit](https://github.com/firebase/firebase-android-sdk/commit/d6bfc7b33336e6e71fed2aa21c29a7194ff733e5)  [Denver Coneybeare]

* dataconnect: LocalDateIntegrationTest.kt added (#6504)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6504) [commit](https://github.com/firebase/firebase-android-sdk/commit/c737e21b0718c10bc126bdf70d8b672307647954)  [Denver Coneybeare]

* dataconnect: DataConnectCredentialsTokenManager: initialize synchronously to fix race condition (#6448)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6448) [commit](https://github.com/firebase/firebase-android-sdk/commit/4e2dcd0b7518c5fb84d01036c2b196f3c1363473)  [Denver Coneybeare]

* dataconnect: relax LocalDateSerializer encoding and decoding, and add unit test coverage (#6451)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6451) [commit](https://github.com/firebase/firebase-android-sdk/commit/0eff5d3b0a6287d93a42bc32cac8abf5a6f41db8)  [Denver Coneybeare]

* dataconnect: DataConnectExecutableVersions.json updated with version 1.6.1 (#6460)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6460) [commit](https://github.com/firebase/firebase-android-sdk/commit/57e401c42ba1c9bb295eba41939e85680c637089)  [Denver Coneybeare]

* dataconnect: TestFirebaseAppFactory.kt: work around IllegalStateException in tests by adding a delay before calling FirebaseApp.delete() (#6447)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6447) [commit](https://github.com/firebase/firebase-android-sdk/commit/f20340a13fbe17d0a2ad3ee23a09d3a6570a68e4)  [Denver Coneybeare]

* dataconnect: GrpcMetadataIntegrationTest.kt: Fix race condition waiting for auth/appcheck to be ready (#6446)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6446) [commit](https://github.com/firebase/firebase-android-sdk/commit/5f6bc63616e4438d2c0386f4e34a440136ebfe97)  [Denver Coneybeare]

* dataconnect: add ktfmt support to gradle plugin (#6439)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6439) [commit](https://github.com/firebase/firebase-android-sdk/commit/6bee142400ff07250def71e2d20697f34cd1de8b)  [Denver Coneybeare]

## firebase-functions
      
* Address Functions compiler warnings (#6544)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6544) [commit](https://github.com/firebase/firebase-android-sdk/commit/bae6706249ef35d97d555acb2d2cddb34cf145a4)  [Rodrigo Lazo]

* Adjust Functions getters to be Kotlin source compatible (#6530)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6530) [commit](https://github.com/firebase/firebase-android-sdk/commit/04bfe558f2644e298f1f9ce0488a193985369788)  [emilypgoogle]

## firebase-functions/ktx
      

## firebase-messaging
      
* Update upstream methods to indicate they are now decommissioned. (#6508)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6508) [commit](https://github.com/firebase/firebase-android-sdk/commit/3d6e8b2c64d9489b7dc26b09b4867c191cf021d5)  [Greg Sakakihara]

## firebase-messaging-directboot
      
* Update upstream methods to indicate they are now decommissioned. (#6508)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6508) [commit](https://github.com/firebase/firebase-android-sdk/commit/3d6e8b2c64d9489b7dc26b09b4867c191cf021d5)  [Greg Sakakihara]

* Add directboot release notes (#6441)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6441) [commit](https://github.com/firebase/firebase-android-sdk/commit/928b9a97e03ec7deb73e3a4e5e42a7b8647dd96f)  [Vinay Guthal]

## firebase-messaging/ktx
      

## firebase-vertexai
      
* Minor bump vertex-ai version (#6553)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6553) [commit](https://github.com/firebase/firebase-android-sdk/commit/eeb23f73ee3828a22f127a4b04d11f7fdf83150e)  [Rodrigo Lazo]

* Add missing experimentalApi annotations to vertex tests (#6541)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6541) [commit](https://github.com/firebase/firebase-android-sdk/commit/800bc7a2c510c64c785333a1ba9fdeb363842269)  [Rodrigo Lazo]

* Add missing category to HarmCategory (#6502)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/6502) [commit](https://github.com/firebase/firebase-android-sdk/commit/28a227a172cf8377c39a24fbe9d34e757fd428a6)  [Rodrigo Lazo]


## SDKs with changes, but no changelogs
:firebase-common  
:firebase-common:ktx  
:firebase-components  
:firebase-firestore  
:firebase-firestore:ktx  
:firebase-perf  
:firebase-perf:ktx  
:firebase-components:firebase-dynamic-module-support  
:transport:transport-backend-cct  
:transport:transport-runtime