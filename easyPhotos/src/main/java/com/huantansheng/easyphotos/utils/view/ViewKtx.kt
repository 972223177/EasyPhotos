package com.huantansheng.easyphotos.utils.view

import android.view.View

/**
 * ViewKtx
 * @author ly
 * Created on 2021-07-27 09:43
 */


private var lastTimestamp = 0L
fun View.debounceClick(interval: Long = 600L, block: () -> Unit) {
    setOnClickListener {
        val currTimestamp = System.currentTimeMillis()
        val plusResult = currTimestamp - lastTimestamp
        if (plusResult > interval) {
            lastTimestamp = currTimestamp
            block.invoke()
        }
    }
}

fun View.gone() {
    visibility = View.GONE
}

fun View.visible() {
    visibility = View.VISIBLE
}

fun View.invisible() {
    visibility = View.INVISIBLE
}
