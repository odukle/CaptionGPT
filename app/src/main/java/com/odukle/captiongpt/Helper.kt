package com.odukle.captiongpt

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.TextView
import android.widget.Toast
import java.util.UUID


fun View.hide() {
    this.visibility = View.GONE
}

fun View.show() {
    this.visibility = View.VISIBLE
}

fun Context.shortToast(text: String) {
    Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}

fun Context.longToast(text: String) {
    Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}

fun Context.putPreferences(prefName: String, key: String, value: String) {
    this.getSharedPreferences(prefName, Context.MODE_PRIVATE)
        .edit()
        .putString(key, value)
        .apply()
}

fun Context.getPreference(prefName: String, key: String): String {
    return this.getSharedPreferences(prefName, Context.MODE_PRIVATE)
        .getString(key, "1000") ?: ""
}

fun getDeviceUUID(context: Context?): String {
//    var serial: String? = null
//    val mSzdevidshort =
//        "35" + Build.BOARD.length % 10 +
//                Build.BRAND.length % 10 +
//                Build.CPU_ABI.length % 10 +
//                Build.DEVICE.length % 10 +
//                Build.DISPLAY.length % 10 +
//                Build.HOST.length % 10 +
//                Build.ID.length % 10 +
//                Build.MANUFACTURER.length % 10 +
//                Build.MODEL.length % 10 +
//                Build.PRODUCT.length % 10 +
//                Build.TAGS.length % 10 +
//                Build.TYPE.length % 10 +
//                Build.USER.length % 10 //13 Bit
//    try {
//        serial = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && ActivityCompat.checkSelfPermission(
//                context!!,
//                READ_PHONE_STATE
//            ) == PackageManager.PERMISSION_GRANTED) {
//            Build.getSerial()
//        } else {
//            Build.SERIAL
//        }
//        //API>=9 Use serial number
//        return UUID(mSzdevidshort.hashCode().toLong(), serial.hashCode().toLong()).toString()
//    } catch (exception: Exception) {
//        //serial Need an initialization
//        serial = "serial" // Random initialization
//    }
//    // 15-digit number cobbled together using hardware information
//    return UUID(mSzdevidshort.hashCode().toLong(), serial.hashCode().toLong()).toString()

    ///////////////////////
    var androidId = Settings.Secure.getString(context!!.contentResolver, Settings.Secure.ANDROID_ID)

    // If the retrieved Android ID is null or a known invalid value, generate a random UUID

    // If the retrieved Android ID is null or a known invalid value, generate a random UUID
    if (androidId == null || androidId == "9774d56d682e549c" || androidId.length < 8) {
        androidId = UUID.randomUUID().toString()
    }

    return androidId
    ////////////////////////
}

fun openInBrowser(url: String, context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(intent)
}

fun TextView.setBoldText(fullText: String, boldText: String) {
    val startIndex = fullText.indexOf(boldText)
    val endIndex = startIndex + boldText.length

    val spannableString = SpannableString(fullText)
    spannableString.setSpan(StyleSpan(Typeface.BOLD), startIndex, endIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    spannableString.setSpan(RelativeSizeSpan(1.2f), startIndex, endIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    this.text = spannableString
}
