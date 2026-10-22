### Unreleased

### App Distribution Gradle plugin version 5.3.1

* {{changed}} Updated HTTP transport initialization to use the default JVM certificate trust store instead of the deprecated `GoogleUtils.getCertificateTrustStore()`. (#8745)

### App Distribution Gradle plugin version 5.3.0

* {{feature}} Added authentication support for Application Default Credentials and Workload Identity Federation (#6936)
* {{feature}} Support setting a custom Google Cloud Storage bucket when running App Testing Agent tests (#8265)

### App Distribution Gradle plugin version 5.2.1

* {{fixed}} Declare dependency on GoogleServices plugin

