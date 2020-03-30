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
def metadata(
        group_id,
        artifact_id,
        version,
        target,
        license,
        github_slug,
        developers = [],
        description = None,
        name = None):
    """ Generates a known struct which can be consumed to generate release packaging. """
    if not developers:
        fail("Must specify at least one developer.")
    return struct(
        name = name if name != None else artifact_id,
        description = description,
        group_id = group_id,
        artifact_id = artifact_id,
        version = version,
        target = target,
        github_slug = github_slug,
        license = license,
        developers = developers,
    )

def developer(id, name, email, url = None):
    """ Generates an id/string pair which can be consumed by maven_pom"""
    if not url:
        url = "http://github.com/{id}".format(id = id)
    return "{id}::{name}::{email}::{url}".format(id = id, name = name, email = email, url = url)
