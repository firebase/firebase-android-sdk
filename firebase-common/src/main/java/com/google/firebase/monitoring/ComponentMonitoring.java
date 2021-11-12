package com.google.firebase.monitoring;

import androidx.annotation.Nullable;
import com.google.firebase.components.Component;
import com.google.firebase.components.ComponentFactory;
import com.google.firebase.components.ComponentRegistrar;
import com.google.firebase.components.ComponentRegistrarProcessor;
import com.google.firebase.platforminfo.LibraryVersionComponent;
import java.util.ArrayList;
import java.util.List;

/**
 * Instruments {@link ComponentFactory ComponentFactories} to trace their initialization.
 *
 * <p>{@link ComponentRegistrar}s usually have define than one {@link Component}, however usually
 * only one of them is of interest w.r.t startup time impact measurement. We also need to assign
 * stable meaningful names to initialization traces. For these reasons, this processor uses the
 * following heuristic to determine which {@link Component} to trace:
 *
 * <ol>
 *   <li>If the registrar has any {@link Component#getName() "named"} components, we instrument only
 *       them.
 *   <li>If the registrar has less than 2 components, we don't instrument it
 *   <li>If the registrar has exactly 1 {@link LibraryVersionComponent}, we use it as a name.
 *       Otherwise we don't instrument any component in the registrar.
 *   <li>Find the first non-{@link LibraryVersionComponent} component and instrument it with the
 *       name determined in step 3.
 *   <li>TODO(vkryachko): We should prefer eager components and not only rely on the main component
 *       being first.
 * </ol>
 */
public class ComponentMonitoring implements ComponentRegistrarProcessor {
  private final Tracer tracer;

  public ComponentMonitoring(Tracer tracer) {
    this.tracer = tracer;
  }

  @Override
  public List<Component<?>> processRegistrar(ComponentRegistrar registrar) {
    List<Component<?>> components = registrar.getComponents();
    if (anyHasExplicitName(components)) {
      return wrapWithNames(components);
    }
    if (components.size() < 2) {
      return components;
    }
    NamedComponent named = findTheOnlyLibraryName(components);
    if (named == null) {
      return components;
    }
    List<Component<?>> result = new ArrayList<>();
    boolean foundFirst = false;
    for (Component<?> component : components) {
      if (component != named.component) {
        if (!foundFirst) {
          foundFirst = true;

          @SuppressWarnings("unchecked")
          Component<Object> cmp = (Component<Object>) component;
          component = cmp.withFactory(wrap(named.name, cmp.getFactory()));
        }
        result.add(component);
      }
    }
    return result;
  }

  private List<Component<?>> wrapWithNames(List<Component<?>> components) {
    List<Component<?>> result = new ArrayList<>();
    for (Component<?> component : components) {
      if (component.getName() != null) {
        @SuppressWarnings("unchecked")
        Component<Object> cmp = (Component<Object>) component;
        result.add(cmp.withFactory(wrap(cmp.getName(), cmp.getFactory())));
      }
    }
    return result;
  }

  private <T> ComponentFactory<T> wrap(String name, ComponentFactory<T> factory) {
    return c -> {
      try (TraceHandle ignored = tracer.startTrace(name)) {
        return factory.create(c);
      }
    };
  }

  private static boolean anyHasExplicitName(List<Component<?>> components) {
    for (Component<?> component : components) {
      if (component.getName() != null) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private static NamedComponent findTheOnlyLibraryName(List<Component<?>> components) {
    NamedComponent result = null;
    for (Component<?> component : components) {
      String name = LibraryVersionComponent.getNameIfLibraryVersionComponent(component);
      if (name != null) {
        if (result != null) {
          // more than one library version found.
          return null;
        }
        result = new NamedComponent(component, name);
      }
    }
    return result;
  }

  static class NamedComponent {
    final Component<?> component;
    final String name;

    NamedComponent(Component<?> component, String name) {
      this.component = component;
      this.name = name;
    }
  }
}
