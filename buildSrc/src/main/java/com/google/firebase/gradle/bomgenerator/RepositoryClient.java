// Copyright 2021 Google LLC
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

package com.google.firebase.gradle.bomgenerator;

import com.google.firebase.gradle.bomgenerator.model.Dependency;
import com.google.firebase.gradle.bomgenerator.model.VersionBump;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.version.Version;
import org.gradle.api.GradleException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class RepositoryClient {
  private static final RemoteRepository GMAVEN =
      new RemoteRepository.Builder("central", "default", "https://maven.google.com").build();

  private final RepositorySystem system;
  private final RepositorySystemSession session;

  public RepositoryClient() {
    system = newRepositorySystem();
    session = newRepositorySystemSession(system);
  }

  public Dependency populateDependencyVersion(
      Dependency firebaseDep, Map<String, String> versionsFromPreviousBomByArtifact) {
    try {
      List<Version> rangeResult = getVersionsForDependency(firebaseDep).getVersions();
      String version = rangeResult.get(rangeResult.size() - 1).toString();
      String versionFromPreviousBom =
          versionsFromPreviousBomByArtifact.get(firebaseDep.fullArtifactId());

      VersionBump versionBump =
          versionFromPreviousBom == null
              ? VersionBump.MINOR
              : VersionBump.getBumpBetweenVersion(version, versionFromPreviousBom);
      return Dependency.create(
          firebaseDep.groupId(), firebaseDep.artifactId(), version, versionBump);
    } catch (VersionRangeResolutionException e) {
      throw new GradleException("Failed to resolve dependency: " + firebaseDep.toGradleString(), e);
    }
  }

  public Optional<String> getLastPublishedVersion(Dependency dependency)
      throws VersionRangeResolutionException {
    Version version = getVersionsForDependency(dependency).getHighestVersion();
    return Optional.ofNullable(version).map(Version::toString);
  }

  public Set<String> getAllFirebaseArtifacts() {
    try (InputStream index =
        new URL("https://dl.google.com/dl/android/maven2/com/google/firebase/group-index.xml")
            .openStream()) {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setValidating(true);
      factory.setIgnoringElementContentWhitespace(true);
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document doc = builder.parse(index);
      NodeList artifactList = doc.getFirstChild().getChildNodes();
      Set<String> outputArtifactIds = new HashSet<>();
      for (int i = 0; i < artifactList.getLength(); i++) {
        Node artifact = artifactList.item(i);
        if (artifact.getNodeName().contains("#")) {
          continue;
        }
        outputArtifactIds.add("com.google.firebase:" + artifact.getNodeName());
      }
      return outputArtifactIds;
    } catch (SAXException | IOException | ParserConfigurationException e) {
      throw new RuntimeException("Failed to get Firebase Artifact Ids", e);
    }
  }

  // Dependency string must be in the format <groupId>:<artifactId>
  // for example: "com.google.firebase:firebase-bom"
  private VersionRangeResult getVersionsForDependency(Dependency dep)
      throws VersionRangeResolutionException {
    Artifact requestArtifact = new DefaultArtifact(dep.fullArtifactId() + ":[0,)");

    VersionRangeRequest rangeRequest = new VersionRangeRequest();
    rangeRequest.setArtifact(requestArtifact);
    rangeRequest.setRepositories(Arrays.asList(GMAVEN));

    return system.resolveVersionRange(session, rangeRequest);
  }

  private static RepositorySystem newRepositorySystem() {
    /*
     * Aether's components implement org.eclipse.aether.spi.locator.Service to ease
     * manual wiring and using the prepopulated DefaultServiceLocator, we only need
     * to register the repository connector and transporter factories.
     */
    DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
    locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
    locator.addService(TransporterFactory.class, FileTransporterFactory.class);
    locator.addService(TransporterFactory.class, HttpTransporterFactory.class);

    locator.setErrorHandler(
        new DefaultServiceLocator.ErrorHandler() {
          @Override
          public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
            exception.printStackTrace();
          }
        });

    return locator.getService(RepositorySystem.class);
  }

  private static DefaultRepositorySystemSession newRepositorySystemSession(
      RepositorySystem system) {
    DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

    LocalRepository localRepo = new LocalRepository("target/local-repo");
    session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));

    return session;
  }
}
