#!/usr/bin/env bash
# Install bazelisk, if not already present.
# Assume that PLATFORM is in the environment presented to this script.

if [[ ! -e ${HOME}/bin/bazel ]]; then
  cd ""${HOME}"" || exit
  wget https://github.com/bazelbuild/bazelisk/releases/download/v${BAZELISK_VERSION}/bazelisk-${PLATFORM}-amd64
  mkdir -p "${HOME}/bin"
  install "${HOME}/bazelisk-${PLATFORM}-amd64" "${HOME}/bin/bazel"
  chmod a+x "${HOME}/bin/bazel"
else
  echo "bazelisk (bazel) already installed, skipping download."
fi