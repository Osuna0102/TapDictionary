# GitHub Repository Setup Guide

## Step 1: Create Repository on GitHub

1. Go to: https://github.com/new
2. Fill in the details:
   - **Repository name:** `AndroidGodTap` (or your preferred name)
   - **Description:** "Japanese-English Dictionary with Real-time Text Translation Overlay for Android"
   - **Visibility:** Choose Public or Private
   - ⚠️ **DO NOT** check "Initialize with README" (we already have files)
   - ⚠️ **DO NOT** add .gitignore or license yet

3. Click "Create repository"

## Step 2: Connect Local Repository to GitHub

After creating the repo, GitHub will show you commands. Use these:

```bash
# Add the remote (replace YOUR_USERNAME with your GitHub username)
git remote add origin https://github.com/YOUR_USERNAME/AndroidGodTap.git

# Verify the remote was added
git remote -v

# Push your code (use 'main' or keep 'master')
git branch -M main  # Optional: rename master to main
git push -u origin main

# OR if you want to keep 'master' branch name:
git push -u origin master
```

## Step 3: Verify on GitHub

Go to your repository URL: `https://github.com/YOUR_USERNAME/AndroidGodTap`

You should see all your files!

## Quick Command Sequence

```bash
cd /Applications/XAMPP/xamppfiles/htdocs/devs/AndroidGodTap

# Add remote (REPLACE YOUR_USERNAME!)
git remote add origin https://github.com/YOUR_USERNAME/AndroidGodTap.git

# Push to GitHub
git push -u origin master
```

## Troubleshooting

### Authentication Required

If you get an authentication error, you have two options:

**Option 1: Use Personal Access Token (Recommended)**
1. Go to GitHub Settings → Developer settings → Personal access tokens → Tokens (classic)
2. Generate new token with `repo` scope
3. When prompted for password, use the token instead

**Option 2: Use SSH**
```bash
# Generate SSH key (if you don't have one)
ssh-keygen -t ed25519 -C "your_email@example.com"

# Add to GitHub: Settings → SSH and GPG keys → New SSH key
# Copy your public key:
cat ~/.ssh/id_ed25519.pub

# Change remote to SSH:
git remote set-url origin git@github.com:YOUR_USERNAME/AndroidGodTap.git
git push -u origin master
```

### Remote Already Exists

If you get "remote origin already exists":
```bash
git remote set-url origin https://github.com/YOUR_USERNAME/AndroidGodTap.git
```

## Current Git Status

Your local repository has:
- ✅ 4 commits
- ✅ All project files
- ✅ .gitignore configured
- ✅ Bug fixes applied

Commit history:
```
f741cc3 Fix: Resolve handler race condition causing popup to disappear
e05ba23 docs: Add bug fix documentation for popup auto-hide issue  
b90799b Fix: Prevent popup from auto-hiding and add debug logging
ade9f65 Initial commit: Android GodTap Dictionary App
```

## After Pushing to GitHub

1. Add a description to your repository
2. Add topics/tags: `android`, `kotlin`, `dictionary`, `japanese`, `accessibility-service`
3. Consider adding a LICENSE file (MIT, Apache 2.0, etc.)
4. Update README.md with screenshots and instructions

## Future Git Workflow

```bash
# Make changes to your code
git add .
git commit -m "Your commit message"
git push

# Pull changes (if working from multiple machines)
git pull
```

## Recommended: Create a Good README

Add to your README.md:
- App description and features
- Screenshots/GIFs
- Installation instructions
- Usage guide
- Technical stack
- Contributing guidelines
- License

---

Need help? Check:
- GitHub Docs: https://docs.github.com/en/get-started
- Git Guide: https://rogerdudler.github.io/git-guide/
