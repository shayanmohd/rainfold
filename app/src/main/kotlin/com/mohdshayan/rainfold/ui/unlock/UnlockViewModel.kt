package com.mohdshayan.rainfold.ui.unlock

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.mohdshayan.rainfold.billing.PurchaseOutcome
import com.mohdshayan.rainfold.billing.UnlockState
import com.mohdshayan.rainfold.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One message line under the buttons, in the words of the button that caused it. */
enum class UnlockNote { NONE, CANCELLED, FAILED, NOT_OFFERED, NO_ANSWER, NOT_OWNED }

class UnlockViewModel : ViewModel() {
    private val unlock = ServiceLocator.unlock

    val state: StateFlow<UnlockState> = unlock.state

    private val _note = MutableStateFlow(UnlockNote.NONE)
    val note: StateFlow<UnlockNote> = _note.asStateFlow()

    init {
        unlock.refresh()
        viewModelScope.launch {
            unlock.outcomes.collect { outcome ->
                _note.value = when (outcome) {
                    PurchaseOutcome.CANCELLED -> UnlockNote.CANCELLED
                    PurchaseOutcome.FAILED -> UnlockNote.FAILED
                    PurchaseOutcome.UNLOCKED, PurchaseOutcome.PENDING -> UnlockNote.NONE
                }
            }
        }
    }

    fun buy(activity: Activity) {
        _note.value = when (unlock.launchPurchase(activity)) {
            BillingResponseCode.OK, BillingResponseCode.ITEM_ALREADY_OWNED -> UnlockNote.NONE
            else -> UnlockNote.NOT_OFFERED
        }
    }

    fun restore() {
        viewModelScope.launch {
            val answered = unlock.syncNow()
            _note.value = when {
                !answered -> UnlockNote.NO_ANSWER
                unlock.state.value.unlocked -> UnlockNote.NONE
                else -> UnlockNote.NOT_OWNED
            }
        }
    }
}
