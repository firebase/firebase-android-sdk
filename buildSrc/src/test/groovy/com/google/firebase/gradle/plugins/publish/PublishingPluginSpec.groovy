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

package com.google.firebase.gradle.plugins.publish

import com.google.firebase.gradle.plugins.publish.Publisher.Mode
import groovy.text.SimpleTemplateEngine
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification;

class PublishingPluginSpec extends Specification {

    static class Project {
        static final String BUILD_TEMPLATE = '''
        plugins {
            id 'com.android.library'
        }
        android.compileSdkVersion = 26
        group = '${group}'
        version = '${version}'
        <% if (latestReleasedVersion) println "ext.latestReleasedVersion = $latestReleasedVersion" %>
        repositories {
            google()
            jcenter()
	}
        dependencies {
            <%dependencies.each { println "implementation project(':$it.name')" } %>
        }
        '''
        String name
        String group = 'com.example'
        String version = 'undefined'
        String latestReleasedVersion = ''
        Set<Project> projectDependencies = []

        String generateBuildFile() {
            def text = new SimpleTemplateEngine().createTemplate(BUILD_TEMPLATE).make([
                    name: name,
                    group: group,
                    version: version,
                    dependencies: projectDependencies,
                    latestReleasedVersion: latestReleasedVersion
            ])

            return text
        }

        Optional<File> getPublishedPom(String rootFolder) {
            def poms = new FileNameFinder().getFileNames(rootFolder,
                    "${group.replaceAll('\\.', '/')}/${name}/${version}*/*.pom")

            if(poms.empty) {
                return Optional.empty()
            }
            return Optional.of(new File(poms[0]))
        }
    }

    @Rule TemporaryFolder testProjectDir = new TemporaryFolder()
    File rootBuildFile
    File rootSettingsFile

    List<Project> subprojects = []

    final String rootProject = """
        buildscript {
            repositories {
                google()
                jcenter()
            }
        }
        plugins {
            id 'PublishingPlugin'
        }
        """

    def MANIFEST = """<?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                  package="com.example">
            <uses-sdk android:minSdkVersion="14"/>
        </manifest>
    """

    final Project project1 = new Project(name: 'childProject1', version: '1.0')
    final Project project2 = new Project(name: 'childProject2', version: '0.9', projectDependencies: [project1])

    final String childProject1 = """
        plugins {
            id 'com.android.library'
        }
        android.compileSdkVersion = 26
        version = '1.0'
        """

    final String childProject2 = """
        plugins {
            id 'com.android.library'
        }
        android.compileSdkVersion = 26
        version = '0.9'
        
        dependencies {
            implementation project(':childProject1')
        }
        """

    def "Publish all dependent projects succeeds"() {
        Project project1 = new Project(name: 'childProject1', version: '1.0')
        Project project2 = new Project(name: 'childProject2', version: '0.9', projectDependencies: [project1])

        when: "publishFirebase invoked"
        subprojectsDefined(project1, project2)
        def result = publish(Mode.RELEASE, 'childProject1', 'childProject2')
        then: 'poms exist'
        def pom1 = project1.getPublishedPom("$testProjectDir.root/build/m2repository")
        def pom2 = project2.getPublishedPom("$testProjectDir.root/build/m2repository")
        assert pom1.isPresent()
        assert pom2.isPresent()

        and: 'versions are valid'
        def xml1 = new XmlSlurper().parseText(pom1.get().text)
        xml1.version == project1.version

        def xml2 = new XmlSlurper().parseText(pom2.get().text)
        xml2.version == project2.version
        def dependency = xml2.dependencies.dependency

        dependency.groupId == project1.group
        dependency.artifactId == project1.name
        dependency.version == project1.version
    }

    def "Publish with unreleased dependency"() {
        Project project1 = new Project(name: 'childProject1', version: '1.0')
        Project project2 = new Project(name: 'childProject2', version: '0.9', projectDependencies: [project1])

        when: "publishFirebase invoked"
        subprojectsDefined(project1, project2)
        def result = publish(Mode.RELEASE, 'childProject2')
        then: 'build fails'
        Exception e = thrown(Exception)
        e.getMessage().contains("Failed to release project ':childProject2'")
    }

