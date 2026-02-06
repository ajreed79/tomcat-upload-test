#!/bin/bash

# Tomcat Upload Test - Quick Start Script
# This script helps set up and run the test with the correct Java version

echo "=========================================="
echo "  Tomcat Safari Upload Bug Test"
echo "=========================================="
echo

# Check Java version
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | awk -F '"' '{print $2}' | awk -F '.' '{print $1}')

echo "Detected Java version: $JAVA_VERSION"

if [ "$JAVA_VERSION" -ge 22 ]; then
    echo
    echo "⚠️  WARNING: Java $JAVA_VERSION detected"
    echo "   Gradle 8.13 has compatibility issues with Java 22+"
    echo
    echo "   Please install and use Java 11, 17, or 21 (LTS versions)"
    echo "   Example:"
    echo "     - On Ubuntu/Debian: sudo apt install openjdk-21-jdk"
    echo "     - On Fedora/RHEL: sudo dnf install java-21-openjdk-devel"
    echo "     - On macOS: brew install openjdk@21"
    echo
    echo "   Then set JAVA_HOME before running:"
    echo "     export JAVA_HOME=/path/to/java-21"
    echo "     export PATH=\$JAVA_HOME/bin:\$PATH"
    echo
    exit 1
elif [ "$JAVA_VERSION" -ge 11 ]; then
    echo "✓ Java version is compatible"
else
    echo
    echo "⚠️  WARNING: Java $JAVA_VERSION is too old"
    echo "   This project requires Java 11 or higher"
    echo
    exit 1
fi

echo
echo "Starting embedded Tomcat server..."
echo "Press Ctrl+C to stop"
echo

./gradlew run
