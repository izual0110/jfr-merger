#!/usr/bin/env bash

set -euo pipefail

JFR_ASYNC_PROFILER_VERSION="4.3"
ASYNC_PROFILER_PLATFORM="linux-x64"
LIB_DIR="lib"
CLOJURE_INSTALLER="linux-install.sh"

JFR_CONVERTER_URL="https://github.com/async-profiler/async-profiler/releases/download/v${JFR_ASYNC_PROFILER_VERSION}/jfr-converter.jar"
CLOJURE_INSTALLER_URL="https://github.com/clojure/brew-install/releases/latest/download/${CLOJURE_INSTALLER}"

HAS_RLWRAP=false
HAS_CLJ=false
HAS_CONVERTER=false

if command -v rlwrap >/dev/null 2>&1; then
  HAS_RLWRAP=true
fi

if command -v clj >/dev/null 2>&1; then
  HAS_CLJ=true
fi

mkdir -p "${LIB_DIR}"

if [[ ! -f "${LIB_DIR}/jfr-converter.jar" ]]; then
  echo "Downloading jfr-converter.jar"
  curl -fsSL -o "${LIB_DIR}/jfr-converter.jar" "${JFR_CONVERTER_URL}"
else
  echo "${LIB_DIR}/jfr-converter.jar already exists; skipping download"
  HAS_CONVERTER=true
fi

if ${HAS_RLWRAP} && ${HAS_CLJ} && ${HAS_CONVERTER}; then
  echo "Prerequisites already present; nothing to do."
  exit 0
fi

if ! ${HAS_RLWRAP}; then
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
fi

if ! ${HAS_CLJ}; then
  if [[ ! -f "${CLOJURE_INSTALLER}" ]]; then
    echo "Downloading Clojure installer"
    curl -fsSL -o "${CLOJURE_INSTALLER}" "${CLOJURE_INSTALLER_URL}"
  else
    echo "${CLOJURE_INSTALLER} already exists; skipping download"
  fi

  chmod +x "${CLOJURE_INSTALLER}"
  echo "Running Clojure installer"
  "./${CLOJURE_INSTALLER}"
fi

echo "Environment preparation complete"
