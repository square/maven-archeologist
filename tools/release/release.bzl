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
load("//:versions.bzl", "maven_artifacts")

def maven_pom_from_metadata(
        name,
        metadata):
    """ Generates a pom file from given metadata created from release_metadata.bzl """
    maven_pom(
        name = name,
        artifact_name = metadata.name,
        group_id = metadata.group_id,
        artifact_id = metadata.artifact_id,
        version = metadata.version,
        target = metadata.target,
        description = metadata.description,
        license = metadata.license,
        github_slug = metadata.github_slug,
        developers = metadata.developers,
    )

def sources_jar_from_metadata(
        name,
        metadata):
    """ Generates a pom file from given metadata created from release_metadata.bzl """
    sources_jar(
        name = name,
        group_id = metadata.group_id,
        artifact_id = metadata.artifact_id,
        version = metadata.version,
        target = metadata.target,
    )

def deployment_jar_from_metadata(
        name,
        metadata):
    """ Generates a pom file from given metadata created from release_metadata.bzl """
    deployment_jar(
        name = name,
        group_id = metadata.group_id,
        artifact_id = metadata.artifact_id,
        version = metadata.version,
        target = metadata.target,
    )

DEPENDENCY_TEMPLATE = """
    <dependency>
      <groupId>{group_id}</groupId>
      <artifactId>{artifact_id}</artifactId>
      <version>{version}</version>{scope}
    </dependency>"""
DEVELOPER_TEMPLATE = """
    <developer>
      <id>{id}</id>
      <name>{name}</name>
      <email>{email}</email>
      <url>{url}</url>
    </developer>"""

RUNTIME_SCOPE_TAG = """
      <scope>runtime</scope>"""

POM_TEMPLATE = """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/maven-v4_0_0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>{group_id}</groupId>
  <artifactId>{artifact_id}</artifactId>
  <version>{version}</version>
  <name>{name}</name>
  <description>{description}</description>
  <url>http://github.com/{github_slug}</url>
  <developers>{developers}
  </developers>
  <dependencies>{dependencies}
  </dependencies>
  <licenses>
    <license>
      <name>{license}</name>
      <url>https://spdx.org/licenses/{license}.txt</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <scm>
    <connection>scm:git:git://github.com/{github_slug}.git</connection>
    <developerConnection>scm:git:git@github.com:{github_slug}.git</developerConnection>
    <url>https://github.com/{github_slug}</url>
  </scm>
</project>
"""

DirectDepsInfo = provider(
    fields = {
        "direct_deps": "directly declared deps in this library",
        "direct_runtime_deps": "directly declared runtime_deps in this library",
    },
)

def _direct_deps_aspect_info(target, aspect_ctx):
    """Scans targets with deps/runtime and extracts the direct deps/runtime_deps """
    has_deps = hasattr(aspect_ctx.rule.attr, "deps")
    has_runtime_deps = hasattr(aspect_ctx.rule.attr, "runtime_deps")
    deps = aspect_ctx.rule.attr.deps if has_deps else []
    runtime_deps = aspect_ctx.rule.attr.runtime_deps if has_runtime_deps else []
    if bool(deps) and bool(has_runtime_deps):
        return [DirectDepsInfo(direct_deps = deps, direct_runtime_deps = runtime_deps)]
    elif bool(deps):
        return [DirectDepsInfo(direct_deps = deps)]
    elif bool(deps):
        return [DirectDepsInfo(direct_runtime_deps = runtime_deps)]
    else:
        return []

_direct_deps_aspect = aspect(
    implementation = _direct_deps_aspect_info,
    attr_aspects = ["deps", "runtime_deps"],
)

def _process_dep(dep, runtime = False):
    # This relies heavily on maven_bazel_repository assumptions and wouldn't work. This should
    # actually be some sort of MavenMetadataInfo provider created by any maven import tool, once
    # we have shared providers like that. For now, I'm parsing a known path structure.
    dir = _only_dir(dep)
    path_elements = dir.split("/")[3::]
    return struct(
        version = path_elements[-1],
        artifact_id = path_elements[-2],
        group_id = ".".join(path_elements[0:-2]),
        runtime = runtime,
    )

def _only_dir(dep):
    # Relies on the current bazel_maven_repository logic. Might need to be changed to scan for
    # the right file if there are more than one
    return dep.files.to_list()[0].dirname

def _process_developer(dev):
    (id, name, email, url) = dev.split("::")
    return DEVELOPER_TEMPLATE.format(
        id = id,
        name = name,
        email = email,
        url = url,
    )

