## Kotlin serialization support for firebase firestore android SDK 

### To run this project, make sure you have:
1. [firebase firestore emulator to run the integration tests](https://firebase.google.com/docs/emulator-suite/connect_firestore)
2. Special effor has been made to downgrade Gradle 7.2 and Gradle Android Plugin 7.1.2, to [Gradle version to 6.9 and GAP version 4.2.2 to match the main android SDK](https://github.com/firebase/firebase-android-sdk/pull/3744). 
    - The reference table between Gradle and AGP can be found [here](https://developer.android.com/studio/releases/gradle-plugin).
    - Module level build.gradle need to have `implementation("androidx.multidex:multidex:2.0.1")` dependence,
    - Also need to have `multiDexEnabled true` in andorid defaultConfig

3. Kotlin version is currently 1.6.21, target is 1.5.32 (tried 1.4.32, but integration test will not run, will do more test if 1.4 is really necessary)

### Some Known issue about this implementation