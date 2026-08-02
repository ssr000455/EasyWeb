#!/bin/bash
# Cross-compile Rust library for Android
# Prerequisites: cargo-ndk, Android NDK, rustup targets
#
# Install:
#   cargo install cargo-ndk
#   rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android

set -e

# Build for all Android ABIs
echo "Building for arm64-v8a..."
cargo ndk -t arm64-v8a -o android/app/src/main/jniLibs build --release

echo "Building for armeabi-v7a..."
cargo ndk -t armeabi-v7a -o android/app/src/main/jniLibs build --release

echo "Building for x86_64..."
cargo ndk -t x86_64 -o android/app/src/main/jniLibs build --release

echo "Done! Native libraries are in android/app/src/main/jniLibs/"