# Copyright (C) 2020 Square, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
# in compliance with the License. You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed under the License
# is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
# or implied. See the License for the specific language governing permissions and limitations under
# the License.

# Main library metadata:
# library version - change in release branches. This should always be "HEAD-SNAPSHOT" at main HEAD
load("//tools/release:release_metadata.bzl", "developer", "metadata")

LIBRARY_VERSION = "HEAD-SNAPSHOT"  # Don't refactor this without altering tools/deploy.kts
LIBRARY_METADATA = metadata(
    name = "Maven Archeologist",
    description = "A thin API for resolving and downloading Maven artifacts and metadata",
    group_id = "com.squareup.tools.build",
    artifact_id = "maven-archeologist",
    # library version - change in release branches.
    # This should always be "HEAD-SNAPSHOT" at main HEAD
    version = LIBRARY_VERSION,
    target = "//src/main/java/com/squareup/tools/maven/resolution",
    license = "Apache-2.0",  # SPDX token for Apache 2.0
    github_slug = "square/maven-archeologist",
    developers = [developer("cgruber", "Christian Gruber", "gruber@squareup.com")],
)

# What language compliance levels are we configuring
JAVA_LANGUAGE_LEVEL = "1.8"
KOTLIN_LANGUAGE_LEVEL = "1.5"

# What version of kotlin are we using
KOTLIN_VERSION = "1.5.32"
KOTLINC_RELEASE_SHA = "2e728c43ee0bf819eae06630a4cbbc28ba2ed5b19a55ee0af96d2c0ab6b6c2a5"

# what version of the kotlin rules are we using
KOTLIN_RULES_VERSION = "v1.6.0-RC-2"
KOTLIN_RULES_FORK = "bazelbuild"
KOTLIN_RULES_SHA = "88d19c92a1fb63fb64ddb278cd867349c3b0d648b6fe9ef9a200b9abcacd489d"
KOTLIN_RULES_URL = "https://github.com/{fork}/rules_kotlin/releases/download/{version}/rules_kotlin_release.tgz".format(
    fork = KOTLIN_RULES_FORK,
    version = KOTLIN_RULES_VERSION,
)

MAVEN_REPOSITORY_RULES_VERSION = "2.0.0-alpha-5"
MAVEN_REPOSITORY_RULES_SHA = "fde80cafa02a2c034cc8086c158f500e7b6ceb16d251273a6cc82f1c0723e0e8"

MAVEN_LIBRARY_VERSION = "3.6.3"

DIRECT_ARTIFACTS = {
    "com.github.ajalt:clikt:2.6.0": {"insecure": True},
    "com.google.truth:truth:1.0": {
        "insecure": True,
        "testonly": True,
        "exclude": ["com.google.auto.value:auto-value-annotations"],
    },
    "com.google.guava:guava:27.1-jre": {
        "insecure": True,
        "testonly": True,
        "exclude": ["com.google.guava:failureaccess", "com.google.guava:listenablefuture"],
    },
    "com.squareup.okhttp3:okhttp:4.7.2": {"insecure": True},
    "com.squareup.okio:okio:2.6.0": {"insecure": True},
    "com.squareup.moshi:moshi:1.9.3": {"insecure": True},
    "com.squareup.moshi:moshi-kotlin:1.9.3": {"insecure": True},
    "org.apache.maven:maven-artifact:%s" % MAVEN_LIBRARY_VERSION: {"insecure": True},
    "org.apache.maven:maven-builder-support:%s" % MAVEN_LIBRARY_VERSION: {"insecure": True},
    "org.apache.maven:maven-model:%s" % MAVEN_LIBRARY_VERSION: {"insecure": True},
    "org.apache.maven:maven-model-builder:%s" % MAVEN_LIBRARY_VERSION: {
        "insecure": True,
        "exclude": ["javax.inject:javax.inject", "org.eclipse.sisu:org.eclipse.sisu.inject"],
    },
    "junit:junit:4.13": {"insecure": True, "testonly": True},
}

TRANSITIVE_ARTIFACTS = [
    "com.google.code.findbugs:jsr305:3.0.2",
    "com.google.errorprone:error_prone_annotations:2.3.1",
    "com.google.guava:failureaccess:1.0.1",
    "com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava",
    "com.google.j2objc:j2objc-annotations:1.1",
    "com.googlecode.java-diff-utils:diffutils:1.3.0",
    "org.apache.commons:commons-lang3:3.8.1",
    "org.checkerframework:checker-compat-qual:2.5.5",
    "org.checkerframework:checker-qual:2.5.2",
    "org.codehaus.mojo:animal-sniffer-annotations:1.17",
    "org.codehaus.plexus:plexus-interpolation:1.26",
    "org.codehaus.plexus:plexus-utils:3.3.0",
    "org.hamcrest:hamcrest-core:1.3",
    "org.jetbrains.kotlin:kotlin-stdlib-common:%s" % KOTLIN_VERSION,
    "org.jetbrains.kotlin:kotlin-stdlib:%s" % KOTLIN_VERSION,
    "org.jetbrains.kotlin:kotlin-reflect:%s" % KOTLIN_VERSION,
    "org.jetbrains:annotations:13.0",
]

def maven_artifacts():
    artifacts = {}
    artifacts.update(DIRECT_ARTIFACTS)
    for artifact in TRANSITIVE_ARTIFACTS:
        artifacts.update({artifact: {"insecure": True}})
    return artifacts
