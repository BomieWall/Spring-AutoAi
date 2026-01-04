#!/bin/bash

# Spring-AutoAi Core Build Script
# Used for compiling and packaging the spring-autoai-core module

set -e  # Exit immediately on error

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Project information
PROJECT_NAME="spring-autoai-core"
MODULE="spring-autoai-core"

# Get the directory where the script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Output directory
OUTPUT_DIR="$SCRIPT_DIR/output"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Spring-AutoAi Core Build Script${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}Error: Maven not installed or not in PATH${NC}"
    echo "Please install Maven first: https://maven.apache.org/download.cgi"
    exit 1
fi

# Show Maven version
echo -e "${YELLOW}Maven Version:${NC}"
mvn -version | head -n 1
echo ""

# Parse command line arguments
SKIP_TESTS=true
CLEAN_FIRST=true

while [[ $# -gt 0 ]]; do
    case $1 in
        --with-tests)
            SKIP_TESTS=false
            shift
            ;;
        --no-clean)
            CLEAN_FIRST=false
            shift
            ;;
        -h|--help)
            echo "Usage: $0 [options]"
            echo ""
            echo "Options:"
            echo "  --with-tests    Include tests (skip by default)"
            echo "  --no-clean      Do not execute clean (incremental build)"
            echo "  -h, --help      Show help information"
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            echo "Use -h or --help to view help"
            exit 1
            ;;
    esac
done

# Build command
BUILD_CMD="mvn"

# Add clean option
if [ "$CLEAN_FIRST" = true ]; then
    BUILD_CMD="$BUILD_CMD clean"
fi

# Add package target
BUILD_CMD="$BUILD_CMD package -pl $MODULE -am"

# Add skip tests option
if [ "$SKIP_TESTS" = true ]; then
    BUILD_CMD="$BUILD_CMD -DskipTests"
    echo -e "${YELLOW}Build Mode: Skip Tests${NC}"
else
    echo -e "${YELLOW}Build Mode: Include Tests${NC}"
fi

echo ""
echo -e "${YELLOW}Executing command:${NC} $BUILD_CMD"
echo -e "${GREEN}----------------------------------------${NC}"
echo ""

# Execute build
eval $BUILD_CMD

# Check build result
if [ $? -eq 0 ]; then
    echo ""
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}  Build Successful!${NC}"
    echo -e "${GREEN}========================================${NC}"
    echo ""

    # Display generated jar file (using Maven Shade Plugin, main JAR already includes all dependencies)
    JAR_FILE="$SCRIPT_DIR/$MODULE/target/$MODULE-*.jar"

    if ls $JAR_FILE 1> /dev/null 2>&1; then
        JAR_PATH=$(ls $JAR_FILE | grep -v "sources.jar" | head -n 1)
        echo -e "${GREEN}Generated JAR file (includes all dependencies):${NC}"
        ls -lh $JAR_PATH | awk '{print "  " $9 " (" $5 ")"}'
    fi

    if [ -n "$JAR_PATH" ]; then
        echo ""
        echo -e "${YELLOW}Full path:${NC} $JAR_PATH"
        echo ""

        # Create output directory
        if [ ! -d "$OUTPUT_DIR" ]; then
            echo -e "${YELLOW}Creating output directory...${NC}"
            mkdir -p "$OUTPUT_DIR"
        fi

        # Copy JAR file to output directory and rename
        echo -e "${YELLOW}Copying JAR file to output directory...${NC}"
        cp -f $JAR_PATH "$OUTPUT_DIR/spring-autoai-core.jar"

        if [ $? -eq 0 ]; then
            echo -e "${GREEN}Copy successful!${NC}"
            echo ""
            echo -e "${GREEN}Output directory files:${NC}"
            ls -lh "$OUTPUT_DIR/"*.jar 2>/dev/null | awk '{print "  " $9 " (" $5 ")"}'
            echo ""
            echo -e "${YELLOW}Output directory:${NC} $OUTPUT_DIR"
        else
            echo -e "${RED}Copy failed!${NC}"
        fi
    fi
else
    echo ""
    echo -e "${RED}========================================${NC}"
    echo -e "${RED}  Build Failed!${NC}"
    echo -e "${RED}========================================${NC}"
    exit 1
fi
