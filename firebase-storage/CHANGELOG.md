# Unreleased (17.1.0)
- [feature] Added `StorageReference.list()` and `StorageReference.listAll()`,
  which allows developers to list the files and folders under the given
  StorageReference.
- [changed] Added validation to `StorageReference.getDownloadUrl()` and
  `StorageReference.getMetadata()` to return an error if the reference is the
  root of the bucket.

# 17.0.0
- [internal] Updated the SDK initialization process and removed usages of
  deprecated methods.
- [changed] Added `@RestrictTo` annotations to discourage the use of APIs that
  are not public. This affects internal APIs that were previously obfuscated
  and are not mentioned in our documentation.
