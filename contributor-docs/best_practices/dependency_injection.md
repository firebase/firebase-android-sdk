---
parent: Best Practices
---

# Dependency Injection

While [Firebase Components]({{ site.baseurl }}{% components/components.md %}) provides basic
Dependency Injection capabilities for interop between Firebase SDKs, it's not ideal as a general purpose
DI framework or a couple of reasons, to name a few:

* It's verbose, i.e. requires manually specifying dependencies and constructing instances of components in Component
  definitions.
* It has a runtime cost, i.e. initialization time is linear in the number of Components present in the graph

As a result using [Firebase Components]({{ site.baseurl }}{% components/components.md %}) is appropriate only
for inter SDK injection and scoping instances per `FirebaseApp`.

On the other hand, manually instantiating SDKs is often tedious, errorprone, and often leads to code smells.
