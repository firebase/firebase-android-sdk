package com.google.firebase.gradle.plugins

import org.apache.maven.repository.internal.MavenRepositorySystemUtils
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

abstract class GMavenService : BuildService<BuildServiceParameters.None> {

}
