# Maven archeology

A library to make resolving maven artifacts (from various repositories) easy.  The library itself
wraps the maven resolver APIs and provides simple http based resolution. From this any number of
tools can be constructed, which require obtaining (and locally caching) maven artifacts, resolving
the "effective project model", validating hashes, etc.

## Usage

The main entry point to the library, which wraps (and mostly hides) the maven resolution
infrastructure, is `ArtifactResolver`.  It has two ways to use it - the simple one which just
downloads the relevant artifact and its pom file, and returns the file locations, or a slightly
more nuanced API, which lets you get resolved maven metadata (but doesn't downlaod the artifact)

### simple usage

```kotlin
val resolver = ArtifactResolver() // creates a resolver with repo list defaulting to maven central
val (pom, artifact) = resolver.download("com.google.guava:guava:27.1-jre")
```

### flexible usage with metadata

```kotlin
val resolver = ArtifactResolver() // creates a resolver with repo list defaulting to maven central
val artifact = resolver.artifactFor("com.google.guava:guava:27.1-jre") // returns Artifact
val resolvedArtifact = resolver.resolveArtifact(artifact) // returns ResolvedArtifact
val result = resolver.download(resolvedArtifact) // returns FetchStatus
if (result is SUCCESSFUL) { /* win! */ }
```

### repositories

The resolver defaults to resolving against maven central. Specifying repositories is as simple as:

```kotlin
val repo1 = Repository().apply {
  id = "some-identifier"
  releases = RepositoryPolicy().apply { enabled = "true" }
  url = "https://some.server/path/to/repo" // At present, only http/https are supported.
}
val repo2 = ...
val resolver = ArtifactResolver(repositories = listOf(rep1, rep2))
```
> Note: This is one of the rare times you'll directly interact with maven's internal APIs, except
> for interacting with the `Model` object if you resolve the effective-model.

Reasonably popular repositories have been pre-defined in the `Repositories` type, e.g.
`Repositories.MAVEN_CENTRAL`

### local artifact cache
> (maven Local Repository)

By default, artifacts are cached in ${HOME}/.m2/repository, but this can be changed (per resolver
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
