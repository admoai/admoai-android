# 🚀 Testing Instructions - HTTPS Mock Server + IMA SDK

## ✅ Setup Complete!

All configurations are done. Follow these steps to test the video ad player with IMA SDK.

---

## 📋 Prerequisites

1. ✅ HTTPS mock server with SSL certificates
2. ✅ Android app trusts self-signed certificate
3. ✅ All VAST tag URLs updated to HTTPS
4. ✅ App rebuilt and ready to install

---

## 🎯 Step-by-Step Testing

### Step 1: Start HTTPS Mock Server

The mock server is **currently running** at `https://10.0.2.2:8080` (HTTPS enabled).

**To verify it's running:**
```bash
curl -k https://localhost:8080/health
```

**Expected response:**
```json
{"status": "healthy", "service": "mock-endpoints"}
```

**If you need to restart it:**
```bash
cd /Users/matias-admoai/Documents/repos/mock-endpoints
kill $(lsof -ti :8080)  # Kill old server
CERT_FILE=cert.pem KEY_FILE=key.pem PORT=8080 go run main.go
```

---

### Step 2: Install Android App

```bash
cd /Users/matias-admoai/Documents/repos/admoai-android
./gradlew :sample:installDebug
```

Or use Android Studio's "Run" button.

---

### Step 3: Test Video Ad Demo

1. **Open the Admoai Sample App** on the emulator

2. **Navigate to "Video Ad Demo"** (bottom navigation)

3. **Select Test Configuration:**
   - **Placement**: Any (e.g., "Home")
   - **Delivery Method**: **VAST Tag** ⭐
   - **End-Card Type**: **None** (for simplest test)
   - **Video Player**: **ExoPlayer + IMA** ⭐

4. **Click "Launch Video Demo"**

---

### Step 4: What to Look For

#### ✅ **Success Indicators (in Logcat):**

**Filter:** `com.admoai.sample`

**Expected logs:**
```
ExoPlayerIMA: Loading VAST tag URL: https://10.0.2.2:8080/endpoint?scenario=tagurl_vasttag_none
VideoAdDemo: Fetched mock data for scenario: vasttag_none (1305 chars)
```

**IMA SDK should load ads (no errors):**
```
IMA: Ad event: LOADED
IMA: Ad event: STARTED
IMA: Tracking impression
IMA: Tracking start
IMA: Tracking firstQuartile
IMA: Tracking midpoint
IMA: Tracking thirdQuartile
IMA: Tracking complete
```

#### ❌ **Errors to Watch For:**

**If you see this - FIXED!**
```
❌ Mixed Content: ... requested an insecure XMLHttpRequest endpoint 'http://...'
❌ Access to XMLHttpRequest ... blocked by CORS policy
```

**These should NOT appear anymore!**

---

### Step 5: Test Matrix

Test these configurations to verify full functionality:

#### Test 1: VAST Tag + None + ExoPlayer + IMA
- ✅ Video should play
- ✅ IMA SDK loads VAST XML
- ✅ Tracking events fire automatically
- ✅ Poster image displays before playback

#### Test 2: VAST Tag + Native End-card + ExoPlayer + IMA
- ✅ Video plays via IMA
- ✅ Custom overlay appears at 50%
- ✅ CTA button works
- ✅ Publisher-drawn UI over IMA player

#### Test 3: JSON + None + Basic Player
- ✅ Direct video playback
- ✅ Manual tracking
- ✅ No IMA SDK involved

---

## 🔍 Troubleshooting

### Issue: Certificate Not Trusted

**Symptom:** `javax.net.ssl.SSLHandshakeException`

**Solution:**
1. Verify certificate exists: `/sample/src/main/res/raw/mock_server_cert.der`
2. Check Network Security Config references it
3. Clean and rebuild app

### Issue: Server Not Responding

**Check if server is running:**
```bash
lsof -i :8080
```

**Restart server:**
```bash
cd /Users/matias-admoai/Documents/repos/mock-endpoints
kill $(lsof -ti :8080)
./start-https.sh
```

### Issue: "Ad" Indicator Shows

**This is normal** - IMA SDK displays "Ad" label. See documentation for how to hide it if needed.

---

## 📊 Expected Results

### Before HTTPS Fix:
- ❌ CORS errors
- ❌ Mixed content errors
- ❌ IMA SDK cannot load ads
- ❌ No tracking events

### After HTTPS Fix:
- ✅ No CORS errors
- ✅ No mixed content errors  
- ✅ IMA SDK loads VAST XML
- ✅ Video plays with ads
- ✅ Tracking events fire automatically

---

## 📄 Related Documentation

- **Full Flow Documentation**: `/admoai-android/VIDEO_PLAYER_FLOW_SUMMARY.md`
- **Network Security Config**: `/sample/src/main/res/xml/network_security_config.xml`
- **Mock Server Code**: `/mock-endpoints/main.go`
- **SSL Certificates**: `/mock-endpoints/cert.pem`, `/mock-endpoints/key.pem`

---

## 🎉 Success Criteria

You'll know everything works when:

1. ✅ App fetches mock data from `https://10.0.2.2:8080`
2. ✅ IMA SDK loads VAST XML without errors
3. ✅ Video plays with poster image
4. ✅ Tracking events appear in logcat (impression, quartiles, complete)
5. ✅ No CORS or mixed content errors in logs

**Good luck testing! 🚀**
