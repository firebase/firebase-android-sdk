# How Firebase Works

## Background

### Eager Initialization

One of the biggest strengths for Firebase clients is the ease of integration. In a common case, a developer has very few things to do to integrate with Firebase. There is no need to initialize/configure Firebase at runtime. Firebase automatically initializes at application start and begins providing value to developers. A few notable examples:

* `Analytics` automatically tracks app events
* `Firebase Performance` automatically tracks app startup time, all network requests and screen performance
* `Crashlytics` automatically captures all application crashes, ANRs and non-fatals

The ability to initialize and start operating automatically is a core feature of Firebase that makes onboarding and adoption very simple. However with this advantage comes a great responsibility to keep the application snappy and not to slow down application startup for 3p developers as it can stand in the way of user adoption for the application.

### Automatic Inter-Product Discovery

In addition to initializing automatically, when present together in an application, Firebase products can detect each other’s presence and automatically provide additional functionality to the developer, e.g.:

* `Firestore` automatically detects `Auth` and `AppCheck` to protect read/write access to the database
* `Crashlytics` integrates with `Analytics`, when it’s available, to provide additional insights into the application behavior and enables safe app rollouts

## FirebaseApp at the Core of Firebase

Regardless of what Firebase SDKs are present in the app, the main initialization point of Firebase is `FirebaseApp` it acts as a container for all SDKs, manages their configuration, initialization and lifecycle.

### Initialization

`FirebaseApp` gets initialized with the help of `FirebaseApp#initializeApp()`, this happens [automatically at app startup](https://firebase.blog/posts/2016/12/how-does-firebase-initialize-on-android) or manually by the developer.

When that happens `FirebaseApp` discovers all Firebase SDKs present in the app, determines the dependency graph between products(for inter-product functionality) and initializes `eager` products that need to start immediately, e.g. `Crashlytics` and `FirebasePerformance`.

### Firebase Configuration

It contains Firebase configuration for all products to use, namely `FirebaseOptions`, which tells Firebase which `Firebase` project to talk to, which real-time database to use, etc.

### Additional Services/Components

In addition to `FirebaseOptions`, `FirebaseApp` registers additional components that product SDKs can request via dependency injection. To name a few:

* `android.content.Context`(Application context)
* [Common Executors](https://github.com/firebase/firebase-android-sdk/blob/master/docs/executors.md)
* `FirebaseOptions`
* Various internal components

## Discovery and Dependency Injection

TODO
