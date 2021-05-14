package com.example.camerax

import android.view.View

/**
 * Created on 11/28/20.
 */

fun View.disable() {
    isEnabled = false
}

fun View.enable() {
    isEnabled = true
}

fun View.visible() {
    visibility = View.VISIBLE
}

fun View.gone() {
    visibility = View.GONE
}