# Maven Archeologist

> Making handling maven Artifacts easy

A library to make resolving maven artifacts (from various repositories) easy.  The library itself
wraps the Maven resolver APIs and provides simple HTTP-based resolution. From this any number of
tools can be constructed, which require obtaining (and locally caching) maven artifacts, resolving
the "effective project model", validating hashes, etc.

***Latest Release:*** [![Maven Central][maven-badge]][maven-search] [![build][workflow-ci]][workflow-ci-link]

[maven-badge]: https://maven-badges.herokuapp.com/maven-central/com.squareup.tools.build/maven-archeologist/badge.svg?style=plastic
[maven-search]: https://search.maven.org/artifact/com.squareup.tools.build/maven-archeologist
[workflow-ci]: https://github.com/square/maven-archeologist/workflows/CI/badge.svg
[workflow-ci-link]: https://github.com/square/maven-archeologist/actions?query=workflow%3ACI

## Contents

  * [Usage](#usage)
    + [Add to a build](#add-to-a-build)
    + [Simplest Usage](#simplest-usage)
    + [Resolving Metadata](#resolving-metadata)
    + [Downloading Artifacts](#downloading-artifacts)
    + [Downloading Sources](#downloading-sources)
    + [Downloading Classified Sub-Artifacts](#downloading-classified-sub-artifacts)
    + [Adding Repositories](#adding-repositories)
    + [Local Artifact Cache](#local-artifact-cache)
    + [Proxies and fetching](#proxies-and-fetching)
    + [Comparing Artifact Versions](#comparing-artifact-versions)
  * [Demo CLI](#demo-cli)
  * [Possible uses](#possible-uses)
  * [Core Capabilities](#core-capabilities)
  * [Known Limitations](#known-limitations)
  * [Planned features](#planned-features)
  * [License](#license)

## Usage

The main entry point to the library, which wraps (and mostly hides) the Maven resolution
infrastructure, is `ArtifactResolver`.  It has two ways to use it - the simple one which just
downloads the relevant artifact and its POM file, and returns the file locations, or a slightly
more nuanced API, which lets you get resolved Maven metadata (but doesn't download the artifact).

### Add to a build

Maven-Archeologist is published as `com.squareup.tools.build:maven-archeologist:0.0.3.1` in
the Maven Central repository. Use your build system's standard import mechanism to bring in
that artifact. e.g.:

Gradle:
```groovy
dependencies {
  implementation 'com.squareup.tools.build:maven-archeologist:0.0.3.1'
}
```

Maven:
```xml
  <dependency>
    <groupId>com.squareup.tools.build</groupId>
    <artifactId>maven-archeologist</artifactId>
    <version>0.0.3.1</version>
  </dependency>
```

### Simplest Usage

In this variant, the API just downloads the POM and artifact files and their hash files, validates
the files, and returns POM and artifact files to you in a small data class you can destructure easily,
containing the pom file, the main artifact file, and optionally the sources file. 

```kotlin
val resolver = ArtifactResolver() // creates a resolver with repo list defaulting to Maven Central.
val (pom, artifact) = resolver.download("com.google.guava:guava:27.1-jre")
```

To also get source jars, you can do

```kotlin
val resolver = ArtifactResolver() // creates a resolver with repo list defaulting to Maven Central.
val (pom, artifact, sourcejar) = resolver.download("com.google.guava:guava:27.1-jre", downloadSources = true)
```

> Note: `sourcejar` above can be null if (a) `downloadSources = false` (the default), or (b) it could not
> be downloaded. If failing to download sources is a breaking condition, use the more rigorous metadata
> system below.  Likewise, if you need other classifiers than "sources" use the more rigorous metadata
> resolution system. 

### Resolving Metadata

An artifact can be resolved without automatically downloading via the following:

```kotlin
val resolver = ArtifactResolver() // creates a resolver with repo list defaulting to Maven Central.
val artifact = resolver.artifactFor("com.google.guava:guava:27.1-jre") // returns Artifact
val resolvedArtifact = resolver.resolveArtifact(artifact) // returns ResolvedArtifact
val dependencies: List<Dependency> = resolvedArtifact.model.dependencies
```

`resolveArtifact` returns a `ResolvedArtifact`, which contains the fully resolved model object,
specifically from the Maven APIs. The model includes all of the resolved metadata for that artifact,
such as resolved dependencies (including from dependencyManagement and properties substitutions).

### Downloading Artifacts

The resolved artifact can then also be used to fetch the main artifact.  The `ResolvedArtifact` has
properties defining abstract file references for the pom file `resolved.pom` (type `PomFile`) and
`resolved.main` (type `ArtifactFile`). These each have some important properties, namely the
maven relative path of the artifact (e.g. `com/google/guava/guava/18.0/guava-18.0.pom`), and
the local file reference into which the file will be (or has been) downloaded (e.g. 
`/Users/cgruber/.m2/repository/com/google/guava/18.0/guava-18.0.jar`). (See below for sources and
sub-artifacts).


The pom file will be downloaded and in the local cache, upon obtaining a `ResolvedArtifact`.  The main
artifact will only be fetched into the cache upon request, like so:

```kotlin
val result = resolver.download(resolvedArtifact) // returns FetchStatus

// if you care about whether it was a cache-hit or not, do this. Otherwise test for "is SUCCESSFUL"
when (result) {
  is SUCCESSFUL.FOUND_IN_CACHE -> { /* win! */ }
  is SUCCESSFUL.SUCESSFULLY_FETCHED -> { /* win, but remotely! */ }
  else -> { /* Handle error */ }
}
```

Once you get one of the two SUCCESSFUL signals shown above, the file will be available for access
in the `Path` reference in `resolved.main.localFile`, e.g.:

```kotlin
val pomLines = Files.readAllLines(resolved.pom.localFile) // do what you do with Path objects here.
val mainLines = Files.readAllLines(resolved.main.localFile) // do what you do with Path objects here.
```

### Downloading Sources

Source artifacts can be downloaded, similar to the main artifact, simply asking the resolver for them,
like so:

```kotlin
val result = resolver.downloadSources(resolvedArtifact) // returns FetchStatus
require(result.sources.localFile.exists()) { "File should have existed" }
val lines = Files.readAllLines(resolved.sources.localFile) // do what you do with Path objects here.
```

Sources can also be obtained by the simplified `download()` API like so:

```kotlin
val (pom, artifact, sourcejar) = resolver.download("com.google.guava:guava:27.1-jre", downloadSources = true)
val lines = Files.readAllLines(sourceJar) // do what you do with Path objects here.
```

### Downloading Classified Sub-Artifacts

Classified artifacts (artifacts with a classifier) requires a bit more information. Classified sub-artifacts
be fully described in the pom, but may not be. A classified file reference can be obtained from the resolved
artifact, and requested like so:

```kotlin
val artifact = resolver.artifactFor("foo.bar:bar:1.0") // assume this is a "jar" type
val resolved = resolver.resolveArtifact(artifact)
val classified = resolved.subArtifact("extra") // references bar-1.0-extra.jar
val status = resolver.downloadSubArtifact(classified) // 
if (status is SUCCESSFUL) {
  Files.readAllLines(classified.localFile).forEach { /* do line stuff */ }
} else { /* freak out */ }
```

Some classified sub-artifacts do not have the same file suffix as their main artifact. Such artifacts can
be referenced like this:

```kotlin
val artifact = resolver.artifactFor("foo.bar:bar:1.0") // assume this is a "jar" type
val resolved = resolver.resolveArtifact(artifact)
val classified = resolved.subArtifact("extra", "zip) // references bar-1.0-extra.zip
val classifiedStatus = resolver.downloadSubArtifact(classified) // 
if (status is SUCCESSFUL) {
  Files.readAllLines(classified.localFile).forEach { /* do line stuff */ }
} else { /* freak out */ }
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

### Local Artifact Cache

By default, artifacts are cached in `${HOME}/.m2/repository`, exactly as Maven 3 or Gradle would do,
but this can be changed (per resolver instance) like so:

```kotlin
val resolver = ArtifactResolver(cacheDir = fs.getPath("/some/cache/dir"))
```

### Proxies and fetching

Maven Archeologist uses OkHttp 4.0 and HttpArtifactFetcher can be created with a lambda that
supplies a configured `OkHttpClient`. While this can be overridden by any lambda that takes
a URL string and returns an `OkHttpClient` the default mechanism is sensitive to two environment
variables ("HTTP_PROXY" and "NO_PROXY")

#### Default Environment Variables

> Environment variables are used in the default configuration, as there is often a need to have
> a different context in CI without altering code, and maven resolution is often required in build
> systems used in CI

To use a proxy in the default setup, set the a variable in the environment in which the app you
wish to use will run with the url of the proxy service (including, optionally, a
username/password). e.g.:

```sh
setenv HTTP_PROXY=localhost:8080
artifact_resolver_cli some:artifact:1.0
```

Assuming `artifact_resolver_cli` is a maven-archeologist client, it will route fetches through
the proxy.

Because some environments need to disallow certain addresses from being routed through the
proxy (for security or performance reasons), setting a list of url matching infixes into
the environment variable `NO_PROXY` will allow maven-archeologist to route prefix-matching 

```sh
setenv HTTP_PROXY=localhost:8080
setenv NO_PROXY=.repo.corp,.repo2.corp
artifact_resolver_cli some:artifact:1.0
```

This would cause artifacts fetched/resolved from (for example) `https://internal.repo.corp/repo` and
`https://internal.repo2.corp/repo` to not go through the proxy, but one fetched/resolved via
`https://repo1.maven.org/maven2` to pass through the proxy.

#### Custom Client Provision

If this default machanism isn't appropriate, different heuristics are needed, or for any other reason,
an `HttpArtifactFetcher` can be constructed with a lambda that supplies an OkHttpClient appropriate to
the requested url and whatever context signals you have available. e.g., assuming:


```kotlin
object HttpClientHelper {
  val proxiedClient = OkHttpClient.builder()
    // set proxy based on an env var CI_ENVIRONMENT
    .build()
  fun clientForUrl(url: String) = proxiedClient // doesn't care about URL
}

... later ...

val cacheDir = fs.getPath("/path/to/local/cache")
val fetcher = HttpArtifactFetcher(cacheDir = cacheDir, HttpClientHelper::clientForUrl)
val resolver = ArtifactResolver(cacheDir = fs.getPath("/some/cache/dir"))
```

or

```kotlin
object HttpClientHelper {
  val unproxied OkHttpClient.builder().build()
  val proxied = OkHttpClient.builder()
    // set settings
    .build()
  fun clientForUrl(url: String) = with(URL(url)) {
    when {
      this.host.startsWith("foo.bar") -> unproxied
      this.host.endsWith("blah.foo") -> unproxied
      else -> proxied
    }
}

... later ...

val cacheDir = fs.getPath("/path/to/local/cache")
val fetcher = HttpArtifactFetcher(cacheDir = cacheDir, HttpClientHelper::clientForUrl)
val resolver = ArtifactResolver(cacheDir = fs.getPath("/some/cache/dir"))

```

This mechanism can be used to create as many kinds of clients, or as few, as sensibly works
for your system. It can return a fresh client every time, or reuse clients with the same
configuration, hold builders - whatever you need to configure a request appropriately.

### Comparing Artifact Versions

maven-archeologist has a convenience for representing maven versions in a way that they can be
compared according to semantic versioning, or at least the maven 3 variant, rather than merely
lexically. MavenVersion extends `Comparable<MavenVersion>` and can be used in comparison checks,
ordered collections, etc.  Their toString() simply prints the raw string representation.

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

  * Does not do any traversal (this can be done in calling code) of dependencies or other
    transitive operations
  * No multithreaded operations (though calling code can build a parallel graph walk around it)
    - file writes DO use a "write to temp file, atomically move" strategy, so generally the
      library should be tolerant of race-condition in resolution/download.
  * Has a crappy heuristic (with a hack for bundle) for converting packaging->suffix
    - Doesn't resolve plugin metadata that might configure things like that.
    - No clear strategy for fixing this, as plugins can hack this sort of thing programmatically.
  * The CLI is super limited as a demo-app.
  * Might need some more ability to configure deeper maven infrastucture (without bailing out
    of the wrapper infrastructure entirely)
  * APIs don't conform to a rigorous graph-theory system such as `com.google.common.graph`, which would
    allow fun things like applying lookup/cycle-detection algorithms in a very generalized way.
    - This would be a cadillac feature - it's not clear that the cost of wrapping the innards of
      maven `Model` objects is really worth the indirection, nor the additional dependency. It could
      buy some advanced features, but we'd need to know they were needed.

## Planned features

> Note: These are all doable in calling code, but some of these should be useful in the core
> library.

  * transitive/bulk operations on artifacts
    - pre-download all files needed to do off-line resolution later
    - gather the full maven universe implied by the offered initial artifacts
    - identify diamond dependency skew and other dependency conflicts or graph analysis.
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

## About the project

### Why Archeologist?

Because the library handles artifacts, and references to certain movies starring Harrison Ford
might garner trademark concerns.

### Why not Archaeologist?

Because 'murica! More seriously, both spellings are accepted english, and while the primary author
is Canadian, he lives in the US. 
