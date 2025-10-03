package com.tftguide.assistant.overlay

import android.content.Context
import android.content.Intent
import com.tftguide.assistant.screen.BoardAnalysisBus
import com.tftguide.assistant.screen.BoardAnalyzer
import com.tftguide.bubble.ExpandableBubbleService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class GuideBubbleService : ExpandableBubbleService() {

    private val job = Job()
    private val scope = CoroutineScope(job + Dispatchers.Main.immediate)
    private var subscription: Job? = null

    override fun onCreate() {
        super.onCreate()
        subscription = scope.launch {
            BoardAnalysisBus.events.collectLatest { state ->
                updateBubble("TFT Guide", buildMessage(state))
            }
        }
    }

    override fun onDestroy() {
        subscription?.cancel()
        job.cancel()
        super.onDestroy()
    }

    private fun buildMessage(state: BoardAnalyzer.BoardState): String {
        return if (state.rawText.isBlank()) {
            "Analyzing board…"
        } else {
            "Detected: ${state.rawText.take(40)}"
        }
    }

    companion object {
        fun createIntent(context: Context): Intent =
            Intent(context, GuideBubbleService::class.java)
    }
}
