# Dackka Plugin

If the implementation of the DackkaPlugin is not straight forward enough
for you to understand, hopefully this document will help you.

## Dackka

Dackka is an internal-purposed Dokka plugin. Google hosts its documentation on
an internal service called **Devsite**. Firebase hosts their documentation on a
variant of Devsite called **Firesite**. You can click [here](https://firebase.google.com/docs/reference) 
to see how that looks. Essentially, it's just Google's way of decorating (and organizing)
documentation.

Devsite expects its files to be in a very specific format. Previously, we would
use an internal variant of metalava called doclava- which allowed us to provide
sensible defaults as to how the Javadoc should be rendered. Then, we would do
some further transformations to get the Javadoc output in-line with what
Devsite expects. This was a lengthy process, and definitely undesirable.

Dackka is an internal solution to that. Dackka provides a devsite plugin for
Dokka that will handle the job of doclava. Not only does this mean we can cut
out a huge portion of our transformation systems- but the overhead for maintaining
such systems is deferred away to the AndroidX team (the maintainers of Dackka).

## Dackka Usage

The Dackka we use is a fat jar pulled periodically from Dackka nightly builds,
and moved to our own maven repo bucket. Since it's recommended from the AndroidX
team to run Dackka on the command line, the fat jar allows us to ignore all the
miscenalionous dependencies of dackka (in regards to Dokka especially).

The general process of using Dackka is that you collection the dependencies and
source sets of the project, create a [Dokka appropriate JSON file](https://kotlin.github.io/dokka/1.7.10/user_guide/cli/usage/#example-using-json), 
run the Dackka fat jar with the JSON file as an argument, and publish the 
output folder.

A diagram of the flow can be seen below:
![Dackka Usage Flow](/docs/images/dackka_usage.png)

## Implementation

Our implementation of Dackka falls into three separate files, and four separate
tasks.

### GenerateDocumentationTask

This task is the meat of our Dackka implementation. It's what actually handles
the running of Dackka itself. The task exposes a gradle extension called 
`GenerateDocumentationTaskExtension` with various configurations points for
Dackka. This will likely be expanded upon in the future, as configurations are
needed.

The job of this task is to **just** run Dackka. What happens after-the-fact does
not matter to this task. It will take the provided inputs, organize them into
the expected JSON file, and run Dackka with the JSON file as an argument.

### FiresiteTransformTask

Dackka was design with Devsite in mind. The problem though, is that we use
Firesite. Firesite is very similar to Devsite, but there *are* minor differences.

The job of this task is to transform the Dackka output from a Devsite purposed format,
to a Firesite purposed format. This includes removing unnecessary files, fixing
links, removing unnecessary headers, and so forth.

There are open bugs for each transformation, as in an ideal world- they are instead
exposed as configurations for Dackka. Should these configurations be adopted by
Dackka, this task could become unnecessary itself- as we could just configure the task
during generation.

## DackkaPlugin 

This plugin is the mind of our Dackka implementation. It manages registering,
and configuring all the tasks for Dackka (that is, the already established
tasks above). While we do not currently offer any configuration for the Dackka
plugin, this could change in the future as needed. Currently, the DackkaPlugin
provides sensible defaults to output directories, package lists, and so forth.

The DackkaPlugin also provides two extra tasks: 
`cleanDackkaDocumentation` and
`deleteDackkaGeneratedJavaReferences`.

`cleanDackkaDocumentation` is exactly what it sounds like, a task to clean up
the output of Dackka. This is useful when testing Dackka outputs itself- shouldn't
be apart of the normal flow. The reasoning is that it would otherwise invalidate
the gradle cache.

`deleteDackkaGeneratedJavaReferences` is a temporary addition. Dackka generates
two separate style of docs for every source set: Java & Kotlin. Regardless of
whether the source is in Java or Kotlin. The Java output is how the source looks
from Java, and the Kotlin output is how the source looks from Kotlin. We publish
these under two separate categories, which you can see here: 
[Java](https://firebase.google.com/docs/reference/android/packages) 
or 
[Kotlin](https://firebase.google.com/docs/reference/kotlin/packages). 
Although, we do not currently publish Java packages with Dackka- and will wait
until we are more comfortable with the output of Dackka to do so. So until then,
this task will remove all generate Java references from the Dackka output.