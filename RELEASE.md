# Release Instructions

> Note: Versions should be semantic versioning maj.min.patch style, except for the default
> HEAD-SNAPSHOT which is the version used on the `main` branch

Preconditions:

  1. You must have a username on oss.sonatype.org.
    - If you need one, sign up at issues.sonatype.org.
  2. Your sonatype identity must have permissions to publish to org.squareup.
    - Fill out a [ticket] and tag in a square opensource contributor who already has access.
  3. You must have a PGP private key (typically using GnuPG) whose key you have uploaded
     to the main keyservers.
     - Follow the instructions here: https://central.sonatype.org/pages/working-with-pgp-signatures.html
     - Pay especial attention to https://central.sonatype.org/pages/working-with-pgp-signatures.html#distributing-your-public-key
     - use `gpg --list-keys` to see the keys available.
  4. Obviously have gpg, bazelisk, java, and other preconditions of building installed.

Steps:
  1. Prepare the repo
      1. `git clone` repository or update with `git fetch`
      2. `git checkout origin/main`
      3. `git switch -c release-<version>` # create a release branch

  2. Prepare the release artifacts (and test the system)
      1. Edit the `versions.bzl` file updating the LIBRARY_VERSION var to the release version.
      2. `git commit -m "Prepare to release version <version>" versions.bzl`
      3. `git tag v<version>` # version such as v0.0.6. Include the v. 
      4. `bazel build //... && bazel test //...`

  3. Deploy the artifacts
      1. ```
          tools/deploy.kts \
             --key <yourgpgkey> \
             --username <yourusername> \
             --password <yourpassword> \
         ```
          - username/password are for oss.sonatype.org
      2. Enter your gnupg passphrase for the given PGP key (if you set a passphrase for it).
      3. Wait for the deployment to push all the files to sonatype. 
      4. Log in to `oss.sonatype.org`
      5. Search for "staging repositories"
      6. Find a `comsquareup-<somenumber>` repo.
          - If more than one, select the one exists, select each and in the bottom frame, select the
            `content` tab, and open the file system tree to make sure it's the project you want to
            deploy.
      7. Select the repository to release and hit "close". Enter a message like
         "Prepare to release Maven Archeologist 0.0.6"
      8. Wait, occasionally hitting refresh (the button in the web-page, not the browser refresh)
         - when it's done, the buttons "release" and "drop" should be available, and "close" should
           be greyed out. 
      9. Select the repository to release and hit "release". Enter a message like
         "Release Maven Archeologist 0.0.6"
      10. Wait. For minutes. Go get a coffee. Hit refresh. When it's done, the repository should be
          gone from the list.
      11. Sync to the common mirrors/websites listing artifacts can take as much as an hour.
          - The artifacts should be available directly on https://repo1.maven.org/maven2/ a minute
            or two after the repository is "released" on sonatype.

  4. Finish the release
      1. `git push --tags` # push the release tag
      2. On the [releases page](https://github.com/square/maven-archeologist/releases) find the tag
         and edit it. 
          - For minor releases, just list the incremental change log since the last relevant release
            and link to the readme.
          - For major releases, list the full featureset (summary) and key differences and link to
            the readme and changelog
      3. Update the README.md to ensure any usage information, versions listed, hashes, and
         feature descriptions are reflected.

  5. Announce (wherever - twitter, blogs, etc.)

Problems and Solutions:
  * I get a bad tty reported when gpg asks for my passphrase.
      - prefix your `tools/deploy.kts` command with `GPG_TTY=$(tty) ` and then the passphrase
        screen should work normally. 
          - Note: the tty stuff works around a bug in gnupg being run deep in a toolchain
      - Other solutions are recommended here: https://stackoverflow.com/questions/14114528/avoid-gpg-signing-prompt-when-using-maven-release-plugin
  * `tools/deploy.kts` fails because of missing pom file or other files.
      - Make sure you run `bazel build //...` so all the files are built.



[ticket]: https://issues.sonatype.org/secure/CreateIssue!default.jspa