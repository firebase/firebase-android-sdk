// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.components;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Implementation of topological sort. */
class ComponentSorter {
  private static class ComponentNode {
    private final Component<?> component;
    private final Set<ComponentNode> dependencies = new HashSet<>();
    private final Set<ComponentNode> dependents = new HashSet<>();

    ComponentNode(Component<?> component) {
      this.component = component;
    }

    void addDependency(ComponentNode node) {
      dependencies.add(node);
    }

    void addDependent(ComponentNode node) {
      dependents.add(node);
    }

    Set<ComponentNode> getDependencies() {
      return dependencies;
    }

    void removeDependent(ComponentNode node) {
      dependents.remove(node);
    }

    Component<?> getComponent() {
      return component;
    }

    boolean isRoot() {
      return dependents.isEmpty();
    }

    boolean isLeaf() {
      return dependencies.isEmpty();
    }
  }

  /**
   * Given a list of components, returns a sorted permutation of it.
   *
   * @param components Components to sort.
   * @return Sorted list of components.
   * @throws IllegalArgumentException thrown if multiple components implement the same interface.
   * @throws DependencyCycleException thrown if a dependency cycle between components is detected.
   */
  static List<Component<?>> sorted(List<Component<?>> components) {
    Set<ComponentNode> graph = toGraph(components);
    Set<ComponentNode> roots = getRoots(graph);

    List<Component<?>> result = new ArrayList<>();
    while (!roots.isEmpty()) {
      ComponentNode node = roots.iterator().next();
      roots.remove(node);
      result.add(node.getComponent());

      for (ComponentNode dependent : node.getDependencies()) {
        dependent.removeDependent(node);
        if (dependent.isRoot()) {
          roots.add(dependent);
        }
      }
    }

    // If there is no dependency cycle in the graph, the size of the resulting component list will
    // be equal to the original list, meaning that we were able to sort all components.
    if (result.size() == components.size()) {
      Collections.reverse(result);
      return result;
    }

    // Otherwise there is a cycle.
    List<Component<?>> componentsInCycle = new ArrayList<>();
    for (ComponentNode node : graph) {
      if (!node.isRoot() && !node.isLeaf()) {
        componentsInCycle.add(node.getComponent());
      }
    }

    throw new DependencyCycleException(componentsInCycle);
  }

  private static Set<ComponentNode> toGraph(List<Component<?>> components) {
    Map<Class<?>, ComponentNode> componentIndex = new HashMap<>(components.size());
    for (Component<?> component : components) {
      ComponentNode node = new ComponentNode(component);
      for (Class<?> anInterface : component.getProvidedInterfaces()) {
        if (componentIndex.put(anInterface, node) != null) {
          throw new IllegalArgumentException(
              String.format("Multiple components provide %s.", anInterface));
        }
      }
    }

    for (ComponentNode component : componentIndex.values()) {
      for (Dependency dependency : component.getComponent().getDependencies()) {
        if (!dependency.isDirectInjection()) {
          continue;
        }

        ComponentNode depComponent = componentIndex.get(dependency.getInterface());
        // Missing dependencies are skipped for the purposes of the sort as there is no component to
        // sort.
        if (depComponent != null) {
          component.addDependency(depComponent);
          depComponent.addDependent(component);
        }
      }
    }

    return new HashSet<>(componentIndex.values());
  }

  private static Set<ComponentNode> getRoots(Set<ComponentNode> components) {

    Set<ComponentNode> roots = new HashSet<>();
    for (ComponentNode component : components) {
      if (component.isRoot()) {
        roots.add(component);
      }
    }
    return roots;
  }
}
