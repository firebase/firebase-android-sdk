### Firebase App Test Rule

This module encapsulates the FirebaseAppRule shared by the test configurations of firebase-common and firebase-storage.

There does not seem to be a simpler way to share test file other than vendoring it into each module.

Suggestions in [this](https://discuss.gradle.org/t/how-do-i-declare-a-dependency-on-a-modules-test-code/7172/8) post do not work since the android gradle's sourcesets no longer generated individual jars.

