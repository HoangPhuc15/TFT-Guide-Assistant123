package com.example.tft.app

import android.content.Context
import androidx.startup.Initializer
import com.example.tft.data.DataSyncer

@Suppress("unused")
class TftGuideInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        DataSyncer.from(context).scheduleRefresh()
    }

    override fun dependencies(): MutableList<Class<out Initializer<*>>> = mutableListOf()
}
