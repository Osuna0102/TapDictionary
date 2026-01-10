# 💰 Monetization Implementation Guide - TapDictionary

**Branch:** `feature/monetization`  
**Date:** January 10, 2026  
**Status:** Ready for Testing

---

## 📋 Overview

This branch implements the **Freemium** monetization strategy as outlined in [GOOGLE_PLAY_STORE_SUBMISSION.md](./GOOGLE_PLAY_STORE_SUBMISSION.md).

### Key Features Added:
1. ✅ **Google Play Billing** - In-app purchase for Pro version ($4.99)
2. ✅ **Usage Limits** - 20 lookups/day, 10 TTS/day for free users
3. ✅ **Google AdMob** - Banner ads in popup for free users
4. ✅ **Upgrade Prompts** - Smart triggers to encourage Pro purchase
5. ✅ **Pro User Management** - Purchase verification and restoration

---

## 🗂️ New Files Created

### 1. Billing System
**📁 `app/src/main/java/com/godtap/dictionary/billing/BillingManager.kt`**
- Handles Google Play Billing API v6
- Product ID: `tapdictionary_pro` (one-time purchase)
- Verifies purchases on app start
- Restores purchases across devices
- Caches Pro status for instant access

**Key Methods:**
```kotlin
BillingManager.getInstance(context)
billingManager.launchPurchaseFlow(activity) // Start purchase
billingManager.isProUser() // Check Pro status (instant)
billingManager.verifyPurchases() // Verify ownership
```

### 2. Advertisement System
**📁 `app/src/main/java/com/godtap/dictionary/ads/AdManager.kt`**
- Google AdMob banner ads (320x50)
- Only shows ads for free-tier users
- Test ads enabled by default (see `USE_TEST_ADS` flag)
- Automatic ad lifecycle management

**Key Methods:**
```kotlin
AdManager.getInstance(context)
adManager.createBannerAdForPopup(container) // Returns AdView or null
adManager.shouldShowAds() // Check if user sees ads
```

**⚠️ IMPORTANT:** Update `PROD_AD_UNIT_ID` in AdManager.kt with your real AdMob ID before production release!

### 3. Usage Tracking
**📁 `app/src/main/java/com/godtap/dictionary/usage/UsageLimitManager.kt`**
- Tracks daily word lookups and TTS uses
- Resets counters at midnight automatically
- Enforces free-tier limits
- Provides upgrade prompt triggers

**Free Tier Limits:**
- 20 word lookups per day
- 10 TTS uses per day
- Banner ads in popup

**Pro Tier:**
- Unlimited everything
- No ads

**Key Methods:**
```kotlin
UsageLimitManager.getInstance(context)
usageLimitManager.canLookupWord() // Check before lookup
usageLimitManager.incrementLookupCount() // After successful lookup
usageLimitManager.canUseTts() // Check before TTS
usageLimitManager.incrementTtsCount() // After TTS use
usageLimitManager.shouldShowLookupUpgradePrompt() // At 18/20 lookups
```

### 4. Upgrade Prompts
**📁 `app/src/main/java/com/godtap/dictionary/ui/UpgradePromptManager.kt`**
- Dialog prompts to encourage Pro purchase
- Multiple trigger points for optimal conversion
- Shows feature comparison

**Prompt Triggers:**
1. **Limit Reached** - After hitting 20 lookups or 10 TTS uses
2. **Approaching Limit** - At 18 lookups or 8 TTS uses (warning)
3. **Ad Fatigue** - After viewing 5 ads (optional)
4. **Manual** - "Upgrade to Pro" button in settings

**Key Methods:**
```kotlin
UpgradePromptManager.showLimitReachedDialog(activity, LimitType.LOOKUP) { 
    // User clicked "Upgrade"
}
UpgradePromptManager.showProFeaturesDialog(activity) {
    // Launch billing flow
}
```

### 5. Preferences Manager
**📁 `app/src/main/java/com/godtap/dictionary/util/PreferencesManager.kt`**
- SharedPreferences wrapper
- Stores Pro status, usage counts, settings
- Daily reset tracking

---

## 🔧 Modified Files

