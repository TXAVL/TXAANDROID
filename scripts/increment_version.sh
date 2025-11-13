#!/bin/bash

# Script để tự động tăng version cho TXA Hub App
# Usage: ./increment_version.sh [bugfix|feature|major]

VERSION_FILE="../version.properties"
TYPE=${1:-bugfix} # bugfix, feature, hoặc major

if [ ! -f "$VERSION_FILE" ]; then
    echo "Error: version.properties not found!"
    exit 1
fi

# Đọc version hiện tại
CURRENT_VERSION=$(grep "^VERSION_NAME=" "$VERSION_FILE" | cut -d'=' -f2)
CURRENT_CODE=$(grep "^VERSION_CODE=" "$VERSION_FILE" | cut -d'=' -f2)

echo "Current version: $CURRENT_VERSION (code: $CURRENT_CODE)"

# Parse version
IFS='_' read -ra VERSION_PARTS <<< "$CURRENT_VERSION"
VERSION_NUMBER=${VERSION_PARTS[0]}
SUFFIX=${VERSION_PARTS[1]:-_txa}

IFS='.' read -ra NUM_PARTS <<< "$VERSION_NUMBER"
MAJOR=${NUM_PARTS[0]}
MINOR=${NUM_PARTS[1]:-0}
PATCH=${NUM_PARTS[2]:-0}

# Tăng version theo type
case $TYPE in
    bugfix)
        PATCH=$((PATCH + 1))
        NEW_CODE=$((CURRENT_CODE + 1))
        ;;
    feature)
        MINOR=$((MINOR + 1))
        PATCH=0
        NEW_CODE=$((CURRENT_CODE + 10))
        ;;
    major)
        MAJOR=$((MAJOR + 1))
        MINOR=0
        PATCH=0
        NEW_CODE=$((CURRENT_CODE + 100))
        ;;
    *)
        echo "Error: Invalid type. Use: bugfix, feature, or major"
        exit 1
        ;;
esac

# Tạo version mới
if [ "$PATCH" -eq 0 ]; then
    NEW_VERSION="$MAJOR.$MINOR$SUFFIX"
else
    NEW_VERSION="$MAJOR.$MINOR.$PATCH$SUFFIX"
fi

# Cập nhật file
sed -i "s/^VERSION_NAME=.*/VERSION_NAME=$NEW_VERSION/" "$VERSION_FILE"
sed -i "s/^VERSION_CODE=.*/VERSION_CODE=$NEW_CODE/" "$VERSION_FILE"

echo "New version: $NEW_VERSION (code: $NEW_CODE)"
echo "Version updated successfully!"

