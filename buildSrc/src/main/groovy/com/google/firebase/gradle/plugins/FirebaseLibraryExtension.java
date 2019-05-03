package com.google.firebase.gradle.plugins;

import com.google.firebase.gradle.plugins.ci.device.FirebaseTestLabExtension;

import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

public class FirebaseLibraryExtension {
  public final Property<Boolean> publishJavadoc;
  public final Property<Boolean> publishSources;
  public final FirebaseTestLabExtension testLab;

  @Inject
  public FirebaseLibraryExtension(ObjectFactory objectFactory) {
    this.publishJavadoc = objectFactory.property(Boolean.class);
    this.publishSources = objectFactory.property(Boolean.class);
    this.testLab = new FirebaseTestLabExtension(objectFactory);
  }

  public void setPublishJavadoc(boolean value) {
    publishJavadoc.set(value);
  }

  void testLab(Action<FirebaseTestLabExtension> action) {
    action.execute(testLab);
  }
}
