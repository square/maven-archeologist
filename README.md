# Maven archeology

A library to make resolving maven artifacts (from various repositories) easy.  The library itself
wraps the Maven resolver APIs and provides simple HTTP-based resolution. From this any number of
tools can be constructed, which require obtaining (and locally caching) maven artifacts, resolving
the "effective project model", validating hashes, etc.

## Usage

The main entry point to the library, which wraps (and mostly hides) the Maven resolution
infrastructure, is `ArtifactResolver`.  It has two ways to use it - the simple one which just
downloads the relevant artifact and its POM file, and returns the file locations, or a slightly
more nuanced API, which lets you get resolved Maven metadata (but doesn't download the artifact).

### Simplest Usage

In this variant, the API just downloads the POM and artifact files and their hash files, validates
the files, and returns POM and artifact files to you in a pair. 

```kotlin
val resolver = ArtifactResolver() // creates a resolver with repo list defaulting to Maven Central.
val (pom, artifact) = resolver.download("com.google.guava:guava:27.1-jre")
```

### Resolving Metadata

This is the more robust way to use the API, and it gets you a `ResolvedArtifact`, which contains
the fully resolved model object (resolved dependencies (including from dependencyManagement and
properties substitutions)). The resolved artifact can then be used to fetch the main artifact.

```kotlin
val resolver = ArtifactResolver() // creates a resolver with repo list defaulting to Maven Central.
val artifact = resolver.artifactFor("com.google.guava:guava:27.1-jre") // returns Artifact
val resolvedArtifact = resolver.resolveArtifact(artifact) // returns ResolvedArtifact
val result = resolver.download(resolvedArtifact) // returns FetchStatus
if (result is SUCCESSFUL) { /* win! */ }
```

### Adding Repositories

The resolver defaults to resolving against Maven Central. Specifying repositories is as simple as:

```kotlin
val repo1 = Repository().apply {
  id = "some-identifier"
  releases = RepositoryPolicy().apply { enabled = "true" }
  url = "https://some.server/path/to/repo" // At present, only http/https are supported.
}
val repo2 = ...
val resolver = ArtifactResolver(repositories = listOf(rep1, rep2))
```
> Note: This is one of the rare times you'll directly interact with Maven's internal APIs, except
> for interacting with the `Model` object if you resolve the effective-model.

Reasonably popular repositories have been pre-defined in the `Repositories` type, e.g.
`Repositories.MAVEN_CENTRAL`

### Local Artifact Cache (Maven Local Repository)

By default, artifacts are cached in `${HOME}/.m2/repository`, but this can be changed (per resolver
instance) like so:

```kotlin
val resolver = ArtifactResolver(cacheDir = fs.getPath("/some/cache/dir")
```
## Demo CLI

The project also contains a demo CLI app which will resolve and download the maven artifacts listed
on the repository, cache them in a defined directory. It is iterative and single-threaded, so not
suitable for high-volume resolution, but it can help for small tasks and show how to wield the APIs.
```shell
bazel run //:resolver -- --local_maven_cache /path/to/cache some:artifact:1 another:artifact:2
```

## Possible uses

  * Build a dependency graph scanning/analysis tool
  * Use in non-Maven build tools for easier use of Maven resolution
  * Pre-fetching artifacts to permit later off-line function.
  * ...

## Known Limitations

  * Does not download sub-artifacts (-sources.jar) or classifiers (yet)
  * Does not do any traversal (this can be done in calling code) or transitive activity
  * Does not do any multithreading (calling code can build a parallel graph walk around it)
  * Has a crappy heuristic (with a hack for bundle) for converting packaging->suffix
    - Doesn't resolve plugin metadata that might configure things like that.
  * The CLI is super limited as a demo-app.
  * Basic functionality is tested, but coverage is weak.
  * Wraps the Maven APIs, but might need some more ability to configure them (without bailing
    out of the wrapper infrastructure entirely)

## License

```
Copyright (c) 2020, Square, Inc. All Rights Reserved

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
