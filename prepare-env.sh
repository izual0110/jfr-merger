#!/usr/bin/env bash

set -euo pipefail

JFR_ASYNC_PROFILER_VERSION="4.1"
ASYNC_PROFILER_PLATFORM="linux-x64"
LIB_DIR="lib"
CLOJURE_INSTALLER="linux-install.sh"

JFR_CONVERTER_URL="https://github.com/async-profiler/async-profiler/releases/download/v${JFR_ASYNC_PROFILER_VERSION}/jfr-converter.jar"
CLOJURE_INSTALLER_URL="https://github.com/clojure/brew-install/releases/latest/download/${CLOJURE_INSTALLER}"

if command -v dnf >/dev/null 2>&1; then
  echo "Installing rlwrap via dnf"
  dnf install -y rlwrap
elif command -v apt-get >/dev/null 2>&1; then
  echo "Installing rlwrap via apt-get"
  apt-get update
  apt-get install -y rlwrap
else
  echo "dnf and apt-get not found; skipping rlwrap installation" >&2
fi

mkdir -p "${LIB_DIR}"

if [[ ! -f "${LIB_DIR}/jfr-converter.jar" ]]; then
  echo "Downloading jfr-converter.jar"
  curl -fsSL -o "${LIB_DIR}/jfr-converter.jar" "${JFR_CONVERTER_URL}"
else
  echo "${LIB_DIR}/jfr-converter.jar already exists; skipping download"
fi

if [[ ! -f "${CLOJURE_INSTALLER}" ]]; then
  echo "Downloading Clojure installer"
  curl -fsSL -o "${CLOJURE_INSTALLER}" "${CLOJURE_INSTALLER_URL}"
else
  echo "${CLOJURE_INSTALLER} already exists; skipping download"
fi

chmod +x "${CLOJURE_INSTALLER}"

echo "Running Clojure installer"
"./${CLOJURE_INSTALLER}"

echo "Environment preparation complete"
