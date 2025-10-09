package com.example.tft.ui

import android.app.NotificationManager
import android.content.Context
import android.os.Build
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
import com.example.tft.rules.TipEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BubbleActivity : ComponentActivity() {
    private val viewModel: BubbleViewModel by viewModels { BubbleViewModel.factory(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bubble)

        val entries = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(EXTRA_TIP_ENTRIES, TipEntry::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<TipEntry>(EXTRA_TIP_ENTRIES)
        } ?: arrayListOf()
        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID) ?: "conversation"
        val summary = intent.getStringExtra(EXTRA_SUMMARY) ?: ""
        val patch = intent.getStringExtra(EXTRA_PATCH) ?: ""

        viewModel.bootstrap(conversationId, entries, summary, patch)

        val categoryView: TextView = findViewById(R.id.categoryText)
        val tipText: TextView = findViewById(R.id.tipText)
        val typeView: TextView = findViewById(R.id.typeText)
        val metadataView: TextView = findViewById(R.id.metadataText)
        val nextButton: Button = findViewById(R.id.nextTipButton)
        val dismissButton: Button = findViewById(R.id.dismissButton)

        lifecycleScope.launch {
            viewModel.state.collect { state ->
                if (state == null) {
                    finish()
                    return@collect
                }
                categoryView.text = state.categoryLabel
                val background = if (state.isCorrective) {
                    R.drawable.bg_tip_category_alert
                } else {
                    R.drawable.bg_tip_category
                }
                categoryView.setBackgroundResource(background)
                tipText.text = state.currentTip
                typeView.text = state.typeLabel
                metadataView.text = state.summaryLabel
                metadataView.visibility = if (state.summaryLabel.isBlank()) View.GONE else View.VISIBLE
                nextButton.visibility = if (state.hasMore) View.VISIBLE else View.GONE
            }
        }

        nextButton.setOnClickListener { viewModel.nextTip() }
        dismissButton.setOnClickListener {
            viewModel.dismiss()
            finish()
        }
    }

    companion object {
        const val EXTRA_TIP_ENTRIES = "tip_entries"
        const val EXTRA_CONVERSATION_ID = "conversation_id"
        const val EXTRA_SUMMARY = "summary"
        const val EXTRA_PATCH = "patch"
    }
}

private class BubbleViewModel(
    private val notificationManager: NotificationManager
) : ViewModel() {

    data class UiState(
        val conversationId: String,
        val categoryLabel: String,
        val currentTip: String,
        val typeLabel: String,
        val summaryLabel: String,
        val hasMore: Boolean,
        val isCorrective: Boolean
    )

    private val _state = MutableStateFlow<UiState?>(null)
    val state: StateFlow<UiState?> = _state

    private var tips: MutableList<TipEntry> = mutableListOf()
    fun bootstrap(conversationId: String, payload: List<TipEntry>, summary: String, patch: String) {
        if (_state.value != null) return
        tips = payload.toMutableList()
        val initial = tips.firstOrNull()
        if (initial == null) {
            _state.value = null
            return
        }
        _state.value = UiState(
            conversationId = conversationId,
            categoryLabel = initial.categoryLabel,
            currentTip = initial.text,
            typeLabel = initial.typeLabel,
            summaryLabel = buildSummary(summary, patch),
            hasMore = tips.size > 1,
            isCorrective = initial.isCorrective
        )
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
                categoryLabel = next.categoryLabel,
                currentTip = next.text,
                typeLabel = next.typeLabel,
                hasMore = tips.size > 1,
                isCorrective = next.isCorrective
            )
        }
    }

    fun dismiss() {
        val current = _state.value ?: return
        notificationManager.cancel(current.conversationId.hashCode())
        _state.value = null
    }

    private fun buildSummary(summary: String, patch: String): String {
        val pieces = mutableListOf<String>()
        if (summary.isNotBlank()) pieces += summary
        if (patch.isNotBlank()) pieces += "Patch $patch"
        return if (pieces.isEmpty()) "" else pieces.joinToString(" • ")
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    @Suppress("UNCHECKED_CAST")
                    return BubbleViewModel(manager) as T
                }
            }
    }
}
