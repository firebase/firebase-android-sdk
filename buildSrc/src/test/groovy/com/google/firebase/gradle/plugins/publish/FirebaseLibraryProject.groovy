// Copyright 2019 Google LLC
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

package com.google.firebase.gradle.plugins.publish

import groovy.text.SimpleTemplateEngine

import java.util.zip.ZipFile


class FirebaseLibraryProject implements HasBuild {
    static final String BUILD_TEMPLATE = '''
        plugins {
            id 'firebase-library'
        }
        group = '${group}'
        version = '${version}'
        <% if (latestReleasedVersion) println "ext.latestReleasedVersion = $latestReleasedVersion" %>
        firebaseLibrary {
          <% if (releaseWith != null) println "releaseWith project(':$releaseWith.name')" %>
          <% if (customizePom != null) println "customizePom {$customizePom}" %>
        }
        android.compileSdkVersion = 26

        repositories {
            google()
            jcenter()
	    }
        dependencies {
            <%dependencies.each { c ->
              c.value.each { d ->
                println "$c.key project(':$d.name')"
              }
            } %>
            <%externalDependencies.each { c ->
              c.value.each { d ->
                println "$c.key '$d'"
              }
            } %>
        }
        '''
    String name
    String group = 'com.example'
    String version = 'undefined'
    String latestReleasedVersion = ''
    Map<String, List<FirebaseLibraryProject>> projectDependencies = [:]
    Map<String, List<String>> externalDependencies = [:]
    FirebaseLibraryProject releaseWith = null
    String customizePom = null
    List<String> classNames = []

    @Override
    String generateBuildFile() {
        def text = new SimpleTemplateEngine().createTemplate(BUILD_TEMPLATE).make([
                name: name,
                group: group,
                version: version,
                dependencies: projectDependencies,
                externalDependencies: externalDependencies,
                releaseWith: releaseWith,
                latestReleasedVersion: latestReleasedVersion,
                customizePom: customizePom,
        ])

        return text
    }

    Optional<File> getPublishedPom(String rootFolder) {
        def v = releaseWith == null ? version : releaseWith.version
        def poms = new FileNameFinder().getFileNames(rootFolder,
                "${group.replaceAll('\\.', '/')}/${name}/${v}*/*.pom")

        if(poms.empty) {
            return Optional.empty()
        }
        return Optional.of(new File(poms[0]))
    }

    Optional<ZipFile> getPublishedAar(String rootFolder) {
        def v = releaseWith == null ? version : releaseWith.version
        def poms = new FileNameFinder().getFileNames(rootFolder,
                "${group.replaceAll('\\.', '/')}/${name}/${v}*/*.aar")

        if(poms.empty) {
            return Optional.empty()
        }
        return Optional.of(new ZipFile(new File(poms[0])))
    }
}