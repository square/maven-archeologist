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

// if you care about whether it was a cache-hit or not, do this. Otherwise test for "is SUCCESSFUL"
when (result) {
  is SUCCESSFUL.FOUND_IN_CACHE -> { /* win! */ }
  is SUCCESSFUL.SUCESSFULLY_FETCHED -> { /* win, but remotely! */ }
  else -> { /* Handle error */ }
}
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
val resolver = ArtifactResolver(cacheDir = fs.getPath("/some/cache/dir"))
```

### Comparing Versions

maven-archeologist has a convenience for representing maven versions in a way that they can be
compared according to semantic versioning, or at least the maven 3 variant, rather than merely
lexically. MavenVersion extends `Comparable<MavenVersion>` and can be used in comparison checks,
ordered collections, etc.

```kotlin
val versions = listOf(
  MavenVersion.from("2.3.5-SNAPSHOT"),
  MavenVersion.from("2.3.5"),
  MavenVersion.from("2.0"),
  MavenVersion.from("2a.0"),
  MavenVersion.from("2.0-beta"),
  MavenVersion.from("2.0-beta-SNAPSHOT"),
  MavenVersion.from("2.3.5.2"),
).sorted()

println(versions)
// should print [2.0-beta-SNAPSHOT, 2.0-beta, 2.0, 2.3.5-SAPSHOT, 2.3.5, 2.3.5.2, 2a.0]
```

> Note: 2a.0 comes after 2.0 because 2a is non-numeric and so is lexically compared.

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

## Core Capabilities

  * Basic wrapping of Maven APIs into much simpler conveniences
  * Basic artifact resolution to a maven model object
  * Downloading of POM and artifact files
    - file caching in (and resolution from) a maven-style local repository (cache)
  * md5/sha1 validation for files published with the accompanying hash files (maven-style)
  * Resolving from multiple/different/custom repositories
    - by default, for security, pinning the list of repositories to the given set, ignoring
      attempts from maven metadata to add more repositories, though this can be overridden
  * Metadata about the resolution/fetch operations, including whether the file(s) were satisfied
    from the cache or a remote fetch occurred.
  * Basic example CLI to resolve artifacts and find dependencies.

## Known Limitations

  * Does not download sub-artifacts (-sources.jar) or classifiers (yet)
  * Does not do any traversal (this can be done in calling code) of dependencies or other
    transitive operations
  * No multithreaded operations (though calling code can build a parallel graph walk around it)
    - file writes DO use a "write to temp file, atomically move" strategy, so generally the
      library should be tolerant of race-condition in resolution/download.
  * Has a crappy heuristic (with a hack for bundle) for converting packaging->suffix
    - Doesn't resolve plugin metadata that might configure things like that.
  * The CLI is super limited as a demo-app.
  * Basic functionality is tested, but coverage is weak.
  * Might need some more ability to configure them (without bailing out of the wrapper
    infrastructure entirely)

## Planned features

> Note: These are all doable in calling code, but some of these should be useful in the core
> library.

  * transitive/bulk operations on artifacts
    - pre-download all files needed to do off-line resolution later
    - gather the full maven universe implied by the offered initial artifacts
    - identify diamond dependency skew and other dependency conflicts
  * Download -sources artifacts and possibly other sub-artifacts.
  * more useful CLI functionality

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
