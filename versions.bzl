# What language compliance levels are we configuring
JAVA_LANGUAGE_LEVEL = "1.8"
KOTLIN_LANGUAGE_LEVEL = "1.3"

# What version of kotlin are we using
KOTLIN_VERSION = "1.3.71"
KOTLINC_RELEASE_SHA = "7adb77dad99c6d2f7bde9f8bafe4c6244a04587a8e36e62b074d00eda9f8e74a"
KOTLINC_RELEASE_URL = "https://github.com/JetBrains/kotlin/releases/download/v{v}/kotlin-compiler-{v}.zip".format(v = KOTLIN_VERSION)


# what version of the kotlin rules are we using
KOTLIN_RULES_VERSION = "sq_05"
KOTLIN_RULES_FORK = "cgruber"
KOTLIN_RULES_SHA = "3b9df70421660bd22d90841e57531d2df41a8123d427c04dc00ff2d866ac9c63"
KOTLIN_RULES_URL = "https://github.com/{fork}/rules_kotlin/releases/download/{version}/rules_kotlin.tgz".format(
    fork = KOTLIN_RULES_FORK,
    version = KOTLIN_RULES_VERSION,
)

MAVEN_REPOSITORY_RULES_VERSION = "1.1-rc1"
MAVEN_REPOSITORY_RULES_SHA = "92db5ef2eaee8281e9e6c136adce757b129b45b198a9f5b4bf6df278dc95da18"

MAVEN_LIBRARY_VERSION = "3.6.3"

DIRECT_ARTIFACTS = {
    "com.beust:jcommander:1.78": {"insecure": True},
    "com.google.truth:truth:1.0": {
        "insecure": True,
        "testonly": True,
        "exclude": ["com.google.auto.value:auto-value-annotations"]
    },
    "com.google.guava:guava:27.1-jre": {
        "insecure": True,
        "testonly": True,
        "exclude": ["com.google.guava:failureaccess", "com.google.guava:listenablefuture"],
    },
    "com.squareup.okhttp3:okhttp:4.4.1": {"insecure": True},
    "org.apache.maven:maven-artifact:%s" % MAVEN_LIBRARY_VERSION: {"insecure": True},
    "org.apache.maven:maven-builder-support:%s" % MAVEN_LIBRARY_VERSION: {"insecure": True},
    "org.apache.maven:maven-model:%s" % MAVEN_LIBRARY_VERSION: {"insecure": True},
    "org.apache.maven:maven-model-builder:%s" % MAVEN_LIBRARY_VERSION: {
        "insecure": True,
        "exclude": ["javax.inject:javax.inject", "org.eclipse.sisu:org.eclipse.sisu.inject"]
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
    "com.squareup.okio:okio:2.4.3",
    "org.apache.commons:commons-lang3:3.8.1",
    "org.checkerframework:checker-compat-qual:2.5.5",
    "org.checkerframework:checker-qual:2.5.2",
    "org.codehaus.mojo:animal-sniffer-annotations:1.17",
    "org.codehaus.plexus:plexus-interpolation:1.26",
    "org.codehaus.plexus:plexus-utils:3.3.0",
    "org.hamcrest:hamcrest-core:1.3",
    "org.jetbrains.kotlin:kotlin-stdlib-common:%s" % KOTLIN_VERSION,
    "org.jetbrains.kotlin:kotlin-stdlib:%s" % KOTLIN_VERSION,
    "org.jetbrains:annotations:13.0",
]

def maven_artifacts():
    artifacts = {}
    artifacts.update(DIRECT_ARTIFACTS)
    for artifact in TRANSITIVE_ARTIFACTS:
        artifacts.update({ artifact: {"insecure": True}})
    return artifacts