    def "Publish with released dependency"() {
        Project project1 = new Project(name: 'childProject1', version: '1.0', latestReleasedVersion: '0.8')
        Project project2 = new Project(name: 'childProject2', version: '0.9', projectDependencies: [project1])

        when: "publishFirebase invoked"
        subprojectsDefined(project1, project2)
        def result = publish(Mode.RELEASE, 'childProject2')
        then: 'poms exist'
        def pom1 = project1.getPublishedPom("$testProjectDir.root/build/m2repository")
        def pom2 = project2.getPublishedPom("$testProjectDir.root/build/m2repository")
        assert !pom1.isPresent()
        assert pom2.isPresent()

        and: 'versions are valid'

        def xml2 = new XmlSlurper().parseText(pom2.get().text)
        xml2.version == project2.version
        def dependency = xml2.dependencies.dependency

        dependency.groupId == project1.group
        dependency.artifactId == project1.name
        dependency.version == project1.latestReleasedVersion
    }

    def "Publish all dependent snapshot projects succeeds"() {
        Project project1 = new Project(name: 'childProject1', version: '1.0')
        Project project2 = new Project(name: 'childProject2', version: '0.9', projectDependencies: [project1])

        when: "publishFirebase invoked"
        subprojectsDefined(project1, project2)
        def result = publish(Mode.SNAPSHOT, 'childProject1', 'childProject2')
        then: 'poms exist'
        def pom1 = project1.getPublishedPom("$testProjectDir.root/build/m2repository")
        def pom2 = project2.getPublishedPom("$testProjectDir.root/build/m2repository")
        assert pom1.isPresent()
        assert pom2.isPresent()

        and: 'versions are valid'
        def xml1 = new XmlSlurper().parseText(pom1.get().text)
        xml1.version == "${project1.version}-SNAPSHOT"

        def xml2 = new XmlSlurper().parseText(pom2.get().text)
        xml2.version == "${project2.version}-SNAPSHOT"
        def dependency = xml2.dependencies.dependency

        dependency.groupId == project1.group
        dependency.artifactId == project1.name
        dependency.version == "${project1.version}-SNAPSHOT"
    }

    def "Publish snapshots with released dependency"() {
        Project project1 = new Project(name: 'childProject1', version: '1.0', latestReleasedVersion: '0.8')
        Project project2 = new Project(name: 'childProject2', version: '0.9', projectDependencies: [project1])

        when: "publishFirebase invoked"
        subprojectsDefined(project1, project2)
        def result = publish(Mode.SNAPSHOT, 'childProject2')
        then: 'poms exist'
        def pom1 = project1.getPublishedPom("$testProjectDir.root/build/m2repository")
        def pom2 = project2.getPublishedPom("$testProjectDir.root/build/m2repository")
        assert !pom1.isPresent()
        assert pom2.isPresent()

        and: 'versions are valid'

        def xml2 = new XmlSlurper().parseText(pom2.get().text)
        xml2.version == "${project2.version}-SNAPSHOT"
        def dependency = xml2.dependencies.dependency

        dependency.groupId == project1.group
        dependency.artifactId == project1.name
        dependency.version == project1.latestReleasedVersion
    }

    private BuildResult build(String... args) {
        GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments(args)
                .withPluginClasspath()
                .build()
    }

    private BuildResult publish(Mode mode, String... projects) {
        def projectsArg = "-PprojectsToPublish=${projects.join(',')}"
        def modeArg = "-PpublishMode=$mode"
        build(projectsArg, modeArg, 'firebasePublish')
    }

    private include(Project project) {
        testProjectDir.newFolder(project.name, 'src', 'main')
        testProjectDir.newFile("${project.name}/build.gradle") << project.generateBuildFile()
        testProjectDir.newFile("${project.name}/src/main/AndroidManifest.xml") << MANIFEST

        subprojects.add(project)
    }

    private void subprojectsDefined(Project... projects) {
        rootBuildFile  = testProjectDir.newFile('build.gradle')
        rootSettingsFile = testProjectDir.newFile('settings.gradle')

        projects.each { include it}

        rootBuildFile << rootProject
        rootSettingsFile << generateSettingsGradle()
    }

    private String generateSettingsGradle() {
        return subprojects.collect { "include ':$it.name'" }.join('\n')
    }
}
