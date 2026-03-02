package com.odogwudev.esdiacwebrtcdemo.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface CallControlAction {
    data object EndCall : CallControlAction
    data object ToggleSpeaker : CallControlAction
    data object ToggleHold : CallControlAction
}

object CallControlActionBus {
    private val _actions = MutableSharedFlow<CallControlAction>(
        replay = 1,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val actions: SharedFlow<CallControlAction> = _actions.asSharedFlow()

    fun dispatch(action: CallControlAction) {
        _actions.tryEmit(action)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun consume(action: CallControlAction) {
        if (_actions.replayCache.firstOrNull() == action) {
            _actions.resetReplayCache()
        }
    }
}
