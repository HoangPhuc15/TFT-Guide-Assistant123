package com.tftguide.assistant.screen

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

object BoardAnalysisBus {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val _events = MutableSharedFlow<BoardAnalyzer.BoardState>(replay = 1)
    val events: SharedFlow<BoardAnalyzer.BoardState> = _events

    fun publish(state: BoardAnalyzer.BoardState) {
        scope.launch {
            _events.emit(state)
        }
    }
}
