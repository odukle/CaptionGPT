package com.odukle.captiongpt

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.forEach
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.SkuDetails
import com.android.billingclient.api.SkuDetailsParams
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.odukle.captiongpt.databinding.FragmentMainBinding
import com.odukle.captiongpt.databinding.LayoutBottomSheetBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.properties.Delegates


private const val TAG = "FragmentMain"
private const val OPENAI_API_MD_KEY = "OPENAI_API_KEY"
private const val AWS_API_MD_KEY = "AWS_API_KEY"
private const val AWS_SECRET_MD_KEY = "AWS_SECRET_KEY"
private const val TOKEN_PREF = "TokenPref"
private const val TOKENS = "tokenAmount"
const val SKU20 = "sku20"
const val SKU50 = "sku50"
const val SKU80 = "sku80"

class FragmentMain : Fragment() {

    //Ad related
    private var rewardedAd: RewardedAd? = null
    private lateinit var adRequest: AdRequest
    private var isAdLoading = true
    private var adRequested = false
    private var tokens by Delegates.notNull<Int>()
    private val skuList = listOf("sku20", "sku50", "sku80")
    private var productDetailsList: MutableList<SkuDetails>? = null

    //Firebase
    private lateinit var database: FirebaseDatabase
    private lateinit var uuid: String

    // Billing related
    private lateinit var billingClient: BillingClient

    //Fragment and ViewModel Related
    lateinit var activityResultLauncher: ActivityResultLauncher<Intent>
    var imageUri: Uri? = null
    private lateinit var binding: FragmentMainBinding
    private var imageDescription: String? = null
    private lateinit var viewModel: FragmentMainViewModel
    private lateinit var bottomSheetBinding: LayoutBottomSheetBinding
    private lateinit var openApiKey: String
    private lateinit var awsKey: String
    private lateinit var awsSecretKey: String

