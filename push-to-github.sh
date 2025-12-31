#!/bin/bash

# Quick script to push to GitHub
# Usage: ./push-to-github.sh YOUR_GITHUB_USERNAME

if [ -z "$1" ]; then
    echo "Usage: ./push-to-github.sh YOUR_GITHUB_USERNAME"
    echo "Example: ./push-to-github.sh johndoe"
    exit 1
fi

USERNAME=$1
REPO_URL="https://github.com/$USERNAME/AndroidGodTap.git"

echo "Setting up GitHub remote..."
echo "Repository: $REPO_URL"
echo ""

# Check if remote already exists
if git remote get-url origin >/dev/null 2>&1; then
    echo "Remote 'origin' already exists. Updating URL..."
    git remote set-url origin "$REPO_URL"
else
    echo "Adding remote 'origin'..."
    git remote add origin "$REPO_URL"
fi

echo ""
echo "Remote configured:"
git remote -v

echo ""
echo "Current branch:"
git branch

echo ""
read -p "Do you want to rename branch to 'main'? (y/N): " rename_branch
if [ "$rename_branch" = "y" ] || [ "$rename_branch" = "Y" ]; then
    echo "Renaming branch to 'main'..."
    git branch -M main
    BRANCH="main"
else
    BRANCH=$(git branch --show-current)
fi

echo ""
read -p "Push to GitHub? This will push branch '$BRANCH' (y/N): " confirm
if [ "$confirm" = "y" ] || [ "$confirm" = "Y" ]; then
    echo "Pushing to GitHub..."
    git push -u origin "$BRANCH"
    
    if [ $? -eq 0 ]; then
        echo ""
        echo "✅ Successfully pushed to GitHub!"
        echo "View your repository at: https://github.com/$USERNAME/AndroidGodTap"
    else
        echo ""
        echo "❌ Push failed. You may need to:"
        echo "1. Create the repository on GitHub first: https://github.com/new"
        echo "2. Set up authentication (Personal Access Token or SSH key)"
        echo ""
        echo "See GITHUB_SETUP_GUIDE.md for detailed instructions."
    fi
else
    echo "Push cancelled."
fi
