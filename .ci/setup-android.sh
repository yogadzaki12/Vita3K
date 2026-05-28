#!/usr/bin/env bash
set -euo pipefail

android_api_level="${ANDROID_API_LEVEL:-35}"
android_build_tools="${ANDROID_BUILD_TOOLS:-36.0.0}"
android_ndk_version="${ANDROID_NDK_VERSION:-29.0.14206865}"

sudo apt-get update
sudo apt-get install -y \
    ninja-build \
    pkg-config \
    libusb-1.0-0-dev \
    zip \
    unzip

run_with_retry() {
    local max_attempts="$1"
    shift

    local attempt=1
    while true; do
        if "$@"; then
            return 0
        fi

        if [[ "$attempt" -ge "$max_attempts" ]]; then
            return 1
        fi

        local backoff=$((attempt * 15))
        echo "Command failed (attempt ${attempt}/${max_attempts}), retrying in ${backoff}s: $*" >&2
        sleep "$backoff"
        attempt=$((attempt + 1))
    done
}

repo_root="$(cd "$(dirname "$0")/.." && pwd)"

if command -v sdkmanager > /dev/null; then
    set +o pipefail
    yes | sdkmanager --licenses > /dev/null
    set -o pipefail
    sdkmanager --install \
        "platforms;android-${android_api_level}" \
        "build-tools;${android_build_tools}" \
        "ndk;${android_ndk_version}"

    sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
    if [[ -n "${sdk_root:-}" ]]; then
        export ANDROID_NDK_HOME="${sdk_root}/ndk/${android_ndk_version}"
        if [[ -n "${GITHUB_ENV:-}" ]]; then
            echo "ANDROID_NDK_HOME=${ANDROID_NDK_HOME}" >> "${GITHUB_ENV}"
        fi
    fi
fi

pushd "$repo_root" > /dev/null
export VCPKG_MAX_CONCURRENCY="${VCPKG_MAX_CONCURRENCY:-2}"
for triplet in arm64-android x64-android; do
    echo "Installing vcpkg dependencies for ${triplet} (VCPKG_MAX_CONCURRENCY=${VCPKG_MAX_CONCURRENCY})"
    if ! run_with_retry 3 vcpkg install --triplet "$triplet"; then
        echo "vcpkg install failed for ${triplet}. Dumping recent vcpkg build logs..." >&2
        find "${VCPKG_ROOT:-/usr/local/share/vcpkg}/buildtrees" -type f \( -name "*.log" -o -name "*.txt" \) -print | tail -n 60 >&2 || true
        exit 1
    fi
done
popd > /dev/null
