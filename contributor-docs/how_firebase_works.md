---
nav_order: 3
---

# How Firebase Works

## Background

### Eager Initialization

One of the biggest strengths for Firebase clients is the ease of integration. In a common case, a developer has very few things to do to integrate with Firebase. There is no need to initialize/configure Firebase at runtime. Firebase automatically initializes at application start and begins providing value to developers. A few notable examples:

* `Analytics` automatically tracks app events
* `Firebase Performance` automatically tracks app startup time, all network requests and screen performance
* `Crashlytics` automatically captures all application crashes, ANRs and non-fatals

This feature makes onboarding and adoption very simple. However, comes with the great responsibility of keeping the application snappy. We shouldn't slow down application startup for 3p developers as it can stand in the way of user adoption of their application.

### Automatic Inter-Product Discovery

When present together in an application, Firebase products can detect each other and automatically provide additional functionality to the developer, e.g.:

* `Firestore` automatically detects `Auth` and `AppCheck` to protect read/write access to the database
* `Crashlytics` integrates with `Analytics`, when available, to provide additional insights into the application behavior and enables safe app rollouts

## FirebaseApp at the Core of Firebase

Regardless of what Firebase SDKs are present in the app, the main initialization point of Firebase is `FirebaseApp`. It acts as a container for all SDKs, manages their configuration, initialization and lifecycle.

### Initialization

`FirebaseApp` gets initialized with the help of `FirebaseApp#initializeApp()`. This happens [automatically at app startup](https://firebase.blog/posts/2016/12/how-does-firebase-initialize-on-android) or manually by the developer.

During initialization, `FirebaseApp` discovers all Firebase SDKs present in the app, determines the dependency graph between products(for inter-product functionality) and initializes `eager` products that need to start immediately, e.g. `Crashlytics` and `FirebasePerformance`.

### Firebase Configuration

`FirebaseApp` contains Firebase configuration for all products to use, namely `FirebaseOptions`, which tells Firebase which `Firebase` project to talk to, which real-time database to use, etc.

### Additional Services/Components

In addition to `FirebaseOptions`, `FirebaseApp` registers additional components that product SDKs can request via dependency injection. To name a few:

* `android.content.Context`(Application context)
* [Common Executors]({{ site.baseurl }}{% link components/executors.md %})
* `FirebaseOptions`
* Various internal components

## Discovery and Dependency Injection

There are multiple considerations that lead to the current design of how Firebase SDKs initialize.

1. Certain SDKs need to initialize at app startup.
2. SDKs have optional dependencies on other products that get enabled when the developer adds the dependency to their app.

To enable this functionality, Firebase uses a runtime discovery and dependency injection framework [firebase-components](https://github.com/firebase/firebase-android-sdk/tree/master/firebase-components).

To integrate with this framework SDKs register the components they provide via a `ComponentRegistrar` and declare any dependencies they need to initialize, e.g.

```java
public class MyRegistrar implements ComponentRegistrar {
  @Override
  public List<Component<?>> getComponents() {
    return Arrays.asList(
        // declare the component
        Component.builder(MyComponent.class)
            // declare dependencies
            .add(Dependency.required(Context.class))
            .add(Dependency.required(FirebaseOptions.class))
            .add(Dependency.optionalProvider(InternalAuthProvider.class))
            // let the runtime know how to create your component.
            .factory(
                diContainer ->
                    new MyComponent(
                        diContainer.get(Context.class),
                        diContainer.get(FirebaseOptions.class),
                        diContainer.get(InternalAuthProvider.class)))
            .build());
  }
}
```

This registrar is then registered in `AndroidManifest.xml` of the SDK and is used by `FirebaseApp` to discover all components and construct the dependency graph.

More details in [Firebase Components]({{ site.baseurl }}{% link components/components.md %}).
