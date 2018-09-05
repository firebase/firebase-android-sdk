# FirestoreProvider README

## Overview

This file defines the interface for the `FirestoreProvider` class. An
implementation of this interface is statically injected at build time. The
implementation *must* have the following name or else it will not be found:
`com.google.firebase.firestore.testutil.provider.FirestoreProvider`.

The provider enables the test suite to access closed-source, integration testing
components without strongly depending on those components. As such, there are
multiple implementations of the provider. The production provider is safe for
open-sourcing.

## Provider Locations

For simplicity, there is a subdirectory in testutil/ for each provider
implementation. The `hexa_provider` directory contains an implementation that
connects to a Hexa environment via the `HexaTestUtil` utility. This code has
been moved out of `IntegrationTestUtil.java`.

Other providers: - `nightly_provider`: Supports running tests against nightly. -
`prod_provider`: Shouldn't be used internally, but is ready for open-source.

## Provider Implementation

Providers are Java classes with the following form. They must have a public,
argumentless constructor.

```java
package com.google.firebase.firestore.testutil.provider;

public class FirestoreProvider {
  public String firestoreHost();

  public String projectId();
}
```

Note, `firestoreHost` should return the host name, or the host and port, if not
using port 443.
