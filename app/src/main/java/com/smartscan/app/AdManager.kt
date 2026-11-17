package com.smartscan.app

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

object AdManager {
    // Google's official TEST ad units. Replace with your own AdMob unit IDs before publishing.
    const val BANNER_ID = "ca-app-pub-3940256099942544/6300978111"
    private const val INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"

    private var interstitial: InterstitialAd? = null
    private var scanCount = 0

    fun init(ctx: Context) {
        MobileAds.initialize(ctx) { }
        load(ctx)
    }

    private fun load(ctx: Context) {
        InterstitialAd.load(ctx, INTERSTITIAL_ID, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { interstitial = ad }
                override fun onAdFailedToLoad(e: LoadAdError) { interstitial = null }
            })
    }

    /** Shows a full-screen ad after every 2nd saved scan. */
    fun onScanSaved(activity: Activity) {
        scanCount++
        val ad = interstitial
        if (scanCount % 2 == 0 && ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() { interstitial = null; load(activity) }
                override fun onAdFailedToShowFullScreenContent(e: AdError) { interstitial = null; load(activity) }
            }
            ad.show(activity)
        }
    }
}