### 1. `app/build.gradle.kts`
Added dependencies:
```kotlin
// Google Play Billing (In-App Purchases)
implementation("com.android.billingclient:billing-ktx:6.1.0")

// Google AdMob (Ads for free tier)
implementation("com.google.android.gms:play-services-ads:22.6.0")
```

### 2. `app/src/main/AndroidManifest.xml`
Added AdMob app ID:
```xml
<meta-data
    android:name="com.google.android.gms.ads.APPLICATION_ID"
    android:value="ca-app-pub-3940256099942544~3347511713"/>
```
**⚠️ NOTE:** This is a test App ID. Replace with your real AdMob App ID before production!

### 3. `app/src/main/res/layout/overlay_dictionary_popup.xml`
Added ad container:
```xml
<LinearLayout
    android:id="@+id/adContainer"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:gravity="center"
    android:visibility="gone">
    <!-- AdView added programmatically -->
</LinearLayout>
```

---

## 🚀 Integration Guide

### Step 1: Initialize Managers in DictionaryApp

**Edit:** `app/src/main/java/com/godtap/dictionary/DictionaryApp.kt`

```kotlin
class DictionaryApp : Application() {
    
    companion object {
        lateinit var instance: DictionaryApp
        
        // Monetization managers
        lateinit var billingManager: BillingManager
        lateinit var adManager: AdManager
        lateinit var usageLimitManager: UsageLimitManager
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Initialize monetization systems
        billingManager = BillingManager.getInstance(this)
        adManager = AdManager.getInstance(this)
        usageLimitManager = UsageLimitManager.getInstance(this)
        
        // Verify purchases on app start
        billingManager.verifyPurchases()
    }
}
```

### Step 2: Update OverlayManager to Show Ads

**Edit:** `app/src/main/java/com/godtap/dictionary/overlay/OverlayManager.kt`

Add these fields:
```kotlin
private val adManager = AdManager.getInstance(context)
private val usageLimitManager = UsageLimitManager.getInstance(context)
private var currentAdView: AdView? = null
```

In `showPopup()` method, after inflating layout:
```kotlin
// Show ads for free users
val adContainer = popupView.findViewById<LinearLayout>(R.id.adContainer)
if (adManager.shouldShowAds()) {
    val adView = adManager.createBannerAdForPopup(adContainer)
    if (adView != null) {
        adContainer.removeAllViews()
        adContainer.addView(adView)
        adContainer.visibility = View.VISIBLE
        currentAdView = adView
    }
} else {
    adContainer.visibility = View.GONE
}
```

In `hidePopup()` method:
```kotlin
// Clean up ad
adManager.destroyAdView(currentAdView)
currentAdView = null
```

### Step 3: Enforce Lookup Limits in TextSelectionAccessibilityService

**Edit:** `app/src/main/java/com/godtap/dictionary/service/TextSelectionAccessibilityService.kt`

Before performing lookup:
```kotlin
private val usageLimitManager = UsageLimitManager.getInstance(this)

// In handleTextSelectionChanged() or handleViewClicked()
// Before calling DictionaryLookup
if (!usageLimitManager.canLookupWord()) {
    Log.d(TAG, "⊘ Daily lookup limit reached")
    
    // Show limit reached dialog (requires Activity context)
    // Option 1: Show toast
    Handler(Looper.getMainLooper()).post {
        Toast.makeText(this, 
            "Daily limit reached! Upgrade to Pro for unlimited lookups.",
            Toast.LENGTH_LONG).show()
    }
    
    // Option 2: Send broadcast to MainActivity to show dialog
    val intent = Intent("com.godtap.dictionary.SHOW_UPGRADE_PROMPT")
    intent.putExtra("limit_type", "lookup")
    sendBroadcast(intent)
    
    return // Don't perform lookup
}

// After successful lookup
usageLimitManager.incrementLookupCount()

// Check if should show approaching limit warning
if (usageLimitManager.shouldShowLookupUpgradePrompt()) {
    // Show warning (similar to above)
}
```

### Step 4: Enforce TTS Limits in TtsManager

