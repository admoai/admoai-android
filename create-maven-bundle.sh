#!/bin/bash

# Maven Central Bundle Creation Script for AdMoai Android SDK
# This script publishes to local Maven repo and bundles for Maven Central upload

set -e

SDK_VERSION="1.1.2"
GROUP_ID="com.admoai"
ARTIFACT_ID="admoai-android"
BUILD_DIR="sdk/build"
BUNDLE_DIR="$BUILD_DIR/maven-central-bundle"
GROUP_PATH="com/admoai"
LOCAL_REPO="$HOME/.m2/repository"

echo "🚀 Creating Maven Central bundle for $GROUP_ID:$ARTIFACT_ID:$SDK_VERSION"

# Clean previous builds
echo "🧹 Cleaning previous builds..."
./gradlew clean

# Publish to local Maven repository (generates AAR, sources JAR, javadoc JAR, and POM)
echo "📦 Publishing to local Maven repository..."
./gradlew :sdk:publishReleasePublicationToMavenLocal

# Create bundle directory structure
echo "📁 Creating bundle directory structure..."
rm -rf "$BUNDLE_DIR"
mkdir -p "$BUNDLE_DIR/$GROUP_PATH/$ARTIFACT_ID/$SDK_VERSION"

# Copy artifacts from local Maven repo
echo "📋 Copying artifacts from local Maven repository..."
LOCAL_ARTIFACT_DIR="$LOCAL_REPO/$GROUP_PATH/$ARTIFACT_ID/$SDK_VERSION"
cp "$LOCAL_ARTIFACT_DIR/$ARTIFACT_ID-$SDK_VERSION.aar" "$BUNDLE_DIR/$GROUP_PATH/$ARTIFACT_ID/$SDK_VERSION/"
cp "$LOCAL_ARTIFACT_DIR/$ARTIFACT_ID-$SDK_VERSION-sources.jar" "$BUNDLE_DIR/$GROUP_PATH/$ARTIFACT_ID/$SDK_VERSION/"
cp "$LOCAL_ARTIFACT_DIR/$ARTIFACT_ID-$SDK_VERSION-javadoc.jar" "$BUNDLE_DIR/$GROUP_PATH/$ARTIFACT_ID/$SDK_VERSION/"
cp "$LOCAL_ARTIFACT_DIR/$ARTIFACT_ID-$SDK_VERSION.pom" "$BUNDLE_DIR/$GROUP_PATH/$ARTIFACT_ID/$SDK_VERSION/"

# Sign all files if GPG is available
if command -v gpg &> /dev/null; then
    echo "🔒 Signing artifacts with GPG..."
    cd "$BUNDLE_DIR/$GROUP_PATH/$ARTIFACT_ID/$SDK_VERSION"
    
    # Sign main artifacts first
    for file in *.aar *.jar *.pom; do
        if [ -f "$file" ]; then
            gpg --armor --detach-sign "$file"
            echo "✅ Signed $file"
        fi
    done
    
    # Generate checksums for all files (including signatures)
    echo "🧮 Generating checksums..."
    for file in *.aar *.jar *.pom *.asc; do
        if [ -f "$file" ]; then
            # Generate MD5 - just the hash value, no filename
            md5sum "$file" | cut -d' ' -f1 > "$file.md5"
            # Generate SHA1 - just the hash value, no filename  
            sha1sum "$file" | cut -d' ' -f1 > "$file.sha1"
            echo "✅ Generated checksums for $file"
        fi
    done
    
    cd - > /dev/null
else
    echo "⚠️  GPG not found. You'll need to sign the artifacts manually:"
    echo "   cd $BUNDLE_DIR/$GROUP_PATH/$ARTIFACT_ID/$SDK_VERSION"
    echo "   gpg --armor --detach-sign <each file>"
    echo "   Generate MD5 and SHA1 checksums for all files"
fi

# Create final ZIP bundle
echo "📦 Creating ZIP bundle..."
cd "$BUNDLE_DIR"
zip -r "../$ARTIFACT_ID-$SDK_VERSION-maven-central-bundle.zip" .
cd - > /dev/null

BUNDLE_FILE="$BUILD_DIR/$ARTIFACT_ID-$SDK_VERSION-maven-central-bundle.zip"

echo ""
echo "✅ Maven Central bundle created successfully!"
echo "📁 Bundle location: $BUNDLE_FILE"
echo "📏 Bundle size: $(du -h "$BUNDLE_FILE" | cut -f1)"

echo ""
echo "📋 Bundle contents:"
unzip -l "$BUNDLE_FILE"

echo ""
echo "🚀 Next steps:"
echo "  1. Upload $ARTIFACT_ID-$SDK_VERSION-maven-central-bundle.zip to Maven Central Portal"
echo "  2. Go to https://central.sonatype.org/"
echo "  3. Navigate to 'Publish Component'"
echo "  4. Upload the bundle and follow validation steps"
echo ""
echo "🔗 Publishing guide: MAVEN_CENTRAL_PUBLISHING.md"
