# Release Report
## firebase-appdistribution

* [Infra] Bump dagger version to 2.57.2 (#7907)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7907) [commit](https://github.com/firebase/firebase-android-sdk/commit/22bd97ed5271db4ce769a5064f6349fde0acefc6)  [Rodrigo Lazo]

## firebase-appdistribution-api

* [Infra] Bump dagger version to 2.57.2 (#7907)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7907) [commit](https://github.com/firebase/firebase-android-sdk/commit/22bd97ed5271db4ce769a5064f6349fde0acefc6)  [Rodrigo Lazo]

## firebase-crashlytics


## firebase-crashlytics-ndk

* Fix a runtime crash that could occur in minified native apps (#7906)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7906) [commit](https://github.com/firebase/firebase-android-sdk/commit/ada1257b1911992c0abc440d717a87f91edad9ba)  [Matthew Robertson]

## firebase-sessions

* [Infra] Bump dagger version to 2.57.2 (#7907)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7907) [commit](https://github.com/firebase/firebase-android-sdk/commit/22bd97ed5271db4ce769a5064f6349fde0acefc6)  [Rodrigo Lazo]

## firebase-dataconnect

* dataconnect: ci: upgrade data connect emulator to 3.3.1 (was 3.3.0) and firebase-tools to 15.12.0 (was 15.11.0) (#8000)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8000) [commit](https://github.com/firebase/firebase-android-sdk/commit/072e9705a64fbd4294c0ad6d0500bcdd467c9d75)  [Denver Coneybeare]

* dataconnect(change): Internal refactor to use immutable byte arrays when calculating SHA512 hashes (#7957)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7957) [commit](https://github.com/firebase/firebase-android-sdk/commit/34f7e47b7dc850bf0e1103b2d449bfca40623078)  [Denver Coneybeare]

* dataconnect(chore): Fix CHANGELOG.md misplaced entries caused by mergeback of M178 (#7956)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7956) [commit](https://github.com/firebase/firebase-android-sdk/commit/e139efe6c2a60c216464623aad00969a57e59b72)  [Denver Coneybeare]

* dataconnect: ci: upgrade data connect emulator to 3.3.0 (was 3.2.1) and firebase-tools to 15.11.0 (was 15.8.0) (#7936)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7936) [commit](https://github.com/firebase/firebase-android-sdk/commit/4029b93983c0f7ea4d411e0e633537423c45ea12)  [Denver Coneybeare]

* dataconnect(test): be more lax in the ChiSquareTest significance result assertion to reduce false positives in unit tests (#7937)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7937) [commit](https://github.com/firebase/firebase-android-sdk/commit/86fba0db2aa7d31ca199f158310b9b0d881c8b63)  [Denver Coneybeare]

* dataconnect(chore): dataconnect_set_cli.toml gemini command added (#7931)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7931) [commit](https://github.com/firebase/firebase-android-sdk/commit/920996826e37d54b438f3324501bad46b15c8d58)  [Denver Coneybeare]

* dataconnect(dev): add docker-compose.yml to replace bespoke bash scripts (#7918)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7918) [commit](https://github.com/firebase/firebase-android-sdk/commit/fa3c4a311fb2e2e210d0ff0bed305797787d81d3)  [Denver Coneybeare]

* dataconnect(dev): add .gemini/commands/dataconnect_emulator_upgrade.toml (#7919)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7919) [commit](https://github.com/firebase/firebase-android-sdk/commit/4b7c413c7e2993cdf603359299188ee573f1f89c)  [Denver Coneybeare]

* dataconnect(change): Use `SecureRandom` when generating internal operation IDs (#7910)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7910) [commit](https://github.com/firebase/firebase-android-sdk/commit/ca90e16c9b97674834926b341a683cfbf986d9d1)  [Denver Coneybeare]

* dataconnect(change): ensure exceptions aren't silently discarded when closing DataConnectGrpcRPCs (#7909)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7909) [commit](https://github.com/firebase/firebase-android-sdk/commit/c572c04af05356968032eee2d8c28a63109ce4d3)  [Denver Coneybeare]

* dataconnect(chore): gradleplugin: add support for specifying preview flags to the data connect cli (#7904)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7904) [commit](https://github.com/firebase/firebase-android-sdk/commit/5a6922518adbe90b4c2bfde40bb5e923f13d9808)  [Denver Coneybeare]

* dataconnect(docs): Add kdoc to CacheSettings.Storage STORAGE and PERSISTENT values (#7901)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7901) [commit](https://github.com/firebase/firebase-android-sdk/commit/460dc985daaf6978c2c71a1977e68243caf14873)  [Denver Coneybeare]

## firebase-firestore

* feat: Add the parent expression (#7999)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7999) [commit](https://github.com/firebase/firebase-android-sdk/commit/3e20194c4a5ed7f9222161b65571423953adbe56)  [Yvonne Pan]

* feat(firestore): add support for `isType` pipeline expression (#7985)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7985) [commit](https://github.com/firebase/firebase-android-sdk/commit/47a947ecc0c13b267dea084a0b5294af7252d70e)  [Daniel La Rocque]

* feat: Search stages enters public preview (#7949)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7949) [commit](https://github.com/firebase/firebase-android-sdk/commit/b77cdc30dd36658d659aa8870055a1fde67ded5d)  [Mark Duckworth]

* feat(firestore): add support for remaining string expressions (#7978)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7978) [commit](https://github.com/firebase/firebase-android-sdk/commit/1f5b6dd2cfde8b101a33fd0d8eb3ed96cd887af4)  [Daniel La Rocque]

* feat(firestore): Add ifNull and coalesce expressions (#7976)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7976) [commit](https://github.com/firebase/firebase-android-sdk/commit/654f761e7cef3d38780d94356e3ebac7e52a493e)  [Mila]

* feat(firestore): add support for remaining map expressions (#7987)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7987) [commit](https://github.com/firebase/firebase-android-sdk/commit/292f9d0433f90572af03f251dfce439292481269)  [Daniel La Rocque]

* test(firestore): run fixed nested fields tests on prod (#7994)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7994) [commit](https://github.com/firebase/firebase-android-sdk/commit/6f5d65b606823305e067ab6f7f67384a2745c081)  [Daniel La Rocque]

* feat: Add timestamp expressions (#7955)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7955) [commit](https://github.com/firebase/firebase-android-sdk/commit/679166154a6566e6ad4fe4bcc73eedd779cd8374)  [Yvonne Pan]

* feat: Add subquery support in pipeline (#7736)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7736) [commit](https://github.com/firebase/firebase-android-sdk/commit/afbe8b90ea01b34967811a4718da679eab815de7)  [cherylEnkidu]

* fix(firestore): Fix broken tests in nightly env (#7964)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7964) [commit](https://github.com/firebase/firebase-android-sdk/commit/df1afc13e8161a03afc10be2d79f8b9f393ac8fd)  [cherylEnkidu]

* chore(firestore): update `api.txt` for pipelines beta annotations (#7982)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7982) [commit](https://github.com/firebase/firebase-android-sdk/commit/49c75125d67aa716c3bb6d024494045b2ad2482e)  [Daniel La Rocque]

* Remove beta annotations for pipelines (#7974)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7974) [commit](https://github.com/firebase/firebase-android-sdk/commit/d7fe25196a5c9d6b4a66d93357f426aa143e7591)  [wu-hui]

* feat(feature):Add logical expressions (#7903)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7903) [commit](https://github.com/firebase/firebase-android-sdk/commit/3126a86959c71a0afb316a14db08802183cc9885)  [Mila]

* Add variable reference (#7926)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7926) [commit](https://github.com/firebase/firebase-android-sdk/commit/8a9beaf476108ee33a7787a627c4c8bd206928a5)  [cherylEnkidu]

* feat: add first, last, arrayAgg and arrayAggDistinct expressions (#7893)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7893) [commit](https://github.com/firebase/firebase-android-sdk/commit/34bf169022716bfd907f11d1bdc4e15f9de7cedd)  [Yvonne Pan]

* feat: add arithmetic expressions (#7886)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7886) [commit](https://github.com/firebase/firebase-android-sdk/commit/f4c17f873329a09c2c14ea4726f3938ccf8de399)  [Yvonne Pan]

## firebase-functions

* [Infra] Update Node.js engine and Firebase dependencies in tests (#7925)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7925) [commit](https://github.com/firebase/firebase-android-sdk/commit/bb76fff5c01ae60838ff929e88b666701dda7e38)  [Rodrigo Lazo]

* [Infra] Bump dagger version to 2.57.2 (#7907)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7907) [commit](https://github.com/firebase/firebase-android-sdk/commit/22bd97ed5271db4ce769a5064f6349fde0acefc6)  [Rodrigo Lazo]

## firebase-inappmessaging

* [Infra] Bump dagger version to 2.57.2 (#7907)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7907) [commit](https://github.com/firebase/firebase-android-sdk/commit/22bd97ed5271db4ce769a5064f6349fde0acefc6)  [Rodrigo Lazo]

## firebase-inappmessaging-display

* [Infra] Bump dagger version to 2.57.2 (#7907)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7907) [commit](https://github.com/firebase/firebase-android-sdk/commit/22bd97ed5271db4ce769a5064f6349fde0acefc6)  [Rodrigo Lazo]

## firebase-ml-modeldownloader

* [Infra] Bump dagger version to 2.57.2 (#7907)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7907) [commit](https://github.com/firebase/firebase-android-sdk/commit/22bd97ed5271db4ce769a5064f6349fde0acefc6)  [Rodrigo Lazo]

## firebase-perf

* [Infra] Bump dagger version to 2.57.2 (#7907)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7907) [commit](https://github.com/firebase/firebase-android-sdk/commit/22bd97ed5271db4ce769a5064f6349fde0acefc6)  [Rodrigo Lazo]

## ai-logic/firebase-ai

* [AI] Add tool-use support for Template AI models (#8004)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8004) [commit](https://github.com/firebase/firebase-android-sdk/commit/1cb95d39e37ee80e4de1dbdbf7b48fd7f9cf41ae)  [Rodrigo Lazo]

* add imagen deprecaton notice (#7988)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7988) [commit](https://github.com/firebase/firebase-android-sdk/commit/ebfa5e4b304187c6ee707edb3fbc3d62058135d1)  [Vinay Guthal]

* [AI] Add integration tests for template models (#8001)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8001) [commit](https://github.com/firebase/firebase-android-sdk/commit/3c7c94c49ba9056cc9ea14e67781d555462f0c7c)  [Rodrigo Lazo]

* [AI] Add TemplateChat for multi-turn template interactions (#7986)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7986) [commit](https://github.com/firebase/firebase-android-sdk/commit/4b76cbce67077ddad4d4eaa3e6e69d1cbc294fc0)  [Rodrigo Lazo]

* [AI] Add missing changelog entry for #7970 (#7977)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7977) [commit](https://github.com/firebase/firebase-android-sdk/commit/1994969fe3ef8dcce40ce08540654012c196c34f)  [Rodrigo Lazo]

* Revert "Implement Maps Grounding (#7950)" (#7980)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7980) [commit](https://github.com/firebase/firebase-android-sdk/commit/afadbf330737c80fc1a4f779485cec9958d7085d)  [emilypgoogle]

* [AI] Gracefully handle unknown LiveServerMessage types (#7975)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7975) [commit](https://github.com/firebase/firebase-android-sdk/commit/16f1dfc73b09d01a9631b6dd7e9b1051bede681d)  [Rodrigo Lazo]

* [AI] Implement toString/equals/hashCode for InferenceSource (#7970)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7970) [commit](https://github.com/firebase/firebase-android-sdk/commit/8bbbb18e50ed44897eed158077fb70de4e335d52)  [Rodrigo Lazo]

* [AI] Enable Ktor logging for debug builds in firebase-ai (#7962)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7962) [commit](https://github.com/firebase/firebase-android-sdk/commit/e859fd761a059ffa47b3fbc3665c79d7913391e6)  [Rodrigo Lazo]

* [AI] Use Ktor's HttpRequestTimeoutException for timeout handling (#7966)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7966) [commit](https://github.com/firebase/firebase-android-sdk/commit/326dc655fdbd1a24f81c66eec8f2de4edb3245c7)  [Rodrigo Lazo]

* Implement Maps Grounding (#7950)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7950) [commit](https://github.com/firebase/firebase-android-sdk/commit/16cb887a4c57f407f74cf3d1ba98672c96e74641)  [emilypgoogle]

* Clean up AI testing and run on all backends (#7912)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7912) [commit](https://github.com/firebase/firebase-android-sdk/commit/0feff8859a1d4d3fdc9ab2584a9e50afd85b9c62)  [emilypgoogle]

* [AI] Refactor: Improve KDoc formatting in ThinkingConfig (#7902)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7902) [commit](https://github.com/firebase/firebase-android-sdk/commit/5092ee06b4eb1a9799140d1af365022f1bfcbbd7)  [Rodrigo Lazo]

## transport/transport-api

* Call back from the firebase transport runtime into the event-emitter … (#7928)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7928) [commit](https://github.com/firebase/firebase-android-sdk/commit/82789982fc3f9e0f5a28e53b39ad2dbbf5aaf10f)  [Philip P. Moltmann]

## transport/transport-backend-cct

* Call back from the firebase transport runtime into the event-emitter … (#7928)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7928) [commit](https://github.com/firebase/firebase-android-sdk/commit/82789982fc3f9e0f5a28e53b39ad2dbbf5aaf10f)  [Philip P. Moltmann]

## transport/transport-runtime

* Add integration test for the uploader calling the PseudonymousIdUpdat… (#7963)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7963) [commit](https://github.com/firebase/firebase-android-sdk/commit/e9ad09850272a2abe543ca57b4aa4c43cc5e8ec8)  [Philip P. Moltmann]

* BackendResponse now takes two arguments not just 1 so include the other argument as well (#7961)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7961) [commit](https://github.com/firebase/firebase-android-sdk/commit/3c4c55fa2b92d6791efff0df5c5653f74ed7fd60)  [Vinay Guthal]

* Call back from the firebase transport runtime into the event-emitter … (#7928)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7928) [commit](https://github.com/firebase/firebase-android-sdk/commit/82789982fc3f9e0f5a28e53b39ad2dbbf5aaf10f)  [Philip P. Moltmann]

* [Infra] Fix `transport-runtime` test error (#7927)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7927) [commit](https://github.com/firebase/firebase-android-sdk/commit/4316ad09d28a49b2d377f4ec546d21b9392f4cac)  [Rodrigo Lazo]

* [Infra] Bump dagger version to 2.57.2 (#7907)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/7907) [commit](https://github.com/firebase/firebase-android-sdk/commit/22bd97ed5271db4ce769a5064f6349fde0acefc6)  [Rodrigo Lazo]


## SDKs with changes, but no changelogs
:firebase-abt  
:firebase-config  
:firebase-datatransport  
:firebase-messaging  
:firebase-messaging-directboot