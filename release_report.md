# Release Report
## firebase-common

* [AppCheck] Remove reCAPTCHA site key support  (#8457)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8457) [commit](https://github.com/firebase/firebase-android-sdk/commit/5d2efd147703e9cedce5ce528ce2879afac4eff3)  [Rodrigo Lazo]

* [Infra] Update test configurations for API level 24 (#8429)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8429) [commit](https://github.com/firebase/firebase-android-sdk/commit/5fb9e2747e8fb3dcce7f636ee7df1889617d168f)  [Mila]

## firebase-crashlytics

* [Infra] Update Android Gradle configurations (#8417)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8417) [commit](https://github.com/firebase/firebase-android-sdk/commit/010ed35cf9c26818f87b72557d74ae7f5dbfc5c8)  [Rodrigo Lazo]

* [Crashlytics] Address missing changelog entries and version bump (#8388)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8388) [commit](https://github.com/firebase/firebase-android-sdk/commit/5769a943903c08f9a13699debe7ec5a17b364b9b)  [Rodrigo Lazo]

## firebase-crashlytics-ndk

* [Infra] Update Android Gradle configurations (#8417)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8417) [commit](https://github.com/firebase/firebase-android-sdk/commit/010ed35cf9c26818f87b72557d74ae7f5dbfc5c8)  [Rodrigo Lazo]

* [Crashlytics] Address missing changelog entries and version bump (#8388)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8388) [commit](https://github.com/firebase/firebase-android-sdk/commit/5769a943903c08f9a13699debe7ec5a17b364b9b)  [Rodrigo Lazo]

## firebase-sessions

* [Crashlytics] Address missing changelog entries and version bump (#8388)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8388) [commit](https://github.com/firebase/firebase-android-sdk/commit/5769a943903c08f9a13699debe7ec5a17b364b9b)  [Rodrigo Lazo]

## firebase-dataconnect

* dataconnect(test): ensure FirebaseDataConnect instances are closed unconditionally in tests using the new useSuspending() extension function (#8465)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8465) [commit](https://github.com/firebase/firebase-android-sdk/commit/00a1bef1ad9f641d5205eee91676bb56a65e089a)  [Denver Coneybeare]

* dataconnect(test): fix test flake due to unconsumed flow events when cancelling flow collection (#8461)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8461) [commit](https://github.com/firebase/firebase-android-sdk/commit/73d72331a0146f76ecf7cbbb194b0c77228cf415)  [Denver Coneybeare]

* dataconnect(chore): Update exponential backoff parameters to match firestore (#8460)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8460) [commit](https://github.com/firebase/firebase-android-sdk/commit/deb8679249291c1807bc2a46d8329344d6e4134e)  [Denver Coneybeare]

* dataconnect(feat): add "jitter" to exponential backoff logic when reconnecting realtime streaming connections (#8456)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8456) [commit](https://github.com/firebase/firebase-android-sdk/commit/c06d32bef1c8a68c39e3fe63e107acc946222691)  [Denver Coneybeare]

* dataconnect(change): Exponential backoff in realtime query subscription reconnection now eagerly retries when network status changes (#8446)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8446) [commit](https://github.com/firebase/firebase-android-sdk/commit/66db6dbed86bb16e30208d41daf564b458390990)  [Denver Coneybeare]

* dataconnect(chore): test_execute.zsh: add support for specifying test name as 2nd command-line argument (#8442)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8442) [commit](https://github.com/firebase/firebase-android-sdk/commit/62f5437959ad82e33993d96a85a754c73d624dbf)  [Denver Coneybeare]

* dataconnect(chore): RetryBackoffCalculator.kt: add jitter support (not used yet though) (#8427)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8427) [commit](https://github.com/firebase/firebase-android-sdk/commit/b7521944522956636f5a4cfdecb2280710d8e3ef)  [Denver Coneybeare]

* dataconnect(chore): add logic to monitor network state changes for future use with exponential backoff logic (#8421)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8421) [commit](https://github.com/firebase/firebase-android-sdk/commit/23b627679a6d509e9d85d38a2c376944755c289d)  [Denver Coneybeare]

* dataconnect(test): Reduce flakiness of concurrency test in DataConnectAuthUnitTest.kt by increasing max threshold (#8431)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8431) [commit](https://github.com/firebase/firebase-android-sdk/commit/e345bf877ff71aeead460be90fedb2ad405b73e5)  [Denver Coneybeare]

* dataconnect: ci: upgrade data connect emulator to 3.4.16 (was 3.4.15) and firebase-tools to 15.24.0 (was 15.23.0) (#8430)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8430) [commit](https://github.com/firebase/firebase-android-sdk/commit/8dc2b0da5c92425d8b0c9ed69b94d4d8aaeea2f6)  [Denver Coneybeare]

* dataconnect(test): Use LongAdder to count concurrent invocations instead of AtomicInteger (#8423)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8423) [commit](https://github.com/firebase/firebase-android-sdk/commit/cbbcbb8d525f1ea42d76f18b8daeb2a699331cca)  [Denver Coneybeare]

* dataconnect(chore): readability improvements to DataConnectBidiConnectStream.kt to initialize physicalConnectionFlow and logicalConnectionFlow separately (#8425)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8425) [commit](https://github.com/firebase/firebase-android-sdk/commit/c9978f8ca68348c589822a58ce9755528a2c1cd8)  [Denver Coneybeare]

* dataconnect(test): fix memory leak in mocked objects and OOM during tests (#8412)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8412) [commit](https://github.com/firebase/firebase-android-sdk/commit/2ea2b507f8dcf00f3530a617e6fa72656356937f)  [Denver Coneybeare]

* dataconnect(chore): migrate to SQL_CONNECT_PREVIEW when running the data connect cli (#8415)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8415) [commit](https://github.com/firebase/firebase-android-sdk/commit/ca4409d0d905785b61a567eba98d42421474aaa8)  [Denver Coneybeare]

* dataconnect: ci: upgrade data connect emulator to 3.4.15 (was 3.4.14) and firebase-tools to 15.23.0 (was 15.22.2) (#8414)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8414) [commit](https://github.com/firebase/firebase-android-sdk/commit/739e19060f761a46bc1bd96b0225bd300df4f7d8)  [Denver Coneybeare]

* dataconnect(test): Fix thread thrashing by using Dispatchers.IO in tests (#8391)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8391) [commit](https://github.com/firebase/firebase-android-sdk/commit/e2aaa50e35ec14970ae49aa0b0d9bce94a3b443d)  [Denver Coneybeare]

* dataconnect(change): add exponential backoff in realtime query subscriptions upon disconnecting from the server (#8381)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8381) [commit](https://github.com/firebase/firebase-android-sdk/commit/cd9837558333bad62dc18952f276d15a55d71657)  [Denver Coneybeare]

## firebase-firestore

* feat(firestore): support retrieving documents up to 16MB over gRPC (#8363)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8363) [commit](https://github.com/firebase/firebase-android-sdk/commit/bb270a6c39a9f9146950f55d7f111e6d75ad151f)  [Daniel La Rocque]

* [Infra] Update Android Gradle configurations (#8417)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8417) [commit](https://github.com/firebase/firebase-android-sdk/commit/010ed35cf9c26818f87b72557d74ae7f5dbfc5c8)  [Rodrigo Lazo]

* feat(firestore): support configurable gRPC flow control window size (#8364)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8364) [commit](https://github.com/firebase/firebase-android-sdk/commit/1493baa2798519f45afa2b3a879eb4a8031717d1)  [Daniel La Rocque]

## firebase-messaging

* [Infra] Update Android Gradle configurations (#8417)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8417) [commit](https://github.com/firebase/firebase-android-sdk/commit/010ed35cf9c26818f87b72557d74ae7f5dbfc5c8)  [Rodrigo Lazo]

* FIS SDK dependency update (#8338)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8338) [commit](https://github.com/firebase/firebase-android-sdk/commit/ab395d7fa56cc990c981adc7deef7172a932e336)  [Eldhose M Babu]

## firebase-messaging-directboot

* [Infra] Update Android Gradle configurations (#8417)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8417) [commit](https://github.com/firebase/firebase-android-sdk/commit/010ed35cf9c26818f87b72557d74ae7f5dbfc5c8)  [Rodrigo Lazo]

* FIS SDK dependency update (#8338)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8338) [commit](https://github.com/firebase/firebase-android-sdk/commit/ab395d7fa56cc990c981adc7deef7172a932e336)  [Eldhose M Babu]

## firebase-ml-modeldownloader

* Deprecate Firebase ML Model Downloader (#8424)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8424) [commit](https://github.com/firebase/firebase-android-sdk/commit/654ef0774d4d272e28157c1b708753b0cedf9096)  [emilypgoogle]

* [Infra] Update Android Gradle configurations (#8417)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8417) [commit](https://github.com/firebase/firebase-android-sdk/commit/010ed35cf9c26818f87b72557d74ae7f5dbfc5c8)  [Rodrigo Lazo]

## ai-logic/firebase-ai

* [AI] Update location docs and notes for Firebase AI (#8466)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8466) [commit](https://github.com/firebase/firebase-android-sdk/commit/668b0ca5e097ea1b99e204774a9fb860dc44bbe2)  [Rodrigo Lazo]

* [AI] Unify the serialization name pattern (#8459)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8459) [commit](https://github.com/firebase/firebase-android-sdk/commit/0344328fa58138507bd019d1213f68af460e16c2)  [Mila]

* [AI] Implement RealtimeInputConfig and Manual Activity Signals for Live API (#8080)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8080) [commit](https://github.com/firebase/firebase-android-sdk/commit/66ec5cd566b2a39df670f4dbbf59af425fe83edd)  [Mila]

* Expose On-Device Model Name in GenerativeModel Extension (#8247)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8247) [commit](https://github.com/firebase/firebase-android-sdk/commit/2484c79d8bfba5a2b8ad74a1a682dbc5f9ffaaf1)  [Vinay Guthal]

* [AI] Introduce Agent Platform backend and deprecate Vertex AI  (#8437)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8437) [commit](https://github.com/firebase/firebase-android-sdk/commit/092d67d46c14200e14dd45e15302d358e02faffd)  [Rodrigo Lazo]

* [Fix] capitalize Grounding with Google Maps / Search (#8408)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8408) [commit](https://github.com/firebase/firebase-android-sdk/commit/a0f8e99e2076b52e7579a9b8b55049df94e26ce4)  [Mila]

* Update testing to Gemini 3 models (#8405)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8405) [commit](https://github.com/firebase/firebase-android-sdk/commit/3a7fa2ce595bd09cac590a1b50807f9ed05e7c5e)  [emilypgoogle]

## ai-logic/firebase-ai-ondevice

* Expose On-Device Model Name in GenerativeModel Extension (#8247)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8247) [commit](https://github.com/firebase/firebase-android-sdk/commit/2484c79d8bfba5a2b8ad74a1a682dbc5f9ffaaf1)  [Vinay Guthal]

* [Infra] Enable nested release notes headers (#8396)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8396) [commit](https://github.com/firebase/firebase-android-sdk/commit/5b4c5c8560e7cb8f5c04fbbaab8f6a60fc3036ac)  [Rodrigo Lazo]

## appcheck/firebase-appcheck

* [AppCheck] Remove reCAPTCHA site key support  (#8457)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8457) [commit](https://github.com/firebase/firebase-android-sdk/commit/5d2efd147703e9cedce5ce528ce2879afac4eff3)  [Rodrigo Lazo]

* [Appcheck] Log debug token on every DebugAppCheckProvider initialization (#8454)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8454) [commit](https://github.com/firebase/firebase-android-sdk/commit/98a5626f695d04f294c94c53396e6f8eb67adeec)  [Mila]

* [Infra] Update Android Gradle configurations (#8417)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8417) [commit](https://github.com/firebase/firebase-android-sdk/commit/010ed35cf9c26818f87b72557d74ae7f5dbfc5c8)  [Rodrigo Lazo]

* [AppCheck] Update App Check changelogs (#8382)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8382) [commit](https://github.com/firebase/firebase-android-sdk/commit/b1e39a06ff79a73b5e5be2b63092da78efbfd667)  [Rodrigo Lazo]

## appcheck/firebase-appcheck-debug

* [AppCheck] Remove reCAPTCHA site key support  (#8457)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8457) [commit](https://github.com/firebase/firebase-android-sdk/commit/5d2efd147703e9cedce5ce528ce2879afac4eff3)  [Rodrigo Lazo]

* [Appcheck] Log debug token on every DebugAppCheckProvider initialization (#8454)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8454) [commit](https://github.com/firebase/firebase-android-sdk/commit/98a5626f695d04f294c94c53396e6f8eb67adeec)  [Mila]

* [Infra] Update Android Gradle configurations (#8417)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8417) [commit](https://github.com/firebase/firebase-android-sdk/commit/010ed35cf9c26818f87b72557d74ae7f5dbfc5c8)  [Rodrigo Lazo]

* [Infra] Enable nested release notes headers (#8396)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8396) [commit](https://github.com/firebase/firebase-android-sdk/commit/5b4c5c8560e7cb8f5c04fbbaab8f6a60fc3036ac)  [Rodrigo Lazo]

* [AppCheck] Update App Check changelogs (#8382)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8382) [commit](https://github.com/firebase/firebase-android-sdk/commit/b1e39a06ff79a73b5e5be2b63092da78efbfd667)  [Rodrigo Lazo]

## appcheck/firebase-appcheck-debug-testing

* [AppCheck] Remove reCAPTCHA site key support  (#8457)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8457) [commit](https://github.com/firebase/firebase-android-sdk/commit/5d2efd147703e9cedce5ce528ce2879afac4eff3)  [Rodrigo Lazo]

* [Infra] Update Android Gradle configurations (#8417)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8417) [commit](https://github.com/firebase/firebase-android-sdk/commit/010ed35cf9c26818f87b72557d74ae7f5dbfc5c8)  [Rodrigo Lazo]

* [Infra] Enable nested release notes headers (#8396)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8396) [commit](https://github.com/firebase/firebase-android-sdk/commit/5b4c5c8560e7cb8f5c04fbbaab8f6a60fc3036ac)  [Rodrigo Lazo]

* [AppCheck] Update App Check changelogs (#8382)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8382) [commit](https://github.com/firebase/firebase-android-sdk/commit/b1e39a06ff79a73b5e5be2b63092da78efbfd667)  [Rodrigo Lazo]

## appcheck/firebase-appcheck-playintegrity

* [AppCheck] Remove reCAPTCHA site key support  (#8457)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8457) [commit](https://github.com/firebase/firebase-android-sdk/commit/5d2efd147703e9cedce5ce528ce2879afac4eff3)  [Rodrigo Lazo]

* [Infra] Update Android Gradle configurations (#8417)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8417) [commit](https://github.com/firebase/firebase-android-sdk/commit/010ed35cf9c26818f87b72557d74ae7f5dbfc5c8)  [Rodrigo Lazo]

* [AppCheck] Fix capitalization in Play Integrity release notes (#8398)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8398) [commit](https://github.com/firebase/firebase-android-sdk/commit/10a5155f234d11ac1031cc1db276126c5dcde788)  [Rodrigo Lazo]

* [Infra] Enable nested release notes headers (#8396)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8396) [commit](https://github.com/firebase/firebase-android-sdk/commit/5b4c5c8560e7cb8f5c04fbbaab8f6a60fc3036ac)  [Rodrigo Lazo]

* [AppCheck] Update App Check changelogs (#8382)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8382) [commit](https://github.com/firebase/firebase-android-sdk/commit/b1e39a06ff79a73b5e5be2b63092da78efbfd667)  [Rodrigo Lazo]

## appcheck/firebase-appcheck-recaptcha

* [AppCheck] Remove reCAPTCHA site key support  (#8457)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8457) [commit](https://github.com/firebase/firebase-android-sdk/commit/5d2efd147703e9cedce5ce528ce2879afac4eff3)  [Rodrigo Lazo]

* [Infra] Update Android Gradle configurations (#8417)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8417) [commit](https://github.com/firebase/firebase-android-sdk/commit/010ed35cf9c26818f87b72557d74ae7f5dbfc5c8)  [Rodrigo Lazo]

* [AppCheck] Update reCAPTCHA capitalization (#8399)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8399) [commit](https://github.com/firebase/firebase-android-sdk/commit/abbc2ceac7b98fce7938b7146f4315f7d39e936e)  [Rodrigo Lazo]

* [Infra] Enable nested release notes headers (#8396)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8396) [commit](https://github.com/firebase/firebase-android-sdk/commit/5b4c5c8560e7cb8f5c04fbbaab8f6a60fc3036ac)  [Rodrigo Lazo]


## SDKs with changes, but no changelogs
:firebase-appdistribution  
:firebase-appdistribution-api  
:firebase-config  
:firebase-datatransport  
:firebase-functions  
:firebase-inappmessaging  
:firebase-inappmessaging-display  
:firebase-installations  
:firebase-installations-interop  
:firebase-perf  
:firebase-storage  
:protolite-well-known-types  
:ai-logic:firebase-ai-ksp-processor  
:appcheck:firebase-appcheck-interop  
:encoders:firebase-decoders-json  
:encoders:firebase-encoders-json  
:encoders:firebase-encoders-reflective  
:transport:transport-api  
:transport:transport-backend-cct  
:transport:transport-runtime