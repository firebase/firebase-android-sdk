# Unreleased
- [fixed] Fixed an issue that caused the SDK to crash if the download location
  was deleted before the download completed. Instead, the download now fails.

# 19.0.2
- [fixed] Fixed an encoding issue in `list()/listAll()` that caused us to miss
  entries for folders that contained special characters.

# 19.0.1
- [fixed] `listAll()` now propagates the error messages if the List operation
  was denied by a Security Rule.

# 19.0.0
- [changed] Added missing nullability annotations for better Kotlin interop.
- [internal] Removed ``@PublicApi` annotations as they are no longer enforced
  and have no semantic meaning.

# 18.1.0
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
