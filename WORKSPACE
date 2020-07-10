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
workspace(name = "maven_archeologist")

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load(
    "//:versions.bzl",
    "KOTLINC_RELEASE_SHA",
    "KOTLINC_RELEASE_URL",
    "KOTLIN_RULES_SHA",
    "KOTLIN_RULES_URL",
    "MAVEN_REPOSITORY_RULES_SHA",
    "MAVEN_REPOSITORY_RULES_VERSION",
    "maven_artifacts",
)

# Load the kotlin rules repository, and setup kotlin rules and toolchain.
http_archive(
    name = "io_bazel_rules_kotlin",
    sha256 = KOTLIN_RULES_SHA,
    urls = [KOTLIN_RULES_URL],
)

load("@io_bazel_rules_kotlin//kotlin:kotlin.bzl", "kotlin_repositories", "kt_register_toolchains")

kotlin_repositories(compiler_release = {
    "urls": [KOTLINC_RELEASE_URL],
    "sha256": KOTLINC_RELEASE_SHA,
})

register_toolchains("//:kotlin_toolchain")

http_archive(
    name = "maven_repository_rules",
    sha256 = MAVEN_REPOSITORY_RULES_SHA,
    strip_prefix = "bazel_maven_repository-%s" % MAVEN_REPOSITORY_RULES_VERSION,
    urls = ["https://github.com/square/bazel_maven_repository/archive/%s.zip" % MAVEN_REPOSITORY_RULES_VERSION],
)

load("@maven_repository_rules//maven:maven.bzl", "maven_repository_specification")

maven_repository_specification(
    name = "maven",
    artifacts = maven_artifacts(),
    repository_urls = {"central": "https://repo1.maven.org/maven2"},
)
