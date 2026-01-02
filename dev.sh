#!/bin/bash
# Simple Dev Script - Build, Install, Launch with Hot-Reload

set -e

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${BLUE}🚀 GodTap Dictionary - Development Mode${NC}\n"

# Set Java Home (Windows path for Git Bash)
export JAVA_HOME="/c/Program Files/Java/jdk-17"

# Check device
if ! adb devices | grep -q "device$"; then
    echo -e "${YELLOW}⚠️  No device connected. Please connect a device.${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Device connected${NC}"
echo -e "${GREEN}✓ Java 17 configured${NC}\n"

# Initial build and deploy
echo -e "${BLUE}📦 Building and installing...${NC}"
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.godtap.dictionary/.MainActivity

echo -e "\n${GREEN}✓ App launched!${NC}\n"

# Note: Hot-reload requires fswatch. Install with: scoop install fswatch (or equivalent)
# If fswatch not available, comment out the following section
echo -e "${BLUE}🔥 Starting hot-reload (fswatch)...${NC}"
echo -e "${BLUE}👀 Watching for file changes...${NC}\n"

fswatch -o app/src/main/java app/src/main/res app/src/main/AndroidManifest.xml | while read change; do
    echo -e "\n${YELLOW}📝 Change detected, rebuilding...${NC}"
    
    if ./gradlew assembleDebug --quiet; then
        echo -e "${GREEN}✓ Build successful${NC}"
        
        if adb install -r app/build/outputs/apk/debug/app-debug.apk 2>&1 | grep -q "Success"; then
            echo -e "${GREEN}✓ Installed${NC}"
            adb shell am force-stop com.godtap.dictionary 2>/dev/null || true
            sleep 0.3
            adb shell am start -n com.godtap.dictionary/.MainActivity 2>/dev/null
            echo -e "${GREEN}✓ App restarted${NC}\n${BLUE}👀 Watching...${NC}"
        fi
    else
        echo -e "${YELLOW}⚠️  Build failed, fix errors and save again${NC}"
    fi
done
