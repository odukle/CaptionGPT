package com.odukle.captiongpt

import android.Manifest.permission.READ_PHONE_STATE
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.app.ActivityCompat
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
    var serial: String? = null
    val mSzdevidshort =
        "35" + Build.BOARD.length % 10 +
                Build.BRAND.length % 10 +
                Build.CPU_ABI.length % 10 +
                Build.DEVICE.length % 10 +
                Build.DISPLAY.length % 10 +
                Build.HOST.length % 10 +
                Build.ID.length % 10 +
                Build.MANUFACTURER.length % 10 +
                Build.MODEL.length % 10 +
                Build.PRODUCT.length % 10 +
                Build.TAGS.length % 10 +
                Build.TYPE.length % 10 +
                Build.USER.length % 10 //13 Bit
    try {
        serial = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && ActivityCompat.checkSelfPermission(
                context!!,
                READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED) {
            Build.getSerial()
        } else {
            Build.SERIAL
        }
        //API>=9 Use serial number
        return UUID(mSzdevidshort.hashCode().toLong(), serial.hashCode().toLong()).toString()
    } catch (exception: Exception) {
        //serial Need an initialization
        serial = "serial" // Random initialization
    }
    // 15-digit number cobbled together using hardware information
    return UUID(mSzdevidshort.hashCode().toLong(), serial.hashCode().toLong()).toString()
}