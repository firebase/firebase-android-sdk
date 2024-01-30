# Firebase Encoders

This project provides libraries and code generation infrastructure that allows
encoding java classes into various target serialization formats(currently
supported: **json** and **proto**).

The project consists of multiple parts:

*   `firebase_encoders` - Core API and Annotations library.
*   `processor` - Java plugin that automatically generates encoders for
    `@Encodable` annotated POJOs.
*   `firebase_encoders_json` - JSON serialization support.
*   `firebase_encoders_proto` - Protobuf serialization support.
*   `protoc_gen` - Protobuf compiler plugin that generates encoder-compliant
    classes. Can be used with `firebase_encoders_proto` and
    `firebase_encoders_json`.
*   `reflective` - Can be used to encode any given class via Java
    reflection(**not recommented**).

### Protobuf gettings started

##### Step1. Place proto files into **src/main/proto/**

*src/main/proto/my.proto*
```proto
syntax = "proto3";

package com.example.my;

import "google/protobuf/timestamp.proto";

message SimpleProto {
  int32 value = 1;
  .google.protobuf.Timestamp time = 2;
}
```


##### Step2. Add the following configurations into gradle module build file.

*example.gradle*
```gradle
plugins {
    id "java-library"
    id 'com.google.protobuf'
}

// add a dependency on the protoc plugin's fat jar to make it available to protobuf below.
configurations.create("protobuild")
dependencies {
    protobuild project(path: ":encoders:protoc-gen-firebase-encoders", configuration: "shadow")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protocVersion"
    }
    plugins {
        firebaseEncoders {
            path = configurations.protobuild.asPath
        }
    }
    generateProtoTasks {
        all().each { task ->
            task.dependsOn configurations.protobuild
            task.inputs.file 'code-gen-cfg.textproto'
            task.plugins {
                firebaseEncoders {
                    option file('code-gen-cfg.textproto').path
                }
            }
            // In most cases you don't need the full Java output
            task.builtins {
                remove java
            }
        }
    }
}

dependencies {
    implementation project(":encoders:firebase-encoders")
    implementation project(":encoders:firebase-encoders-proto")
    annotationProcessor project(":encoders:firebase-encoders-processor")
}
```

##### Step3. Create a code-gen-cfg.textproto file at the module root folder(same location as the gradle module build file).

*code-gen-cfg.textproto* 

Note:
- The filename must be the same as the filename determined in the gradle build file.
- Only need to specify the "root" proto object, anything it references will automatically be included.
```textproto
# code_gen_cfg.textproto
# proto-file: src/main/proto/my.proto
# proto-message: SimpleProto

# all types will be vendored in this package
vendor_package: "com.google"

# marks a type as a "root" message
include: "com.example.my.SimpleProto"
```

With the above configuration here's a list of classes that will be generated:

```
com.google.com.example.my.SimpleProto
com.google.com.example.my.SimpleProto$Builder
com.google.google.protobuf.Timestamp
com.google.google.protobuf.Timestamp$Builder
```

Only `root` classes are "encodable" meaning that they have the following
methods:

```java
public class SimpleProto {
  public void writeTo(OutputStream output) throws IOException;
  public byte[] toByteArray();
}
```

### Annotation Processing on Kotlin

The default gradle `annotationProcessor` import doesn't run the processor over kotlin code, so we need to use `kapt`

1. Add the plugin to your build

```gradle
plugins {
    id 'java-library'
    id 'com.google.protobuf'
    id 'kotlin-kapt'
}
```

2. Replace your `annotationProcessor` tag with `kapt`

```gradle
dependencies {
    implementation project(":encoders:firebase-encoders")
    implementation project(":encoders:firebase-encoders-proto")
    // annotationProcessor project(":encoders:firebase-encoders-processor")
    kapt project(":encoders:firebase-encoders-processor")
}
```