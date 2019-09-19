# Tooling to generate Kotlin documentation.

This module contains configuration for generating Kotlindoc that is hosted at
[firebase.github.io](https://firebase.github.io/firebase-android-sdk/reference/kotlin/firebase-ktx/).

To generate documentation for all "supported" SDKs(ones that have Kotlin extensions) run:

```bash
./gradlew :kotlindoc:dokka
```

To generate documentation for a subset of SDKs run:

```bash
./gradlew -PkotlindocProjects=":firebase-common,:firebase-firestore" :kotlindoc:dokka
```

The output will be located in `kotlindoc/build/dokka/html`.

To update the live reference, create a PR with the contents of the above directory into the `gh-pages` branch.