package com.google.firebase.components;

import java.util.List;

/**
 * Provides the ability to customize/decorate components as they are requested by {@link
 * ComponentRuntime}.
 *
 * <p>This makes it possible to do validation, change/add dependencies, or customize components'
 * initialization logic by decorating their {@link ComponentFactory factories}.
 */
public interface ComponentRegistrarProcessor {

  /** Default "noop" processor. */
  ComponentRegistrarProcessor NOOP = ComponentRegistrar::getComponents;

  List<Component<?>> processRegistrar(ComponentRegistrar registrar);
}
