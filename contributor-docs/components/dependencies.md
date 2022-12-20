---
parent: Firebase Components
---

# Dependencies
{: .no_toc}

1. TOC
{:toc}

This page gives an overview of the different dependency types supported by the Components Framework.

## Background

As discussed in [Firebase Components]({{ site.baseurl }}{% link components/components.md %}), in order
for a `Component` to be injected with the things it needs to function, it has to declare its dependencies.
These dependencies are then made available and injected into `Components` at runtime.

Firebase Components provide different types of dependencies.

## Lazy vs Eager dependencies

When it comes to initialize a component, there are 2 ways of provide its dependencies.

### Direct Injection

With this type of injection, the component gets an instance of its dependency directly.

```kotlin
class MyComponent(private val dep : MyDep) {
  fun someMethod() {
    dep.use();
  }
}
```

As you can see above the component's dependency is passed by value directly,
which means that the dependency needs to be fully initialized before
it's handed off to the requesting component. As a result `MyComponent` may have to pay the cost
of initializing `MyDep` just to be created.

### Lazy/Provider Injection

With this type of injection, instead of getting an instance of the dependency directly, the dependency
is passed into the `Component` with the help of a `com.google.firebase.inject.Provider`

```java
public interface Provider<T> { T get(); }
```

```kotlin
class MyComponent(private val dep : Provider<MyDep>) {
  fun someMethod() {
    // Since all components are singletons, each call to
    // get() will return the same instance.
    dep.get().use();
  }
}
```

On the surface this does not look like a big change, but it has an important side effect. In order to create
an instance of `MyComponent`, we don't need to initialize `MyDep` anymore. Instead, initialization can be
delayed until `MyDep` is actually used.

It is also benefitial to use a `Provider` in the context of [Play's dynamic feature delivery](https://developer.android.com/guide/playcore/feature-delivery).
See [Dynamic Module Support]({{ site.baseurl }}{% link components/dynamic_modules.md %}) for more details.

## Required dependencies

This type of dependency informs the `ComponentRuntime` that a given `Component` cannot function without a dependency.
When the dependency is missing during initialization, `ComponentRuntime` will throw a `MissingDependencyException`.
This type of dependency is useful for built-in components that are always present like `Context`, `FirebaseApp`,
`FirebaseOptions`, [Executors]({{ site.baseurl }}{% link components/executors.md %}).

To declare a required dependency use one of the following in your `ComponentRegistrar`:

```java
  // Required directly injected dependency
  .add(Dependency.required(MyDep.class))
  // Required lazily injected dependency
  .add(Dependency.requiredProvider(MyOtherDep.class))
  .factory( c -> new MyComponent(c.get(MyDep.class), c.getProvider(MyOtherDep.class)))
  .build();
```

## Optional Dependencies

This type of dependencies is useful when your `Component` can operate normally when the dependency is not
available, but can have enhanced functionality when present. e.g. `Firestore` can work without `Auth` but
provides secure database access when `Auth` is present.

To declare an optional dependency use the following in your `ComponentRegistrar`:

```java
  .add(Dependency.optionalProvider(MyDep.class))
  .factory(c -> new MyComponent(c.getProvider(MyDep.class)))
  .build();
```

The provider will return `null` if the dependency is not present in the app.

{: .warning }
When the app uses [Play's dynamic feature delivery](https://developer.android.com/guide/playcore/feature-delivery),
`provider.get()` will return your dependency when it becomes available.  To support this use case, don't store references to the result of `provider.get()` calls.

See [Dynamic Module Support]({{ site.baseurl }}{% link components/dynamic_modules.md %}) for details

{: .warning }
See Deferred dependencies if you your dependency has a callback based API

## Deferred Dependencies

Useful for optional dependencies which have a listener-style API, i.e. the dependent component registers a
listener with the dependency and never calls it again (instead the dependency will call the registered listener).
A good example is `Firestore`'s use of `Auth`, where `Firestore` registers a token change listener to get
notified when a new token is available. The problem is that when `Firestore` initializes, `Auth` may not be
present in the app, and is instead part of a dynamic module that can be loaded at runtime on demand.

To solve this problem, Components have a notion of a `Deferred` dependency. A deferred is defined as follows:

```java
public interface Deferred<T> {
  interface DeferredHandler<T> {
    @DeferredApi
    void handle(Provider<T> provider);
  }

  void whenAvailable(DeferredHandler<T> handler);
}
```

To use it a component needs to call `Dependency.deferred(SomeType.class)`:

```kotlin
class MyComponent(deferred: Deferred<SomeType>) {
  init {
    deferred.whenAvailable { someType ->
      someType.registerListener(myListener)
    }
  }
}
```

See [Dynamic Module Support]({{ site.baseurl }}{% link components/dynamic_modules.md %}) for details

## Set Dependencies

The Components Framework allows registering components to be part of a set, such components are registered explicitly to be a part of a `Set<T>` as opposed to be a unique value of `T`:

```java
// Sdk 1
Component.intoSet(new SomeTypeImpl(), SomeType.class);
// Sdk 2
Component.intoSetBuilder(SomeType.class)
  .add(Dependency(SomeDep.class))
  .factory(c -> new SomeOtherImpl(c.get(SomeDep.class)))
  .build();
```

With the above setup each SDK contributes a value of `SomeType` into a `Set<SomeType>` which becomes available as a
`Set` dependency.

To consume such a set the interested `Component` needs to declare a special kind of dependency in one of 2 ways:

* `Dependency.setOf(SomeType.class)`, a dependency of type `Set<SomeType>`.
* `Dependency.setOfProvider(SomeType.class)`, a dependency of type `Provider<Set<SomeType>>`. The advantage of this
  is that the `Set` is not initialized until the first call to `provider.get()` at which point all elements of the
  set will get initialized.

{: .warning }
Similar to optional `Provider` dependencies, where an optional dependency can become available at runtime due to
[Play's dynamic feature delivery](https://developer.android.com/guide/playcore/feature-delivery),
`Set` dependencies can change at runtime by new elements getting added to the set.
So make sure to hold on to the original `Set` to be able to observe new values in it as they are added.

Example:

```kotlin
class MyClass(private val set1: Set<SomeType>, private val set2: Provider<Set<SomeOtherType>>)
```

```java
Component.builder(MyClass.class)
    .add(Dependency.setOf(SomeType.class))
    .add(Dependency.setOfProvider(SomeOtherType.class))
    .factory(c -> MyClass(c.setOf(SomeType.class), c.setOfProvider(SomeOtherType.class)))
    .build();
```
