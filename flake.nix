{
  description = "Inkber - Uber Rides + Eats WebView optimised for e-ink (Mudita Kompakt)";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config = {
            allowUnfree = true;
            android_sdk.acceptLicense = true;
          };
        };

        # Compose an Android SDK with emulator + system image for testing.
        composed = pkgs.androidenv.composeAndroidPackages {
          platformVersions = [ "34" ];
          buildToolsVersions = [ "34.0.0" ];
          includeEmulator = true;
          includeSystemImages = true;
          systemImageTypes = [ "google_apis" ];
          abiVersions = [ "x86_64" ];
          includeNDK = false;
        };

        androidSdk = composed.androidsdk;
        androidSdkRoot = "${androidSdk}/libexec/android-sdk";

        # JDK version required by AGP 8.x.
        jdk = pkgs.jdk17;

        nativeBuildInputs = with pkgs; [
          gradle
          jdk
          androidSdk
          # Headless browser for WebView-content screenshots.
          chromium
          # E-ink post-processing of screenshots.
          imagemagick
          # Virtual framebuffer for headless emulator.
          xvfb
          # Used by Robolectric to find android-all jars if needed.
          pkgs.which
        ];

      in
      {
        devShells.default = pkgs.mkShell rec {
          inherit nativeBuildInputs;

          ANDROID_SDK_ROOT = androidSdkRoot;
          ANDROID_HOME = androidSdkRoot;

          JAVA_HOME = "${jdk}/lib/openjdk";
          GRADLE_OPTS = "-Dorg.gradle.daemon=false -Dorg.gradle.configureondemand=true";

          CHROMIUM_FLAGS = "--no-sandbox --headless --disable-gpu";

          ANDROID_NDK_HOME = "";
        };

        packages.apk = pkgs.stdenv.mkDerivation {
          name = "inkber-debug-apk";
          src = ./.;

          nativeBuildInputs = [
            pkgs.gradle
            jdk
            androidSdk
            pkgs.perl
          ];

          ANDROID_SDK_ROOT = androidSdkRoot;
          ANDROID_HOME = androidSdkRoot;
          JAVA_HOME = "${jdk}/lib/openjdk";
          GRADLE_OPTS = "-Dorg.gradle.daemon=false -Dorg.gradle.configureondemand=true";

          buildPhase = ''
            runHook preBuild
            gradle assembleDebug --no-daemon -x lint
            runHook postBuild
          '';

          installPhase = ''
            runHook preInstall
            mkdir -p $out
            cp app/build/outputs/apk/debug/*.apk $out/inkber-debug.apk
            runHook postInstall
          '';
        };
      });
}