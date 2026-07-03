# Release Report
## firebase-crashlytics

* Collect ProfilingManager OOM and Anomaly triggers. (#8343)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8343) [commit](https://github.com/firebase/firebase-android-sdk/commit/18ec3a6fbc16fd7067c925c189073684434a5647)  [Konstantin Mandrika]

* Adding check for requireBuildId using latest package nomenclature. (#8239)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8239) [commit](https://github.com/firebase/firebase-android-sdk/commit/ff39625ab53330d4c3d23dd343b6969d33e82e11)  [OOS93]

## firebase-crashlytics-ndk


## firebase-sessions


## firebase-dataconnect

* Add reference to go/firestore-android-sdk-keepalive in inline comments (#8370)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8370) [commit](https://github.com/firebase/firebase-android-sdk/commit/1b8e6202102f465661d487e322064978bbfe7bb4)  [Denver Coneybeare]

* dataconnect(test): QuerySubscriptionImplUnitTest: fix flaky failures due to incorrect cleanup ordering of flows (#8357)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8357) [commit](https://github.com/firebase/firebase-android-sdk/commit/ee470c63293d355d52a2d258b8b6b0b180839cd2)  [Denver Coneybeare]

* dataconnect(test): use 127.0.0.1 instead of localhost (#8365)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8365) [commit](https://github.com/firebase/firebase-android-sdk/commit/e8e88c10f8074e62ea8fbe58c2928126bda0f35c)  [Denver Coneybeare]

* dataconnect: ci: upgrade data connect emulator to 3.4.14 (was 3.4.13) and firebase-tools to 15.22.2 (was 15.22.1) (#8360)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8360) [commit](https://github.com/firebase/firebase-android-sdk/commit/85187766d54ae3098f43222951cef33f27762c62)  [Denver Coneybeare]

* dataconnect(test): fix resource leaks in DataConnectGrpcRPCsUnitTest.kt (#8359)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8359) [commit](https://github.com/firebase/firebase-android-sdk/commit/990367e939cb59c44c7d5ce09a46bc191916adc9)  [Denver Coneybeare]

* dataconnect(change): add core/generated SDK info to request headers of realtime query subscriptions (#8356)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8356) [commit](https://github.com/firebase/firebase-android-sdk/commit/3ef69c9682e3592d04c3207165c6edbf6943ff04)  [Denver Coneybeare]

* dataconnect(test): AuthIntegrationTest.kt: add tests for resubscribe after FirebaseUserChangedException (#8353)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8353) [commit](https://github.com/firebase/firebase-android-sdk/commit/6c522f269858405df0ee14ed983269134c4d766b)  [Denver Coneybeare]

* dataconnect(test): QuerySubscriptionImplUnitTest.kt add tests for UNAUTHENTICATED error (#8351)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8351) [commit](https://github.com/firebase/firebase-android-sdk/commit/118ac59aa801a40daf2cb4855a84d12c8445b367)  [Denver Coneybeare]

* dataconnect(fix): Refresh expired Auth and App Check tokens in realtime query subscriptions (#8346)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8346) [commit](https://github.com/firebase/firebase-android-sdk/commit/d97ea6a8b2bda0b28103ef054318817794650e7f)  [Denver Coneybeare]

* dataconnect: ci: upgrade data connect emulator to 3.4.13 (was 3.4.11) and firebase-tools to 15.22.1 (was 15.20.0) (#8344)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8344) [commit](https://github.com/firebase/firebase-android-sdk/commit/97a669aba06096b76bb4a7269a6f1b8a811385e5)  [Denver Coneybeare]

* dataconnect(test): AuthIntegrationTest.kt: added realtime query subscription tests (#8341)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8341) [commit](https://github.com/firebase/firebase-android-sdk/commit/27d7c12ae05853e1f6bfa17f5960196337af14d1)  [Denver Coneybeare]

* TestUtils.kt: fix long-standing bug where the behavior of `ignoreCase` was inverted 🤦 (#8342)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8342) [commit](https://github.com/firebase/firebase-android-sdk/commit/c6ef1d5660de92ae810faba37ddb56fc35854a90)  [Denver Coneybeare]

* dataconnect(chore): upgrade gradleplugin dependencies `com.google.cloud:google-cloud-storage` to 2.69.0 and `io.github.z4kn4fein:semver` to 3.1.0 (#8335)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8335) [commit](https://github.com/firebase/firebase-android-sdk/commit/9f58de8f74775be241bde449b14cde544158fb96)  [Denver Coneybeare]

* dataconnect(chore): internal cleanup of logging from GrpcBidiFlow (#8340)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8340) [commit](https://github.com/firebase/firebase-android-sdk/commit/5c74949918ba5cc69d400b3a4ff2ae45858fa8bc)  [Denver Coneybeare]

* dataconnect(chore): dataconnect_emulator_upgrade/SKILL.md: fix extension of filename `update_versions_json.zsh` (was `update_versions_json.sh`) (#8337)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8337) [commit](https://github.com/firebase/firebase-android-sdk/commit/3bd0a3c9cc4a6c2a6e6ca921867bbf60d8d64417)  [Denver Coneybeare]

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

## firebase-firestore

* fix(firestore): fetch large documents in chunks from local SQLite cache (#8362)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8362) [commit](https://github.com/firebase/firebase-android-sdk/commit/f521ece7e8901b95af45776e457cc13be1cc375e)  [Daniel La Rocque]

* Add reference to go/firestore-android-sdk-keepalive in inline comments (#8370)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8370) [commit](https://github.com/firebase/firebase-android-sdk/commit/1b8e6202102f465661d487e322064978bbfe7bb4)  [Denver Coneybeare]

* fix(firestore): prevent OutOfMemory errors in debug logging of large values (#8361)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8361) [commit](https://github.com/firebase/firebase-android-sdk/commit/e70eaac41b3b171ce9af17bfe12775f55db7d10f)  [Daniel La Rocque]

## firebase-installations

* Migrate SharedPreferences to DataStorage for Installations (#8355)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8355) [commit](https://github.com/firebase/firebase-android-sdk/commit/e456ebedeef8bafa09557bc0b1cdaf3232409ae9)  [emilypgoogle]

## firebase-messaging

* FIS SDK dependency update (#8338)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8338) [commit](https://github.com/firebase/firebase-android-sdk/commit/9df2e50dff983bea79e7c5a36c4b072524918361)  [Eldhose M Babu]

* Version update to 25.1.0 (#8293)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8293) [commit](https://github.com/firebase/firebase-android-sdk/commit/77eb9511c69997c2741cdb4dc30a3d2722e00976)  [Eldhose M Babu]

## firebase-messaging-directboot

* FIS SDK dependency update (#8338)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8338) [commit](https://github.com/firebase/firebase-android-sdk/commit/9df2e50dff983bea79e7c5a36c4b072524918361)  [Eldhose M Babu]

* Version update to 25.1.0 (#8293)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8293) [commit](https://github.com/firebase/firebase-android-sdk/commit/77eb9511c69997c2741cdb4dc30a3d2722e00976)  [Eldhose M Babu]

* Add ChangeLog for release (AILogicOndeviceinterop and messaging directboot) (#8285)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8285) [commit](https://github.com/firebase/firebase-android-sdk/commit/df801c8c3945dc5a4ca9cd996b7c1f029e29d7d9)  [Vinay Guthal]

## firebase-perf

* Feature/jrc  8103.fix.api.34.heuristic (#8326)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8326) [commit](https://github.com/firebase/firebase-android-sdk/commit/95136b61a11170b0a0bd6411cce217f5b7bf06dc)  [Joseph Rodiz]

## ai-logic/firebase-ai

* [AI] expose isThought and thoughtSignature to public (#8352)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8352) [commit](https://github.com/firebase/firebase-android-sdk/commit/3e11df0c594633848cc533e7a7bc220b1e22a9ff)  [Mila]

* feat(ai-logic): add automatic function calling to LiveGenerativeModel (#8223)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8223) [commit](https://github.com/firebase/firebase-android-sdk/commit/0550af5eb00d4b77ed6d04a4320ac603136f1195)  [Rosário P. Fernandes]

* Ignore flakey daily AI Logic test (#8288)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8288) [commit](https://github.com/firebase/firebase-android-sdk/commit/56b438d028749f6010f1ddd6564c424043d6825b)  [emilypgoogle]

* Add java snippets to compilation tests (#8290)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8290) [commit](https://github.com/firebase/firebase-android-sdk/commit/e92bcc78fec1c5426a810a785774cf2cb40fdcf0)  [emilypgoogle]

* [Fix] remove Latin abbreviations (#8298)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8298) [commit](https://github.com/firebase/firebase-android-sdk/commit/13d159591fd7e6539fc25c59f871732cf8ef6ead)  [Mila]

* Add javadocs to recaptcha  (#8296)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8296) [commit](https://github.com/firebase/firebase-android-sdk/commit/e628cfccaf6c28f462bea04dae4834fdbec51c46)  [Vinay Guthal]

* [AI] Revert dependency change on appcheck-interop (#8286)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8286) [commit](https://github.com/firebase/firebase-android-sdk/commit/e35fb653ec8a3d4afadc9f7ff6c2553b23cc6ecc)  [Rodrigo Lazo]

## ai-logic/firebase-ai-ondevice-interop

* Add ChangeLog for release (AILogicOndeviceinterop and messaging directboot) (#8285)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8285) [commit](https://github.com/firebase/firebase-android-sdk/commit/df801c8c3945dc5a4ca9cd996b7c1f029e29d7d9)  [Vinay Guthal]

## appcheck/firebase-appcheck

* [AppCheck] Update App Check changelogs (#8382)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8382) [commit](https://github.com/firebase/firebase-android-sdk/commit/e18ef5599f3641bec711b32378c5296e3021f05a)  [Rodrigo Lazo]

* [AppCheck] Bump Firebase App Check version to 19.2.0 (#8295)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8295) [commit](https://github.com/firebase/firebase-android-sdk/commit/20ec6fb21a0ed7528628681de83b8da3537f1a4a)  [Rodrigo Lazo]

* Add Change log for appcheck (#8287)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8287) [commit](https://github.com/firebase/firebase-android-sdk/commit/5886da2426c281152628d4c46f844aafc4f9720f)  [Vinay Guthal]

## appcheck/firebase-appcheck-debug

* [AppCheck] Update App Check changelogs (#8382)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8382) [commit](https://github.com/firebase/firebase-android-sdk/commit/e18ef5599f3641bec711b32378c5296e3021f05a)  [Rodrigo Lazo]

* Update changelogs and gradle file to enable javadoc generation (#8299)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8299) [commit](https://github.com/firebase/firebase-android-sdk/commit/2e4da0ba9fe830be9e948883b7e47efc613bd97d)  [Vinay Guthal]

* [AppCheck] Bump Firebase App Check version to 19.2.0 (#8295)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8295) [commit](https://github.com/firebase/firebase-android-sdk/commit/20ec6fb21a0ed7528628681de83b8da3537f1a4a)  [Rodrigo Lazo]

## appcheck/firebase-appcheck-debug-testing

* [AppCheck] Update App Check changelogs (#8382)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8382) [commit](https://github.com/firebase/firebase-android-sdk/commit/e18ef5599f3641bec711b32378c5296e3021f05a)  [Rodrigo Lazo]

* Update changelogs and gradle file to enable javadoc generation (#8299)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8299) [commit](https://github.com/firebase/firebase-android-sdk/commit/2e4da0ba9fe830be9e948883b7e47efc613bd97d)  [Vinay Guthal]

* [AppCheck] Bump Firebase App Check version to 19.2.0 (#8295)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8295) [commit](https://github.com/firebase/firebase-android-sdk/commit/20ec6fb21a0ed7528628681de83b8da3537f1a4a)  [Rodrigo Lazo]

## appcheck/firebase-appcheck-playintegrity

* [AppCheck] Update App Check changelogs (#8382)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8382) [commit](https://github.com/firebase/firebase-android-sdk/commit/e18ef5599f3641bec711b32378c5296e3021f05a)  [Rodrigo Lazo]

* Update changelogs and gradle file to enable javadoc generation (#8299)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8299) [commit](https://github.com/firebase/firebase-android-sdk/commit/2e4da0ba9fe830be9e948883b7e47efc613bd97d)  [Vinay Guthal]

* [AppCheck] Bump Firebase App Check version to 19.2.0 (#8295)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8295) [commit](https://github.com/firebase/firebase-android-sdk/commit/20ec6fb21a0ed7528628681de83b8da3537f1a4a)  [Rodrigo Lazo]

## appcheck/firebase-appcheck-recaptcha

* [AppCheck] Remove file that shouldn't be part of the repo (#8345)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8345) [commit](https://github.com/firebase/firebase-android-sdk/commit/59ee4e9795f048719c05503a0ce3af3431c6ac6e)  [Rodrigo Lazo]

* [AppCheck] Log errors in RecaptchaAppCheckProvider (#8330)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8330) [commit](https://github.com/firebase/firebase-android-sdk/commit/ae94258c587a6d8b2eba83b0797fde47f68daf36)  [Rodrigo Lazo]

* [AppCheck] Bump recaptcha version to 19.0.0 (#8303)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8303) [commit](https://github.com/firebase/firebase-android-sdk/commit/6b2d69d5caa61be7b6f378ff4a84e0e0db48dc71)  [Rodrigo Lazo]

* Update changelogs and gradle file to enable javadoc generation (#8299)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8299) [commit](https://github.com/firebase/firebase-android-sdk/commit/2e4da0ba9fe830be9e948883b7e47efc613bd97d)  [Vinay Guthal]

* [AppCheck] Fix reCAPTCHA refdoc (#8297)   
  [pr](https://github.com/firebase/firebase-android-sdk/pull/8297) [commit](https://github.com/firebase/firebase-android-sdk/commit/feaae645c1e868b2491ea481a1754df58ccd54ac)  [Vinay Guthal]


## SDKs with changes, but no changelogs
:firebase-common