**Edit:** `app/src/main/java/com/godtap/dictionary/util/TtsManager.kt`

```kotlin
private val usageLimitManager = UsageLimitManager.getInstance(context)

fun speak(text: String, language: String) {
    // Check limit
    if (!usageLimitManager.canUseTts()) {
        Log.d(TAG, "⊘ Daily TTS limit reached")
        // Show toast or dialog
        return
    }
    
    // Perform TTS
    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    
    // Increment counter
    usageLimitManager.incrementTtsCount()
}
```

### Step 5: Add "Upgrade to Pro" Button in MainActivity

**Edit:** `app/src/main/java/com/godtap/dictionary/MainActivity.kt`

```kotlin
private lateinit var billingManager: BillingManager
private lateinit var usageLimitManager: UsageLimitManager

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    billingManager = BillingManager.getInstance(this)
    usageLimitManager = UsageLimitManager.getInstance(this)
    
    // Observe Pro status
    lifecycleScope.launch {
        billingManager.isPro.collect { isPro ->
            updateUiForProStatus(isPro)
        }
    }
    
    // Setup upgrade button
    findViewById<Button>(R.id.upgradeButton)?.setOnClickListener {
        showUpgradeDialog()
    }
}

private fun showUpgradeDialog() {
    UpgradePromptManager.showProFeaturesDialog(this) {
        // Launch purchase flow
        billingManager.launchPurchaseFlow(this)
    }
}

private fun updateUiForProStatus(isPro: Boolean) {
    if (isPro) {
        // Hide upgrade button, show "Pro" badge
        findViewById<Button>(R.id.upgradeButton)?.visibility = View.GONE
        findViewById<TextView>(R.id.proStatusText)?.apply {
            text = "🌟 Pro User"
            visibility = View.VISIBLE
        }
    } else {
        // Show usage summary
        val summary = usageLimitManager.getUsageSummary()
        findViewById<TextView>(R.id.usageSummaryText)?.text = 
            "Lookups: ${summary.lookupsUsed}/${summary.lookupsLimit} | " +
            "TTS: ${summary.ttsUsed}/${summary.ttsLimit}"
    }
}
```

---

## 🧪 Testing Checklist

### Pre-Production Testing (with Test IDs)

- [ ] **Install app** from this branch
- [ ] **Verify free tier limits:**
  - [ ] Lookup 20 words → Should show limit dialog on 21st
  - [ ] Use TTS 10 times → Should block 11th use
  - [ ] Check daily reset (change device time to tomorrow)
- [ ] **Verify ads appear:**
  - [ ] Free users see banner ad in popup
  - [ ] Ad loads successfully (test ad)
- [ ] **Test purchase flow:**
  - [ ] Tap "Upgrade to Pro" → Opens Google Play purchase screen
  - [ ] Complete test purchase (use test account)
  - [ ] Verify Pro status granted immediately
- [ ] **Verify Pro benefits:**
  - [ ] Unlimited lookups (try 30+)
  - [ ] Unlimited TTS (try 20+)
  - [ ] No ads in popup
- [ ] **Test purchase restoration:**
  - [ ] Uninstall app
  - [ ] Reinstall
  - [ ] Pro status should restore automatically

### Production Setup (Before Launch)

- [ ] **Google Play Console:**
  - [ ] Create In-App Product: `tapdictionary_pro`
  - [ ] Set price: $4.99 USD (or use localized pricing table from guide)
  - [ ] Set product title: "TapDictionary Pro"
  - [ ] Set product description: "Unlock unlimited lookups, TTS, and remove ads"
  - [ ] Publish product
  
