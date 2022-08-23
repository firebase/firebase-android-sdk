### Helper functions

# Determine if there are changes in files matching any of the
# path patterns provided.
def hasChangesIn(paths)
  path_array = Array(paths)
  path_array.each do |dir|
    if !git.modified_files.grep(/#{dir}/).empty?
      return true
    end
  end
  return false
end

### Definitions

# Label for any change that shouldn't have an accompanying CHANGELOG entry,
# including all changes that do not affect the compiled binary (i.e. script
# changes, test-only changes)
declared_trivial = github.pr_labels.include? "no-changelog"

# Whether or not there are pending changes to any changelog file.
has_changelog_changes = hasChangesIn(["CHANGELOG"])

# A FileList containing Java, Kotlin, or XML changes.
# This is a proxy for knowing whether the change has user visible SDK changes.
sdk_changes = (git.modified_files +
               git.added_files +
               git.deleted_files).select do |line|
  line.end_with?(".java") ||
    line.end_with?(".kt") ||
    line.end_with?(".xml") ||
    line.end_with?(".yml")
end

# Whether or not the PR has modified SDK source files.
has_sdk_changes = !sdk_changes.empty?

### Actions

# Warn if a changelog is left out on a non-trivial PR that has modified
# SDK source files (podspec, markdown, etc changes are excluded).
if has_sdk_changes
  if !has_changelog_changes && !declared_trivial
    warning = "Did you forget to add a changelog entry? (Add the 'no-changelog'"\
      " label to the PR to silence this warning.)"
    warn(warning)
  end
end
