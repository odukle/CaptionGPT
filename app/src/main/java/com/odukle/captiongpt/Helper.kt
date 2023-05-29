package com.odukle.captiongpt

import android.content.Context
import android.view.View
import android.widget.Toast

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
        .getString(key,"1000") ?: ""
}