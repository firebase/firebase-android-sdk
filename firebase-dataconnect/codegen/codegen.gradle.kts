plugins {
  `kotlin-dsl`
  `java-gradle-plugin`
}

gradlePlugin {
  plugins {
    create("codegen") {
      id = "com.google.firebase.dataconnect" 
      implementationClass = "com.google.firebase.dataconnect.codegen.plugin.CodegenPlugin"
    }
  }
}

dependencies {
  implementation(libs.kotlin.stdlib)
}

