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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Cycle detector for the {@link Component} dependency graph. */
class CycleDetector {
  private static class Dep {
    private final Class<?> anInterface;
    private final boolean set;

    private Dep(Class<?> anInterface, boolean set) {
      this.anInterface = anInterface;
      this.set = set;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof Dep) {
        Dep dep = (Dep) obj;
        return dep.anInterface.equals(anInterface) && dep.set == set;
      }
      return false;
    }

    @Override
    public int hashCode() {
      int h = 1000003;
      h ^= anInterface.hashCode();
      h *= 1000003;
      h ^= Boolean.valueOf(set).hashCode();
      return h;
    }
  }

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
   * Detect a dependency cycle among provided {@link Component}s.
   *
   * @param components Components to detect cycle between.
   * @throws IllegalArgumentException thrown if multiple components implement the same interface.
   * @throws DependencyCycleException thrown if a dependency cycle between components is detected.
   */
  static void detect(List<Component<?>> components) {
    Set<ComponentNode> graph = toGraph(components);
    Set<ComponentNode> roots = getRoots(graph);

    int numVisited = 0;
    while (!roots.isEmpty()) {
      ComponentNode node = roots.iterator().next();
      roots.remove(node);
      numVisited++;

      for (ComponentNode dependent : node.getDependencies()) {
        dependent.removeDependent(node);
        if (dependent.isRoot()) {
          roots.add(dependent);
        }
      }
    }

    // If there is no dependency cycle in the graph, the number of visited nodes will be equal to
    // the original list.
    if (numVisited == components.size()) {
      return;
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
    Map<Dep, Set<ComponentNode>> componentIndex = new HashMap<>(components.size());
    for (Component<?> component : components) {
      ComponentNode node = new ComponentNode(component);
      for (Class<?> anInterface : component.getProvidedInterfaces()) {
        Dep cmp = new Dep(anInterface, !component.isValue());
        if (!componentIndex.containsKey(cmp)) {
          componentIndex.put(cmp, new HashSet<>());
        }
        Set<ComponentNode> nodes = componentIndex.get(cmp);
        if (!nodes.isEmpty() && !cmp.set) {
          throw new IllegalArgumentException(
              String.format("Multiple components provide %s.", anInterface));
        }
        nodes.add(node);
      }
    }

    for (Set<ComponentNode> componentNodes : componentIndex.values()) {
      for (ComponentNode node : componentNodes) {
        for (Dependency dependency : node.getComponent().getDependencies()) {
          if (!dependency.isDirectInjection()) {
            continue;
          }

          Set<ComponentNode> depComponents =
              componentIndex.get(new Dep(dependency.getInterface(), dependency.isSet()));
          if (depComponents == null) {
            continue;
          }
          for (ComponentNode depComponent : depComponents) {
            node.addDependency(depComponent);
            depComponent.addDependent(node);
          }
        }
      }
    }

    HashSet<ComponentNode> result = new HashSet<>();
    for (Set<ComponentNode> componentNodes : componentIndex.values()) {
      result.addAll(componentNodes);
    }

    return result;
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
