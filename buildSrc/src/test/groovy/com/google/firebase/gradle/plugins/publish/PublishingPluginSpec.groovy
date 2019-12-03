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
import org.apache.commons.io.IOUtils
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

class PublishingPluginSpec extends Specification {

    @Rule TemporaryFolder testProjectDir = new TemporaryFolder()
    File rootBuildFile
    File rootSettingsFile

    List<FirebaseLibraryProject> subprojects = []

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

    def "Publishing dependent projects succeeds"() {
        FirebaseLibraryProject project1 = new FirebaseLibraryProject(
                name: 'childProject1',
                version: '1.0')
        FirebaseLibraryProject project2 = new FirebaseLibraryProject(
                name: 'childProject2',
                version: '0.9',
                projectDependencies: ['implementation': [project1]],
                customizePom: """
licenses {
  license {
    name = 'Hello'
  }
}
""")

        when: "publishFirebase invoked"
        subprojectsDefined(project1, project2)
        def result = publish(Mode.RELEASE, project1, project2)
        then: 'poms exist'
        def pom1 = project1.getPublishedPom("$testProjectDir.root/build/m2repository")
        def pom2 = project2.getPublishedPom("$testProjectDir.root/build/m2repository")
        assert pom1.isPresent()
        assert pom2.isPresent()

        and: 'versions are valid'
        def xml1 = new XmlSlurper().parseText(pom1.get().text)
        xml1.version == project1.version
        xml1.licenses.license.name == "The Apache Software License, Version 2.0"
        xml1.licenses.license.url == "http://www.apache.org/licenses/LICENSE-2.0.txt"

        def xml2 = new XmlSlurper().parseText(pom2.get().text)
        xml2.version == project2.version
        xml2.licenses.license.name == "Hello"

        def dependency = xml2.dependencies.dependency

        dependency.groupId == project1.group
        dependency.artifactId == project1.name
        dependency.version == project1.version
    }

    def "Publish with unreleased dependency"() {
        FirebaseLibraryProject project1 = new FirebaseLibraryProject(
                name: 'childProject1',
                version: '1.0')
        FirebaseLibraryProject project2 = new FirebaseLibraryProject(
                name: 'childProject2',
                version: '0.9',
                projectDependencies: ['implementation': [project1]])

        when: "publishFirebase invoked"
        subprojectsDefined(project1, project2)
        def result = publish(Mode.RELEASE, project2)
        then: 'build fails'
        Exception e = thrown(Exception)
        e.getMessage().contains("Failed to release project ':childProject2'")
    }

