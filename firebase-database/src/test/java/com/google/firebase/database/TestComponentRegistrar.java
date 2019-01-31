package com.google.firebase.database;

import com.google.firebase.components.Component;
import com.google.firebase.components.ComponentRegistrar;
import com.google.firebase.components.Dependency;
import com.google.firebase.platforminfo.UserAgentPublisher;
import java.util.Collections;
import java.util.List;

public class TestComponentRegistrar implements ComponentRegistrar {
  @Override
  public List<Component<?>> getComponents() {
    return Collections.singletonList(
        Component.builder(TestUserAgentDependentComponent.class)
            .add(Dependency.required(UserAgentPublisher.class))
            .factory(
                container ->
                    new TestUserAgentDependentComponent(container.get(UserAgentPublisher.class)))
            .build());
  }
}
