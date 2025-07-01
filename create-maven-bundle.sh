#!/bin/bash

# Maven Central Bundle Creation Script for AdMoai Android SDK
# This script manually creates all required artifacts and bundles them for Maven Central upload

set -e

SDK_VERSION="1.0.0"
GROUP_ID="com.admoai"
ARTIFACT_ID="admoai-android"
BUILD_DIR="sdk/build"
BUNDLE_DIR="$BUILD_DIR/maven-central-bundle"
GROUP_PATH="com/admoai"

echo "🚀 Creating Maven Central bundle for $GROUP_ID:$ARTIFACT_ID:$SDK_VERSION"

# Clean previous builds
echo "🧹 Cleaning previous builds..."
./gradlew clean

# Build the AAR
echo "📦 Building AAR..."
./gradlew :sdk:assembleRelease

# Generate sources JAR
echo "📄 Generating sources JAR..."
./gradlew :sdk:androidSourcesJar

# Generate javadoc JAR
echo "📚 Generating javadoc JAR..."
./gradlew :sdk:dokkaHtml
./gradlew :sdk:androidJavadocJar

# Create bundle directory structure
echo "📁 Creating bundle directory structure..."
mkdir -p "$BUNDLE_DIR/$GROUP_PATH/$ARTIFACT_ID/$SDK_VERSION"

# Copy main artifacts
echo "📋 Copying artifacts..."
cp "$BUILD_DIR/outputs/aar/sdk-release.aar" "$BUNDLE_DIR/$GROUP_PATH/$ARTIFACT_ID/$SDK_VERSION/$ARTIFACT_ID-$SDK_VERSION.aar"
cp "$BUILD_DIR/libs/sdk-sources.jar" "$BUNDLE_DIR/$GROUP_PATH/$ARTIFACT_ID/$SDK_VERSION/$ARTIFACT_ID-$SDK_VERSION-sources.jar"
cp "$BUILD_DIR/libs/sdk-javadoc.jar" "$BUNDLE_DIR/$GROUP_PATH/$ARTIFACT_ID/$SDK_VERSION/$ARTIFACT_ID-$SDK_VERSION-javadoc.jar"

# Generate POM file
echo "📄 Generating POM file..."
cat > "$BUNDLE_DIR/$GROUP_PATH/$ARTIFACT_ID/$SDK_VERSION/$ARTIFACT_ID-$SDK_VERSION.pom" << EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    
    <groupId>$GROUP_ID</groupId>
    <artifactId>$ARTIFACT_ID</artifactId>
    <version>$SDK_VERSION</version>
    <packaging>aar</packaging>
    
    <name>AdMoai Android SDK</name>
    <description>Android SDK for AdMoai advertising platform with targeting, tracking, and analytics</description>
    <url>https://github.com/admoai/admoai-android</url>
    
    <licenses>
        <license>
            <name>MIT License</name>
            <url>https://opensource.org/licenses/MIT</url>
            <distribution>repo</distribution>
        </license>
    </licenses>
    
    <developers>
        <developer>
            <id>admoai</id>
            <name>AdMoai Team</name>
            <url>https://github.com/admoai</url>
        </developer>
    </developers>
    
    <scm>
        <connection>scm:git:git://github.com/admoai/admoai-android.git</connection>
        <developerConnection>scm:git:ssh://git@github.com/admoai/admoai-android.git</developerConnection>
        <url>https://github.com/admoai/admoai-android</url>
    </scm>
    
    <dependencies>
        <dependency>
            <groupId>androidx.core</groupId>
            <artifactId>core-ktx</artifactId>
            <version>1.12.0</version>
            <scope>compile</scope>
        </dependency>
        <dependency>
            <groupId>org.jetbrains.kotlinx</groupId>
            <artifactId>kotlinx-coroutines-android</artifactId>
            <version>1.8.0</version>
            <scope>compile</scope>
        </dependency>
        <dependency>
            <groupId>org.jetbrains.kotlinx</groupId>
            <artifactId>kotlinx-serialization-json</artifactId>
            <version>1.6.3</version>
            <scope>compile</scope>
        </dependency>
        <dependency>
            <groupId>io.ktor</groupId>
            <artifactId>ktor-client-core</artifactId>
            <version>2.3.11</version>
            <scope>compile</scope>
        </dependency>
        <dependency>
            <groupId>io.ktor</groupId>
            <artifactId>ktor-client-okhttp</artifactId>
            <version>2.3.11</version>
            <scope>compile</scope>
        </dependency>
        <dependency>
            <groupId>io.ktor</groupId>
            <artifactId>ktor-client-content-negotiation</artifactId>
            <version>2.3.11</version>
            <scope>compile</scope>
        </dependency>
        <dependency>
            <groupId>io.ktor</groupId>
            <artifactId>ktor-serialization-kotlinx-json</artifactId>
            <version>2.3.11</version>
            <scope>compile</scope>
        </dependency>
        <dependency>
            <groupId>androidx.compose.runtime</groupId>
            <artifactId>runtime</artifactId>
            <version>1.6.7</version>
            <scope>compile</scope>
        </dependency>
    </dependencies>
</project>
EOF

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