- [ ] **Google AdMob:**
  - [ ] Create AdMob account (https://admob.google.com/)
  - [ ] Add app to AdMob
  - [ ] Create Ad Unit: "Popup Banner Ad"
  - [ ] Copy App ID → Update AndroidManifest.xml `PROD_AD_UNIT_ID`
  - [ ] Copy Ad Unit ID → Update AdManager.kt `APPLICATION_ID`
  - [ ] Enable test devices for testing real ads
  
- [ ] **Code Updates:**
  - [ ] AdManager.kt: Change `USE_TEST_ADS = false`
  - [ ] AdManager.kt: Replace `PROD_AD_UNIT_ID` with real ID
  - [ ] AndroidManifest.xml: Replace AdMob App ID with real ID
  
- [ ] **Testing with Real IDs:**
  - [ ] Test real ads (using test device)
  - [ ] Test real purchase with test account
  - [ ] Verify no crashes or errors

---

## 💵 Revenue Tracking

### Key Metrics to Monitor:

1. **Download → Pro Conversion Rate**
   - Target: 2-3%
   - Track: Google Play Console → Statistics → In-app products

2. **Daily Active Users (DAU)**
   - Free users hitting limits = good engagement
   - Track: Firebase Analytics (recommended to add)

3. **Ad Revenue**
   - Expected: $0.50-$2.00 eCPM
   - Track: AdMob Console → Earnings

4. **Refund Rate**
   - Target: <5%
   - Track: Google Play Console → Orders

5. **Average Revenue Per User (ARPU)**
   - Target: $0.10-$0.50 (mixed free + paid)

### Recommended Analytics Events:
```kotlin
// Add Firebase Analytics
// Track key events:
logEvent("free_limit_reached", "type" to "lookup")
logEvent("upgrade_prompt_shown", "trigger" to "limit_reached")
logEvent("purchase_initiated", null)
logEvent("purchase_completed", "price" to 4.99)
logEvent("ad_viewed", null)
```

---

## 🐛 Troubleshooting

### Issue: "Billing client not ready"
**Solution:** Wait for `BillingManager.isReady` StateFlow to emit `true` before calling purchase methods.

### Issue: "Product not found"
**Solution:** 
1. Ensure `tapdictionary_pro` product is created in Google Play Console
2. Product must be published (not draft)
3. App must be uploaded to at least Internal Testing track

### Issue: Ads not loading
**Solution:**
1. Check internet connection
2. Verify AdMob App ID is correct in AndroidManifest
3. Check AdMob console for errors
4. Enable test device: `MobileAds.setRequestConfiguration(RequestConfiguration.Builder().setTestDeviceIds(listOf("YOUR_TEST_DEVICE_ID")).build())`

### Issue: Purchase not restoring
**Solution:**
1. Call `billingManager.verifyPurchases()` in `DictionaryApp.onCreate()`
2. Check Google Play account is signed in
3. Check network connection (purchases synced via Play Store)

### Issue: Daily limits not resetting
**Solution:**
1. Check device date/time settings
2. Verify `PreferencesManager.getLastUsageResetDate()` format matches current date
3. Clear app data and test again

---

## 📱 User Experience Flow

### New User Journey (Free Tier):
1. **Install app** → Free tier by default
2. **First lookup** → No limits, no prompts
3. **10th lookup** → Small "Upgrade" button appears
4. **18th lookup** → Warning: "2 lookups remaining today"
5. **20th lookup** → Limit dialog: "Upgrade to Pro for unlimited!"
6. **Tap "Upgrade"** → Google Play purchase screen
7. **Complete purchase** → Immediate Pro access, ads disappear

### Returning User (Pro):
1. **Open app** → Pro status restored automatically
2. **All features unlocked** → No limits, no ads
3. **"Pro" badge** → Visible in main screen

---

## 🎯 Next Steps

1. **Merge this branch** after testing
2. **Set up Google Play Console** (product + AdMob)
3. **Test on real devices** with test accounts
4. **Submit to Google Play** (follow GOOGLE_PLAY_STORE_SUBMISSION.md)
5. **Monitor metrics** post-launch
6. **Iterate pricing** based on conversion data

---

## 📄 Related Documents

- [GOOGLE_PLAY_STORE_SUBMISSION.md](./GOOGLE_PLAY_STORE_SUBMISSION.md) - Full launch guide
- [Google Play Billing Docs](https://developer.android.com/google/play/billing)
- [Google AdMob Docs](https://developers.google.com/admob/android/quick-start)

---

**Questions or issues?** Check the troubleshooting section or contact the development team.

**Good luck with your launch! 🚀💰**
