# Release Report
## firebase-common

* m184 mergeback (#8502)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8502) [commit](https://github.com/firebase/firebase-android-sdk/commit/4c212cd250d043a2d6f8ca4e237f3e30b26fe8d9)  [Google Open Source Bot]

## firebase-dataconnect

* dataconnect(test): docker-compose.yml: add postgrest and swagger-ui (#8520)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8520) [commit](https://github.com/firebase/firebase-android-sdk/commit/87ace8120b592d043fbe14b1988e45b086b1314b)  [Denver Coneybeare]

* dataconnect: ci: upgrade data connect emulator to 3.4.17 (was 3.4.16) and firebase-tools to 15.26.0 (was 15.25.1) (#8516)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8516) [commit](https://github.com/firebase/firebase-android-sdk/commit/10e00db36fc51f9038443d5c04d2cef7e7a89a4e)  [Denver Coneybeare]

* dataconnect(test): upgrade postgresql server used in CI from to 18.4 (#8514)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8514) [commit](https://github.com/firebase/firebase-android-sdk/commit/943217a7b8f319c89a3704233022b185c025cfb5)  [Denver Coneybeare]

* dataconnect(chore): various improvements to docker-compose.yml for running postgres (#8513)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8513) [commit](https://github.com/firebase/firebase-android-sdk/commit/94574b3fd60adac96977346551ae7a0a11c853ea)  [Denver Coneybeare]

* dataconnect: fix CHANGELOG entries incorrectly moved to 17.3.3 header by PR #8502 (m184 mergeback) (#8504)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8504) [commit](https://github.com/firebase/firebase-android-sdk/commit/4f740d0e72abb7f68fc3d120613bdabc4c1f265b)  [Denver Coneybeare]

* m184 mergeback (#8502)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8502) [commit](https://github.com/firebase/firebase-android-sdk/commit/4c212cd250d043a2d6f8ca4e237f3e30b26fe8d9)  [Google Open Source Bot]

* dataconnect(change): add "X-Firebase-Sqlconnect-Affinity" header for GSLB soft stickiness (#8499)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8499) [commit](https://github.com/firebase/firebase-android-sdk/commit/a149d4d201799a1fd8dfe5032a5176a9a3adeacb)  [Denver Coneybeare]

* dataconnect: ci: upgrade firebase-tools to 15.25.1 (was 15.24.0) (#8498)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8498) [commit](https://github.com/firebase/firebase-android-sdk/commit/259787ce9673e8edaef73ba7d8784db75771f0f3)  [Denver Coneybeare]

* dataconnect(change): merge "X-Client-Platform" header into "X-Client-Version" (#8495)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8495) [commit](https://github.com/firebase/firebase-android-sdk/commit/7bc5991db7e8a3b789e8bc9968418e4afea99179)  [Denver Coneybeare]

* dataconnect(change): Add 15 second grace before disconnecting a streaming connection with the backend (#8481)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8481) [commit](https://github.com/firebase/firebase-android-sdk/commit/1103e8961ef6994bf7be931396558239c9cd77a2)  [Denver Coneybeare]

* dataconnect(change): add "X-Client-Platform" and "X-Client-Version" headers (#8486)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8486) [commit](https://github.com/firebase/firebase-android-sdk/commit/cf716e09f38c1a7b0e4db386994fdad1cff4bf5d)  [Denver Coneybeare]

* Upgrade distributionUrl to gradle 8.14.5 in all gradle-wrapper.properties files, match the main build (#8472)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8472) [commit](https://github.com/firebase/firebase-android-sdk/commit/8482c84b8340b94dec8aa33e37301a8a1e7cbd5e)  [Denver Coneybeare]

* dataconnect(test): QuerySubscriptionImplUnitTest.kt: fix spurious failures in testNetworkConnectivityRestoration()  (#8471)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8471) [commit](https://github.com/firebase/firebase-android-sdk/commit/70e6717d606adcb57ce4ddad1b79808a6bae9579)  [Denver Coneybeare]

* dataconnect(test): ensure FirebaseDataConnect instances are closed unconditionally in tests using the new useSuspending() extension function (#8465)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8465) [commit](https://github.com/firebase/firebase-android-sdk/commit/00a1bef1ad9f641d5205eee91676bb56a65e089a)  [Denver Coneybeare]

## firebase-firestore

* m184 mergeback (#8502)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8502) [commit](https://github.com/firebase/firebase-android-sdk/commit/4c212cd250d043a2d6f8ca4e237f3e30b26fe8d9)  [Google Open Source Bot]

* [Release] update version to correct number (#8492)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8492) [commit](https://github.com/firebase/firebase-android-sdk/commit/a0b95c2f761025613ab73cfe089c06dccde6670f)  [Mila]

* test(firestore): add integration tests for large documents (#8367)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8367) [commit](https://github.com/firebase/firebase-android-sdk/commit/1b63d4a69eed2774ec33dd962c81ee9119ad44ab)  [Daniel La Rocque]

* feat(firestore): support retrieving documents up to 16MB over gRPC (#8363)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8363) [commit](https://github.com/firebase/firebase-android-sdk/commit/bb270a6c39a9f9146950f55d7f111e6d75ad151f)  [Daniel La Rocque]

## firebase-messaging

* FCM Copybara import fix. (#8523)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8523) [commit](https://github.com/firebase/firebase-android-sdk/commit/41b9fd58bc229119e9ae1ef1b75dc484f11d6d6f)  [Eldhose M Babu]

* Tap issue fixes while importing to G3  (#8521)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8521) [commit](https://github.com/firebase/firebase-android-sdk/commit/f550f17d6ec062f85bc2593e2c4fed7371ac1d6e)  [Eldhose M Babu]

* Handle FID_ALREADY_USED Error from FCM Backend (#8507)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8507) [commit](https://github.com/firebase/firebase-android-sdk/commit/90869aa13bcd4c118a286701373962afdd0fa299)  [Eldhose M Babu]

## firebase-messaging-directboot


## firebase-ml-modeldownloader

* m184 mergeback (#8502)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8502) [commit](https://github.com/firebase/firebase-android-sdk/commit/4c212cd250d043a2d6f8ca4e237f3e30b26fe8d9)  [Google Open Source Bot]

* [Release] update version to correct number (#8492)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8492) [commit](https://github.com/firebase/firebase-android-sdk/commit/a0b95c2f761025613ab73cfe089c06dccde6670f)  [Mila]

## ai-logic/firebase-ai

* [AI] support on-device structured output (#8395)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8395) [commit](https://github.com/firebase/firebase-android-sdk/commit/c62af76eb8e05191535d64329e16129be4a566ff)  [Mila]

* refactor(ai-logic): remove deprecated Imagen APIs (#8512)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8512) [commit](https://github.com/firebase/firebase-android-sdk/commit/ddd73a0af2a03c48db318ea6007740bca88e5be2)  [Rosário P. Fernandes]

* [AI] update AI logic docs URLs (#8518)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8518) [commit](https://github.com/firebase/firebase-android-sdk/commit/87b1e88faa80864690518eeca663396feee8cf0b)  [Mila]

* Adjust LiveSession.isClosed to handle closure more correctly (#8511)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8511) [commit](https://github.com/firebase/firebase-android-sdk/commit/53d5c8b72ce3b19b836fcbff9203c99e6ee608ff)  [emilypgoogle]

* [AI] correct FunctionResponsePart role to "user" (#8508)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8508) [commit](https://github.com/firebase/firebase-android-sdk/commit/cb1f82d1585bc0719933fa2b1d4824a172093edc)  [Mila]

* m184 mergeback (#8502)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8502) [commit](https://github.com/firebase/firebase-android-sdk/commit/4c212cd250d043a2d6f8ca4e237f3e30b26fe8d9)  [Google Open Source Bot]

* [Release] update version to correct number (#8492)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8492) [commit](https://github.com/firebase/firebase-android-sdk/commit/a0b95c2f761025613ab73cfe089c06dccde6670f)  [Mila]

* [AI] Update location docs and notes for Firebase AI (#8466)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8466) [commit](https://github.com/firebase/firebase-android-sdk/commit/668b0ca5e097ea1b99e204774a9fb860dc44bbe2)  [Rodrigo Lazo]

## ai-logic/firebase-ai-ksp-processor

* [AI] support on-device structured output (#8395)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8395) [commit](https://github.com/firebase/firebase-android-sdk/commit/c62af76eb8e05191535d64329e16129be4a566ff)  [Mila]

## ai-logic/firebase-ai-ondevice

* [AI] support on-device structured output (#8395)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8395) [commit](https://github.com/firebase/firebase-android-sdk/commit/c62af76eb8e05191535d64329e16129be4a566ff)  [Mila]

* m184 mergeback (#8502)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8502) [commit](https://github.com/firebase/firebase-android-sdk/commit/4c212cd250d043a2d6f8ca4e237f3e30b26fe8d9)  [Google Open Source Bot]

## ai-logic/firebase-ai-ondevice-interop

* [AI] support on-device structured output (#8395)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8395) [commit](https://github.com/firebase/firebase-android-sdk/commit/c62af76eb8e05191535d64329e16129be4a566ff)  [Mila]

## appcheck/firebase-appcheck

* m184 mergeback (#8502)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8502) [commit](https://github.com/firebase/firebase-android-sdk/commit/4c212cd250d043a2d6f8ca4e237f3e30b26fe8d9)  [Google Open Source Bot]

* [Infra] Update changelogs (#8463)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8463) [commit](https://github.com/firebase/firebase-android-sdk/commit/e2488ea79987b03721935064d33f99886d475820)  [Mila]

## appcheck/firebase-appcheck-debug

* chore(app_check): Update the app check debug token message (#8503)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8503) [commit](https://github.com/firebase/firebase-android-sdk/commit/285a0488fa6de3dc0163f09b265ad9f611891f61)  [Austin Benoit]

* m184 mergeback (#8502)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8502) [commit](https://github.com/firebase/firebase-android-sdk/commit/4c212cd250d043a2d6f8ca4e237f3e30b26fe8d9)  [Google Open Source Bot]

## appcheck/firebase-appcheck-debug-testing

* m184 mergeback (#8502)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8502) [commit](https://github.com/firebase/firebase-android-sdk/commit/4c212cd250d043a2d6f8ca4e237f3e30b26fe8d9)  [Google Open Source Bot]

## appcheck/firebase-appcheck-playintegrity

* m184 mergeback (#8502)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8502) [commit](https://github.com/firebase/firebase-android-sdk/commit/4c212cd250d043a2d6f8ca4e237f3e30b26fe8d9)  [Google Open Source Bot]

## appcheck/firebase-appcheck-recaptcha

* m184 mergeback (#8502)
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8502) [commit](https://github.com/firebase/firebase-android-sdk/commit/4c212cd250d043a2d6f8ca4e237f3e30b26fe8d9)  [Google Open Source Bot]


## SDKs with changes, but no changelogs