    companion object {
        fun newInstance() = FragmentMain()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentMainBinding.inflate(inflater, container, false)
        activityResultLauncher = registerActivityResultLauncher()

        database = Firebase.database
        uuid = getDeviceUUID(requireContext())
        restoreTokens()

        setUpBillingClient()
        adRequest = AdRequest.Builder().build()
        loadAd()
        requireContext().getPreference(TOKEN_PREF, TOKENS).apply {
            tokens = if (isNullOrEmpty()) 1000
            else this.toInt()
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[FragmentMainViewModel::class.java]
        getApiKeys()

        // Set on click listeners
        binding.btnChooseImage.setOnClickListener {
            binding.progressBar.show()
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            activityResultLauncher.launch(intent)
        }

        binding.btnGenerateCaptions.setOnClickListener {
            val num = binding.tvNoOfCaption.text.toString().toInt()
            if (tokens >= 250*num) {
                adRequested = false
                generateCaptions()
            } else {
                val text = "You need minimum ${num*250} tokens to generate $num captions, you can watch an ad to get 800 tokens"
                Snackbar.make(binding.rvMain,text,Snackbar.LENGTH_LONG).setAction("Watch Ad") {
                    adRequested = true
                    showAd(true)
                }.show()
            }
        }

        binding.bottomAppBar.menu.forEach {
            when (it.itemId) {
                R.id.token -> {
                    it.setOnMenuItemClickListener {
                        showBottomSheetDialog()
                        false
                    }
                }

                R.id.share -> {

                }
            }
        }

        viewModel.getDescription().observe(viewLifecycleOwner) {
            val text = it?.trim() ?: "Null"
            binding.tvDescription.setText(text)
            binding.tvDescription.show()
            binding.progressBar.hide()
            binding.btnGenerateCaptions.show()
            binding.layoutTones.show()
            imageDescription = it
        }

        viewModel.getCaptions().observe(viewLifecycleOwner) { captions ->
            populateCaptionsList(captions)
        }
    }

    private fun registerActivityResultLauncher(): ActivityResultLauncher<Intent> {
        return registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { activityResult ->
            if (activityResult.resultCode == Activity.RESULT_OK) {
                imageUri = activityResult.data?.data
                imageUri?.let { uri ->
                    if (Build.VERSION.SDK_INT <= 28) {
                        val bitmap = MediaStore.Images.Media.getBitmap(
                            requireActivity().contentResolver,
                            imageUri
                        )
                        binding.ivImage.setImageBitmap(bitmap)
                    } else {
                        val source = ImageDecoder.createSource(requireActivity().contentResolver, imageUri!!)
                        val bitmap = ImageDecoder.decodeBitmap(source)
                        binding.ivImage.setImageBitmap(bitmap)
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                viewModel.generateImageDescription(uri, requireActivity(), openApiKey, awsKey, awsSecretKey)
                            } catch (e: Exception) {
                                requireActivity().runOnUiThread {
                                    context?.longToast("196 : ${e.message}")
                                    binding.progressBar.hide()
                                }
                                throw e
                            }
                        }
                    }
                }
            }
        }
    }

    private fun getApiKeys() {
        val packageManager = requireContext().packageManager
        val applicationInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(
                requireContext().packageName,
                PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
            )
        } else {
            packageManager.getApplicationInfo(requireContext().packageName, PackageManager.GET_META_DATA)
        }
        openApiKey = applicationInfo.metaData.getString(OPENAI_API_MD_KEY)!!
        awsKey = applicationInfo.metaData.getString(AWS_API_MD_KEY)!!
        awsSecretKey = applicationInfo.metaData.getString(AWS_SECRET_MD_KEY)!!
    }

    private fun loadAd() {
        RewardedAd.load(requireContext(), REWARDED_AD_ID, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.d(TAG, adError.toString())
                rewardedAd = null
                isAdLoading = false
            }

            override fun onAdLoaded(ad: RewardedAd) {
                Log.d(TAG, "Ad was loaded.")
                rewardedAd = ad
                isAdLoading = false
                if (adRequested) showAd(true)
            }
        })
    }

    private fun rewardedAdCallback() {
        rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdClicked() {
                // Called when a click is recorded for an ad.
                Log.d(TAG, "Ad was clicked.")
            }

            override fun onAdDismissedFullScreenContent() {
                // Called when ad is dismissed.
                // Set the ad reference to null so you don't show the ad a second time.
                Log.d(TAG, "Ad dismissed fullscreen content.")
                rewardedAd = null
            }

            override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                // Called when ad fails to show.
                Log.e(TAG, "Ad failed to show fullscreen content.")
                rewardedAd = null
            }

            override fun onAdImpression() {
                // Called when an impression is recorded for an ad.
                Log.d(TAG, "Ad recorded an impression.")
            }

            override fun onAdShowedFullScreenContent() {
                // Called when ad is shown.
                Log.d(TAG, "Ad showed fullscreen content.")
            }
        }
    }

    private fun showAd(generateCaptions: Boolean, updateBottomSheet: Boolean = false) {
        if (isAdLoading) {
            context?.shortToast("Loading Ad...")
        } else if (rewardedAd == null) {
            loadAd()
            context?.shortToast("Loading Ad...")
        } else {
            adRequested = false
            rewardedAd?.show(requireActivity()) {
                updateTokens(800, updateBottomSheet)
                loadAd()
                if (generateCaptions) generateCaptions()
            }
        }
    }

    private fun generateCaptions() {
        binding.tvLoading.text = "generating captions..."
        binding.progressBar.show()
        binding.btnGenerateCaptions.hide()
        binding.layoutTones.hide()

        var noOfCaptions = binding.tvNoOfCaption.text.toString()
        if (noOfCaptions.isEmpty()) noOfCaptions = "4"
        var tone = binding.tvTone.text.toString()
        if (tone == "Neutral") tone = ""

        try {
            imageDescription?.let { it1 -> viewModel.generateCaptions(it1, noOfCaptions, tone, requireActivity(), openApiKey) }
        } catch (e: Exception) {
            requireActivity().runOnUiThread {
                Toast.makeText(requireContext(), "${e.message}", Toast.LENGTH_SHORT).show()
                binding.progressBar.hide()
                binding.btnGenerateCaptions.text = "Retry"
            }
        }
    }

    private fun populateCaptionsList(captions: String) {
        val regex1 = Regex("\\d+\\.")
        val regex2 = Regex("\\d+\\)")
        var captionList = captions.trim().split(regex1).toList().filter { it.trim().isNotEmpty() }
        if (captionList.size == 1) captionList = captions.trim().split(regex2).toList().filter { it.trim().isNotEmpty() }
        binding.rvMain.adapter = CaptionsAdapter(captionList, requireContext())
        binding.rvMain.layoutManager = LinearLayoutManager(requireContext())
        binding.progressBar.hide()
        binding.btnGenerateCaptions.show()
        binding.layoutTones.show()
        binding.btnGenerateCaptions.text = "Generate Captions"

        val tokensUsed: Int = (1000 / 750) * (captions.length) + 100 + (imageDescription?.length ?: 0)
        updateTokens(-1 * tokensUsed)
    }

    private fun updateTokens(amount: Int, updateBottomSheet: Boolean = false, updateDataBase: Boolean = true) {
        Log.d(TAG, "updateTokens: called")
        if (updateDataBase) {
            tokens += amount
            if (amount > 0) context?.longToast("Hurray 🎉 you earned $amount tokens")
            else context?.longToast("you used ${abs(amount)} tokens, tokens left: $tokens")

            // Write tokens to the database
            val userRef = database.getReference(uuid)
            userRef.setValue(tokens)
        }

        if (updateBottomSheet) {
            bottomSheetBinding.tvTokens.text = "You have $tokens tokens left"
        }

        context?.putPreferences(TOKEN_PREF, TOKENS, tokens.toString())
    }

    private fun showBottomSheetDialog() {
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        bottomSheetBinding = LayoutBottomSheetBinding.inflate(LayoutInflater.from(requireContext()))
        bottomSheetDialog.setContentView(bottomSheetBinding.root)
        bottomSheetDialog.show()

        bottomSheetBinding.apply {
            tvTokens.text = "You have $tokens tokens left"

            btnWatchAd.setOnClickListener {
                showAd(generateCaptions = false, true)
            }

            btn20.setOnClickListener {
                productDetailsList?.let {
                    val billingFlowParams = BillingFlowParams.newBuilder()
                        .setSkuDetails(it[0])
                        .build()
                    billingClient.launchBillingFlow(requireActivity(), billingFlowParams).responseCode
                }
            }

            btn50.setOnClickListener {
                productDetailsList?.let {
                    val billingFlowParams = BillingFlowParams.newBuilder()
                        .setSkuDetails(it[1])
                        .build()
                    billingClient.launchBillingFlow(requireActivity(), billingFlowParams).responseCode
                }
            }

            btn80.setOnClickListener {
                productDetailsList?.let {
                    val billingFlowParams = BillingFlowParams.newBuilder()
                        .setSkuDetails(it[2])
                        .build()
                    billingClient.launchBillingFlow(requireActivity(), billingFlowParams).responseCode
                }
            }

            arrayOf(binding.rvMain, binding.ivImage).forEach {
                it.setOnClickListener {
                    binding.tvDescription.clearFocus()
                }
            }
        }
    }

    private fun setUpBillingClient() {
        val purchasesUpdatedListener =
            PurchasesUpdatedListener { billingResult, purchases ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                    for (purchase in purchases) {
                        handleConsumedPurchases(purchase)
                    }
                } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
                    // Handle an error caused by a user cancelling the purchase flow.
                    context?.shortToast("User Canceled")
                } else {
                    // Handle any other error codes.
                }
            }

        billingClient = BillingClient.newBuilder(requireContext())
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases()
            .build()

        startBillingConnection()

    }

    private fun startBillingConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    // The BillingClient is ready. You can query purchases here.
                    queryAvailableProducts()
                }
            }

            override fun onBillingServiceDisconnected() {
                // Try to restart the connection on the next request to
                // Google Play by calling the startConnection() method.
            }
        })
    }

    private fun queryAvailableProducts() {
        val params = SkuDetailsParams.newBuilder()
        params.setSkusList(skuList).setType(BillingClient.SkuType.INAPP)
        billingClient.querySkuDetailsAsync(params.build()) { billingResult, skuDetailsList ->
            // Process the result.
            productDetailsList = skuDetailsList
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && !skuDetailsList.isNullOrEmpty()) {
                for (sku in skuDetailsList) {
                    //This list should contain the products added above
                    updateUI(sku)
                }
            }
        }
    }

    private fun handleConsumedPurchases(purchase: Purchase) {
        val params =
            ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        billingClient.consumeAsync(params) { billingResult, purchaseToken ->
            when (billingResult.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    purchase.products.forEach {
                        when (it) {
                            SKU20 -> {
                                MainScope().launch {
                                    delay(500)
                                    updateTokens(10000, true)
                                }
                            }

                            SKU50 -> {
                                MainScope().launch {
                                    delay(500)
                                    updateTokens(30000, true)
                                }
                            }

                            SKU80 -> {
                                MainScope().launch {
                                    delay(500)
                                    updateTokens(60000, true)
                                }
                            }
                        }
                    }
                }

                else -> {
                    context?.longToast(billingResult.debugMessage)
                }
            }
        }
    }

    private fun updateUI(skuDetails: SkuDetails?) {
        skuDetails?.let {

        }
    }

    private fun restoreTokens() {
        try {
            val userRef = database.getReference(uuid)
            userRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    // This method is called once with the initial value and again
                    // whenever data at this location is updated.
                    tokens = (dataSnapshot.getValue(Long::class.java) ?: 1000).toInt()
                    Log.d(TAG, "onDataChange: tokens >>>>>>>>>>>>> $tokens")
                    updateTokens(tokens, updateDataBase = false)
                }

                override fun onCancelled(error: DatabaseError) {
                    // Failed to read value
                    Log.w(TAG, "Failed to read value.", error.toException())
                }
            })
        } catch (e: Exception) {
            context?.longToast("510 : ${e.message.toString()}")
        }
    }


}