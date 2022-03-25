rm -rf .gradle/zipalign_build || 0
git clone https://github.com/mozilla-services/zipalign.git .gradle/zipalign_build
cd .gradle/zipalign_build || exit 1

echo "Building..."
export CGO_ENABLED=1 GOOS=android
NDK="$LOCALAPPDATA/Android/Sdk/ndk/23.1.7779620/toolchains/llvm/prebuilt/windows-x86_64/bin"
CC="$NDK/x86_64-linux-android30-clang" GOARCH=amd64 go build -o zipalign_x86_64 main.go
CC="$NDK/armv7a-linux-androideabi30-clang" GOARCH=arm go build -o zipalign_armeabi_v7a main.go
CC="$NDK/aarch64-linux-android30-clang" GOARCH=arm64 go build -o zipalign_arm64_v8a main.go

rm ../../app/src/main/res/raw/zipalign_*
mv zipalign_* ../../app/src/main/res/raw
