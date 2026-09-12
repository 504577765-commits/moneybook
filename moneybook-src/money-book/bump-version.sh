#!/bin/bash
# 用法: ./bump-version.sh [1.5.0]
# 每次发版前,运行这个脚本自动升级 versionCode + versionName

set -e

NEW_VERSION="${1:-}"

PROP_FILE="android/gradle.properties"

if [ -z "$NEW_VERSION" ]; then
  # 没传参,自动 +0.0.1
  CURRENT=$(grep "^versionName=" "$PROP_FILE" | cut -d= -f2)
  echo "当前版本: $CURRENT"
  IFS='.' read -r MAJOR MINOR PATCH <<< "$CURRENT"
  PATCH=$((PATCH + 1))
  NEW_VERSION="$MAJOR.$MINOR.$PATCH"
fi

CURRENT_CODE=$(grep "^versionCode=" "$PROP_FILE" | cut -d= -f2)
NEW_CODE=$((CURRENT_CODE + 1))

echo "升级到: versionCode=$NEW_CODE, versionName=$NEW_VERSION"

# 用 sed 替换
sed -i "s/^versionCode=.*/versionCode=$NEW_CODE/" "$PROP_FILE"
sed -i "s/^versionName=.*/versionName=$NEW_VERSION/" "$PROP_FILE"

echo "✅ $PROP_FILE 已更新"
echo ""
cat "$PROP_FILE"