def _generate_pom_rule_impl(ctx):
    filename = ctx.attr.filename if ctx.attr.filename else "%s-%s.pom" % (
        ctx.attr.artifact_id,
        ctx.attr.version,
    )
    out = ctx.actions.declare_file(filename)
    target = ctx.attr.target[DirectDepsInfo]
    deps = (
        [_process_dep(d) for d in target.direct_deps] +
        [_process_dep(d, runtime = True) for d in target.direct_runtime_deps]
    )
    dependencies = "".join([DEPENDENCY_TEMPLATE.format(
        group_id = dep.group_id,
        artifact_id = dep.artifact_id,
        version = dep.version,
        scope = RUNTIME_SCOPE_TAG if dep.runtime else "",
    ) for dep in deps])
    developers = "".join([_process_developer(d) for d in ctx.attr.developers])

    artifacts = maven_artifacts()
    ctx.actions.write(
        output = out,
        content = POM_TEMPLATE.format(
            group_id = ctx.attr.group_id,
            artifact_id = ctx.attr.artifact_id,
            version = ctx.attr.version,
            name = ctx.attr.artifact_name,
            description = ctx.attr.description,
            dependencies = dependencies,
            license = ctx.attr.license,
            github_slug = ctx.attr.github_slug,
            developers = developers,
        ),
    )
    return [DefaultInfo(files = depset([out]))]

def _generate_sources_jar_impl(ctx):
    filename = ctx.attr.filename if ctx.attr.filename else "%s-%s-sources.jar" % (
        ctx.attr.artifact_id,
        ctx.attr.version,
    )
    out = ctx.actions.declare_file(filename)
    target = ctx.attr.target
    source_jar = target[JavaInfo].source_jars[0]
    ctx.actions.run_shell(
        inputs = [source_jar],
        outputs = [out],
        progress_message = "Preparing %s" % out.path,
        command = "cp %s %s" % (source_jar.path, out.path),
    )
    return [DefaultInfo(files = depset([out]))]

def _generate_deployment_jar_impl(ctx):
    filename = ctx.attr.filename if ctx.attr.filename else "%s-%s.jar" % (
        ctx.attr.artifact_id,
        ctx.attr.version,
    )
    out = ctx.actions.declare_file(filename)
    target = ctx.attr.target
    deployment_jar = target[JavaInfo].compile_jars.to_list()[0]
    ctx.actions.run_shell(
        inputs = [deployment_jar],
        outputs = [out],
        progress_message = "Preparing %s" % out.path,
        command = "cp %s %s" % (deployment_jar.path, out.path),
    )
    return [DefaultInfo(files = depset([out]))]

maven_pom = rule(
    implementation = _generate_pom_rule_impl,
    attrs = {
        "filename": attr.string(doc = "The name of the file to be generated (optional)"),
        "group_id": attr.string(doc = "The maven groupId", mandatory = True),
        "artifact_id": attr.string(doc = "The maven artifactId", mandatory = True),
        "version": attr.string(doc = "The maven version", mandatory = True),
        "artifact_name": attr.string(doc = "The descriptive name of the library"),
        "description": attr.string(doc = "A more full description of the library"),
        "target": attr.label(
            aspects = [_direct_deps_aspect],
            doc = "The target library from which to generate metadata",
        ),
        "license": attr.string(doc = "The SPDX name for the associated license."),
        "github_slug": attr.string(doc = "Github user/project spec foro this artifact"),
        "developers": attr.string_list(doc = "Structures to create the developer section"),
    },
)

sources_jar = rule(
    implementation = _generate_sources_jar_impl,
    attrs = {
        "filename": attr.string(doc = "The name of the file to be generated (optional)"),
        "group_id": attr.string(doc = "The maven groupId", mandatory = True),
        "artifact_id": attr.string(doc = "The maven artifactId", mandatory = True),
        "version": attr.string(doc = "The maven version", mandatory = True),
        "target": attr.label(
            aspects = [_direct_deps_aspect],
            doc = "The target library from which to generate metadata",
        ),
    },
)

deployment_jar = rule(
    implementation = _generate_deployment_jar_impl,
    attrs = {
        "filename": attr.string(doc = "The name of the file to be generated (optional)"),
        "group_id": attr.string(doc = "The maven groupId", mandatory = True),
        "artifact_id": attr.string(doc = "The maven artifactId", mandatory = True),
        "version": attr.string(doc = "The maven version", mandatory = True),
        "target": attr.label(
            aspects = [_direct_deps_aspect],
            doc = "The target library from which to generate metadata",
        ),
    },
)
