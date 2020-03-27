workspace(name = "maven_archologist")
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load(
    "//:versions.bzl",
    "KOTLINC_RELEASE_URL",
    "KOTLINC_RELEASE_SHA",
    "KOTLIN_RULES_URL",
    "KOTLIN_RULES_SHA",
    "maven_artifacts",
    "MAVEN_REPOSITORY_RULES_VERSION",
    "MAVEN_REPOSITORY_RULES_SHA",
)

# Load the kotlin rules repository, and setup kotlin rules and toolchain.
http_archive(
    name = "io_bazel_rules_kotlin",
    urls = [ KOTLIN_RULES_URL ],
    sha256 = KOTLIN_RULES_SHA,
)
load("@io_bazel_rules_kotlin//kotlin:kotlin.bzl", "kotlin_repositories", "kt_register_toolchains")

kotlin_repositories(compiler_release = {
    "urls": [ KOTLINC_RELEASE_URL ],
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
    cache_poms_insecurely = True,
    artifacts = maven_artifacts(),
)