    def "Publish with released dependency"() {
        FirebaseLibraryProject project1 = new FirebaseLibraryProject(
                name: 'childProject1',
                version: '1.0',
                latestReleasedVersion: '0.8')
        FirebaseLibraryProject project2 = new FirebaseLibraryProject(
                name: 'childProject2',
                version: '0.9',
                projectDependencies: ['implementation': [project1]])

        when: "publishFirebase invoked"
        subprojectsDefined(project1, project2)
        def result = publish(Mode.RELEASE, project2)
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
        FirebaseLibraryProject project1 = new FirebaseLibraryProject(
                name: 'childProject1',
                version: '1.0')
        FirebaseLibraryProject project2 = new FirebaseLibraryProject(
                name: 'childProject2',
                version: '0.9',
                projectDependencies: ['implementation': [project1]])

        when: "publishFirebase invoked"
        subprojectsDefined(project1, project2)
        def result = publish(Mode.SNAPSHOT, project1, project2)
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
        FirebaseLibraryProject project1 = new FirebaseLibraryProject(
                name: 'childProject1',
                version: '1.0',
                latestReleasedVersion: '0.8')
        FirebaseLibraryProject project2 = new FirebaseLibraryProject(
                name: 'childProject2',
                version: '0.9',
                projectDependencies: ['implementation': [project1]])

        when: "publishFirebase invoked"
        subprojectsDefined(project1, project2)
        def result = publish(Mode.SNAPSHOT, project2)
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

    def "Publish project should also publish coreleased projects"() {
        FirebaseLibraryProject project1 = new FirebaseLibraryProject(
                name: 'childProject1',
                version: '1.0')
        FirebaseLibraryProject project2 = new FirebaseLibraryProject(
                name: 'childProject2',
                version: '0.9',
                projectDependencies: ['implementation': [project1]],
                releaseWith: project1)

        when: "publishFirebase invoked"
        subprojectsDefined(project1, project2)
        def result = publish(Mode.RELEASE, project1)
        then: 'poms exist'
        def pom1 = project1.getPublishedPom("$testProjectDir.root/build/m2repository")
        def pom2 = project2.getPublishedPom("$testProjectDir.root/build/m2repository")
        assert pom1.isPresent()
        assert pom2.isPresent()

        and: 'versions are valid'
        def xml1 = new XmlSlurper().parseText(pom1.get().text)
        xml1.version == project1.version

        def xml2 = new XmlSlurper().parseText(pom2.get().text)
        xml2.version == project1.version
        def dependency = xml2.dependencies.dependency

        dependency.groupId == project1.group
        dependency.artifactId == project1.name
        dependency.version == project1.version
    }

    def "Publish project should correctly set dependency types"() {
        FirebaseLibraryProject project1 = new FirebaseLibraryProject(
                name: 'childProject1',
                version: '1.0',
                latestReleasedVersion: '0.8')
        FirebaseLibraryProject project2 = new FirebaseLibraryProject(
                name: 'childProject2',
                version: '0.9',
                projectDependencies: ['implementation': [project1]],
                externalDependencies: [
                    'implementation': [
                        'com.google.dagger:dagger:2.22',
                        'com.google.dagger:dagger-android-support:2.22',
                        'com.android.support:multidex:1.0.3'
                    ]
                ])

        when: "publishFirebase invoked"
        subprojectsDefined(project1, project2)
        def result = publish(Mode.RELEASE, project2)
        then: 'poms exist'
        def pom1 = project1.getPublishedPom("$testProjectDir.root/build/m2repository")
        def pom2 = project2.getPublishedPom("$testProjectDir.root/build/m2repository")
        assert !pom1.isPresent()
        assert pom2.isPresent()

        and: 'versions and dependency types are valid'
        def xml2 = new XmlSlurper().parseText(pom2.get().text)
        xml2.version == project2.version
        def dependencies = xml2.dependencies.dependency.collect {
            "${it.groupId.text()}:${it.artifactId.text()}:${it.version.text()}:${it.type.text()}:${it.scope.text()}"
        } as Set<String>
        dependencies == [
                "$project1.group:$project1.name:$project1.latestReleasedVersion:aar:compile",
                'com.google.dagger:dagger:2.22:jar:compile',
                'com.google.dagger:dagger-android-support:2.22:aar:compile'
        ] as Set<String>

    }

    def "Publish with exported java-lib should vendor it into aar"() {
        JavaLibraryProject javaProject = new JavaLibraryProject(
                name: 'java-lib',
                externalDependencies: ['implementation': ['com.google.dagger:dagger:2.22']],
                classNames: ['com.example.JavaClass']
        )
        FirebaseLibraryProject firebaseProject = new FirebaseLibraryProject(
                name: 'firebase-lib',
                projectDependencies: [
                        'firebaseLibrary.exports': [javaProject]
                ],
                classNames: ['com.example.AndroidClass']
        )

        when: "publishFirebase invoked"
        subprojectsDefined(javaProject, firebaseProject)
        def result = publish(Mode.RELEASE, firebaseProject)


        then: 'poms exist'
        def pom = firebaseProject.getPublishedPom("$testProjectDir.root/build/m2repository")
        pom.isPresent()

        and: 'versions and dependency types are valid'
        def xml = new XmlSlurper().parseText(pom.get().text)
        xml.version == firebaseProject.version
        def dependencies = xml.dependencies.dependency.collect {
            "${it.groupId.text()}:${it.artifactId.text()}:${it.version.text()}:${it.type.text()}:${it.scope.text()}"
        } as Set<String>
        dependencies == ['com.google.dagger:dagger:2.22:jar:compile'] as Set<String>

        and: 'aar is present'
        def aar = firebaseProject.getPublishedAar("$testProjectDir.root/build/m2repository")
        aar.isPresent()

        and: 'classes.jar contains java-lib classes'
        def aarFile = aar.get()
        def entries = zipEntries(aarFile.getInputStream(aarFile.getEntry('classes.jar')))
        entries == [
                'com/example/JavaClass.class',
                'com/example/AndroidClass.class',
                'com/example/BuildConfig.class'
        ] as Set<String>
    }

    private BuildResult build(String... args) {
        GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments(args)
                .withPluginClasspath()
                .build()
    }

    private BuildResult publish(Mode mode, FirebaseLibraryProject... projects) {
        def projectsArg = "-PprojectsToPublish=${projects.collect { it.name }.join(',')}"
        def modeArg = "-PpublishMode=$mode"
        build(projectsArg, modeArg, 'firebasePublish')
    }

    private include(HasBuild project) {
        testProjectDir.newFolder(project.name, 'src', 'main')
        testProjectDir.newFile("${project.name}/build.gradle") << project.generateBuildFile()
        testProjectDir.newFile("${project.name}/src/main/AndroidManifest.xml") << MANIFEST
        project.classNames.each {
            def path = it.replace('.', '/')
            def (pkg, className) = packageAndClassName(it)
            testProjectDir.newFolder(project.name, 'src', 'main', 'java', *pkg.split('\\.')).mkdirs()
            testProjectDir.newFile("${project.name}/src/main/java/${path}.java") << """
package $pkg;
public class $className {}
"""
        }

        subprojects.add(project)
    }

    def packageAndClassName(String qualifiedName) {
        int p=qualifiedName.lastIndexOf(".")
        if (p == -1) {
            return ['', qualifiedName]
        }
        return [qualifiedName.substring(0, p), qualifiedName.substring(p+1)]
    }

    private void subprojectsDefined(HasBuild... projects) {
        rootBuildFile  = testProjectDir.newFile('build.gradle')
        rootSettingsFile = testProjectDir.newFile('settings.gradle')

        projects.each { include it}

        rootBuildFile << rootProject
        rootSettingsFile << generateSettingsGradle()
    }

    private String generateSettingsGradle() {
        return subprojects.collect { "include ':$it.name'" }.join('\n')
    }

    private static Set<String> zipEntries(InputStream zip) {
        ByteArrayOutputStream classesJar = new ByteArrayOutputStream()
        IOUtils.copy(zip, classesJar)

        Set<String> entries = []

        ZipInputStream zipInput = new ZipInputStream(new ByteArrayInputStream(classesJar.toByteArray()))
        ZipEntry currentEntry = zipInput.nextEntry
        while(currentEntry != null) {
            if (!currentEntry.directory) {
                entries.add(currentEntry.name)
            }
            currentEntry = zipInput.nextEntry
        }
        return entries
    }
}
