# Protolite well known types
This is the proto lite bindingss of the proto [well known types](https://github.com/google/protobuf/tree/5ce724bcebebb56914da6efc40b85c4c801e6fe1/src/google/protobuf) and the google cloud types.

## Problem
When an app uses multiple firebase SDKs, gradle has the ability to [resolve version conflicts](https://docs.gradle.org/current/dsl/org.gradle.api.artifacts.ResolutionStrategy.html) in overlapping libraries that these SDKs may depend on. The caveat with protobuf is that in addition to a runtime, standard practices to use protobuf will result in final artifacts containing class files of the compiled higher level proto datatypes like TimeStamp.class, Any.class etc.

Specifically, protobuf [requires](https://github.com/google/protobuf-gradle-plugin#protos-in-dependencies) apps using these higher level datatypes in their protos use the following dependency declaration in their projects.
```groovy
protobuf 'com.google.protobuf:protobuf-java:3.0.2'
```

The above declaration in addition to making the proto files like [Timestamp.proto](https://github.com/google/protobuf/blob/5ce724bcebebb56914da6efc40b85c4c801e6fe1/src/google/protobuf/timestamp.proto) (say) available to be imported and used with the protoc compiler, also bundles the resulting Timestamp.class file into the final artifact. This is problematic once we have an app use multiple firebase libraries each of which contains a separate copy of the TimeStamp.class file leading to a flaky classpath and indeterminate runtime behavior.

## Solution

We circumvent by not having a direct dependency on the higher level protos, instead depending on the *protolite-well-known-types* module which makes proto files available to be imported in addition to including it into the final artifact. The key to this approach is that gradle now can resolve a version conflict between firebase libraries that depend on v1 and v2 of the *protolite-well-known-types* library.

Note: For the same reasons, this project also contains the google cloud well known types shared across multiple firebase projects.

## Usage
If you have a direct protobuf dependency like shown above, it can be removed and replaced with

```groovy
implementation project(':protolite-well-known-types')
```

A typical grpc and protobuf-lite configuration should look like

```groovy
protobuf {
  protoc {
    artifact = "com.google.protobuf:protoc:$protocVersion"
  }
  plugins {
    grpc {
      artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
    }
  }
  generateProtoTasks {
    all().each { task ->
      task.builtins {
        java { option 'lite' }
      }
      task.plugins {
        grpc {
          option 'lite'
        }
      }
    }
  }
}

android {
 sourceSets {
        main {
            proto {
                srcDir 'src/proto'
            }
            java {
            }
        }
        test {
            java {
                srcDir 'src/testUtil'
            }
        }
        androidTest {
            java {
                srcDir 'src/testUtil'
            }
        }
    }
}

dependencies {
    implementation project(':protolite-well-known-types')
    implementation "io.grpc:grpc-stub:$grpcVersion"

    // optionally override grpc's protobuf-lite runtime
    implementation "io.grpc:grpc-protobuf-lite:$grpcVersion"
}
```

## Long term soluion

https://github.com/google/protobuf/issues/1889
