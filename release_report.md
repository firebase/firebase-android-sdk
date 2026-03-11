# Release Report
## firebase-dataconnect

* dataconnect(change): Implement FetchPolicy.SERVER_ONLY (#7887)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7887) [commit](https://github.com/firebase/firebase-android-sdk/commit/171b67c3ab4716085f65bf386de3e55a7ae2b64a)  [Denver Coneybeare]

* dataconnect(change): Change CACHE_ONLY behavior to return stale data instead of throwing (#7885)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7885) [commit](https://github.com/firebase/firebase-android-sdk/commit/0bb2eecf8a7b2d9827b0f71cccd45ee46d7f045a)  [Denver Coneybeare]

* dataconnect(test): QueryCachingIntegrationTest.kt: add some FetchPolicy coverage (#7878)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7878) [commit](https://github.com/firebase/firebase-android-sdk/commit/93eaa8fd3193185ba0aab686cfd16d123ed99687)  [Denver Coneybeare]

* dataconnect(test): Deduplicate logic in "EvenNumDigitsDistribution" classes and associated unit tests (#7873)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7873) [commit](https://github.com/firebase/firebase-android-sdk/commit/537bb20b6034d52515eb52fb3acb494326902356)  [Denver Coneybeare]

* dataconnect(change): Implement FetchPolicy.CACHE_ONLY (#7875)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7875) [commit](https://github.com/firebase/firebase-android-sdk/commit/93a18a08d053c98550091c8eaad19d5b6447d1f0)  [Denver Coneybeare]

* dataconnect: ci: upgrade data connect emulator to 3.2.1 (was 3.1.3) and firebase-tools to 15.8.0 (was 15.6.0) (#7871)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7871) [commit](https://github.com/firebase/firebase-android-sdk/commit/8c267de0a036ca0af7539c7c6beaa99e4d28a448)  [Denver Coneybeare]

* dataconnect(test): QueryCachingIntegrationTest.kt updated with normalized caching tests for a multi-value entity. (#7869)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7869) [commit](https://github.com/firebase/firebase-android-sdk/commit/b0b2a73546a77e60eeae37aeb85403fe85ec5817)  [Denver Coneybeare]

* dataconnect(test): IntWithEvenNumDigitsDistributionUnitTest: fix test that checked for both positive and negative values (#7865)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7865) [commit](https://github.com/firebase/firebase-android-sdk/commit/a2525c30a450ff4eb3d7160bc2236c7c4fb890f2)  [Denver Coneybeare]

* dataconnect(fix): Fix serialization of *nullable* AnyValue (#7864)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7864) [commit](https://github.com/firebase/firebase-android-sdk/commit/0f67e32ca554c8cd2c34e853c88f9b3d522fed34)  [Denver Coneybeare]

* dataconnect(test): QueryCachingIntegrationTest.kt updated with normalized caching tests for string lists and Any scalars (#7863)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7863) [commit](https://github.com/firebase/firebase-android-sdk/commit/c27fd43ae9650d755efb5e329d36043e2715605e)  [Denver Coneybeare]

* dataconnect(test): QueryCachingIntegrationTest.kt updated with normalized caching tests for standard scalars (#7859)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7859) [commit](https://github.com/firebase/firebase-android-sdk/commit/d1974d7823c489bb9dbb1dc95c3e3d8039623626)  [Denver Coneybeare]

* dataconnect(chore): Clean up CHANGELOG.md (#7860)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7860) [commit](https://github.com/firebase/firebase-android-sdk/commit/e510f5d2c4277b2fa3915a35fcbaed713a30d391)  [Denver Coneybeare]

* dataconnect(test): QueryCachingIntegrationTest.kt added (#7854)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7854) [commit](https://github.com/firebase/firebase-android-sdk/commit/f6b7f425f0254ffa4f58122d113706ea5b4a02da)  [Denver Coneybeare]

* dataconnect: maxAge implemented for offline caching (#7848)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7848) [commit](https://github.com/firebase/firebase-android-sdk/commit/185b4ce45a02d1a01c13663cd8cdfbed496519bb)  [Denver Coneybeare]

* dataconnect: wire up DataSource for caching and extract entity IDs from response extensions (#7814)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7814) [commit](https://github.com/firebase/firebase-android-sdk/commit/9abe6f3a151c06353c9732eae453de0e3a5af226)  [Denver Coneybeare]

* dataconnect: add back public apis for offline caching (#7833)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7833) [commit](https://github.com/firebase/firebase-android-sdk/commit/d6f859afd8d2d207ddc814f05dfede71f430bbab)  [Denver Coneybeare]

## firebase-firestore

* feat(firestore): Add array expressions (#7836)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7836) [commit](https://github.com/firebase/firebase-android-sdk/commit/ed4ef90e1b86349f87f39e526fe8ff121410b4d0)  [Mila]

* Fix performance issue in ObjectValue.equals for large documents (#7884)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7884) [commit](https://github.com/firebase/firebase-android-sdk/commit/2a81fadd0cb844afd0d4af67621d8d571aa8046e)  [wu-hui]

## ai-logic/firebase-ai

* m177 documentation changes (#7891)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7891) [commit](https://github.com/firebase/firebase-android-sdk/commit/7bc9a5fd97ef3ee186a87b79ca6b81a02f42aead)  [emilypgoogle]

* [AI] Use common `JSON` encoder in `LiveGenerativeModel` (#7880)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7880) [commit](https://github.com/firebase/firebase-android-sdk/commit/2db8764ca66e21857243a04a5f6bbe7ce6e0dcc6)  [Andrew Heard]

* [AI] Re-arrange ai SDKs (#7861)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7861) [commit](https://github.com/firebase/firebase-android-sdk/commit/7bb9d37fb5d15a5674c3aff0c2db2949a91a1609)  [Rodrigo Lazo]

## ai-logic/firebase-ai-ksp-processor

* [AI] Re-arrange ai SDKs (#7861)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7861) [commit](https://github.com/firebase/firebase-android-sdk/commit/7bb9d37fb5d15a5674c3aff0c2db2949a91a1609)  [Rodrigo Lazo]

## ai-logic/firebase-ai-ondevice

* [AI] Re-arrange ai SDKs (#7861)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7861) [commit](https://github.com/firebase/firebase-android-sdk/commit/7bb9d37fb5d15a5674c3aff0c2db2949a91a1609)  [Rodrigo Lazo]

## ai-logic/firebase-ai-ondevice-interop

* [AI] Re-arrange ai SDKs (#7861)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7861) [commit](https://github.com/firebase/firebase-android-sdk/commit/7bb9d37fb5d15a5674c3aff0c2db2949a91a1609)  [Rodrigo Lazo]


## SDKs with changes, but no changelogs
