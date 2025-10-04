package com.example.tft.ui

import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.tft.R
import com.example.tft.rules.TipEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BubbleActivity : ComponentActivity() {
    private val viewModel: BubbleViewModel by viewModels {
        BubbleViewModel.factory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bubble)

        val tips = intent.getStringArrayListExtra(EXTRA_TIPS) ?: arrayListOf()
        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID) ?: "default"

        viewModel.bootstrap(conversationId, tips)

        val tipText: TextView = findViewById(R.id.tipText)
        val metadataText: TextView = findViewById(R.id.metadataText)
        val next: Button = findViewById(R.id.nextTipButton)
        val dismiss: Button = findViewById(R.id.dismissButton)

        lifecycleScope.launch {
            viewModel.state.collect { state ->
                if (state == null) {
                    finish()
                    return@collect
                }
                tipText.text = state.currentTip
                metadataText.text = getString(
                    R.string.notification_title
                ) + " • " + state.hint
                next.visibility = if (state.hasMore) View.VISIBLE else View.GONE
            }
        }

        next.setOnClickListener { viewModel.nextTip() }
        dismiss.setOnClickListener {
            viewModel.dismiss()
            finish()
        }
    }

    companion object {
        const val EXTRA_TIPS = "tips"
        const val EXTRA_CONVERSATION_ID = "conversation_id"
    }
}

private class BubbleViewModel(
    private val tipEngine: TipEngine,
    private val notificationManager: NotificationManager
) : ViewModel() {

    data class UiState(
        val conversationId: String,
        val currentTip: String,
        val hasMore: Boolean,
        val hint: String
    )

    private val _state = MutableStateFlow<UiState?>(null)
    val state: StateFlow<UiState?> = _state

    private var tips: MutableList<String> = mutableListOf()

    fun bootstrap(conversationId: String, payload: List<String>) {
        if (_state.value != null) return
        tips = payload.toMutableList()
        val initial = tips.firstOrNull() ?: "Không có gợi ý"
        _state.value = UiState(conversationId, initial, tips.size > 1, "Patch " + tipEngine.patch)
    }

    fun nextTip() {
        val current = _state.value ?: return
        if (tips.isNotEmpty()) {
            tips.removeAt(0)
        }
        val next = tips.firstOrNull()
        if (next == null) {
            dismiss()
        } else {
            _state.value = current.copy(
                currentTip = next,
                hasMore = tips.size > 1
            )
        }
    }

    fun dismiss() {
        val current = _state.value ?: return
        notificationManager.cancel(current.conversationId.hashCode())
        _state.value = null
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val tipEngine = TipEngine.from(context)
                    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    @Suppress("UNCHECKED_CAST")
                    return BubbleViewModel(tipEngine, manager) as T
                }
            }
    }
}
