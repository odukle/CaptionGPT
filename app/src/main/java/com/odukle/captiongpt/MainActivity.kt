package com.odukle.captiongpt

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider

private const val TAG = "MainActivity"
const val REWARDED_AD_ID = "ca-app-pub-3940256099942544/5224354917"

class MainActivity : AppCompatActivity() {

    private lateinit var analytics: FirebaseAnalytics
    private var rewardedAd: RewardedAd? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        analytics = Firebase.analytics
        MobileAds.initialize(this)

        val fragment = FragmentMain()
        openFragment(fragment)
    }

    private fun openFragment(fragment: Fragment) {
        val fragmentManager = supportFragmentManager
        val fragmentTransaction = fragmentManager.beginTransaction()

        fragmentTransaction.add(R.id.fragmentContainer, fragment)
        fragmentTransaction.commit()
    }
}

