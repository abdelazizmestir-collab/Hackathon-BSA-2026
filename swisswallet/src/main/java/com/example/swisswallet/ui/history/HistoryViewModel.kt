package com.example.swisswallet.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.swisswallet.di.AppContainer
import com.example.swisswallet.domain.model.TxKind
import com.example.swisswallet.domain.model.TxRecord
import com.example.swisswallet.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class HistoryFilter(val label: String) { All("All"), Received("Received"), Sent("Sent"), Swaps("Swaps") }

data class HistorySection(val title: String, val items: List<TxRecord>)

data class HistoryUiState(
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false,
    val filter: HistoryFilter = HistoryFilter.All,
    val sections: List<HistorySection> = emptyList(),
    val selected: TxRecord? = null,
)

class HistoryViewModel(
    private val transactions: TransactionRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(HistoryFilter.All)
    private val selected = MutableStateFlow<TxRecord?>(null)
    private val syncing = MutableStateFlow(false)

    val state: StateFlow<HistoryUiState> =
        combine(transactions.transactions, filter, selected, syncing) { list, f, sel, sync ->
            HistoryUiState(
                isLoading = false,
                isSyncing = sync,
                filter = f,
                sections = group(list.filter { it.matches(f) }),
                // keep the sheet in sync with fresh status updates
                selected = sel?.let { s -> list.firstOrNull { it.id == s.id } ?: s },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    /** Pending UserOperations are re-checked with the bundler every time the tab is shown. */
    fun onAppear() = sync()

    fun onRefresh() = sync()

    fun onFilter(f: HistoryFilter) { filter.value = f }
    fun onSelect(tx: TxRecord?) { selected.value = tx }

    private fun sync() {
        if (syncing.value) return
        viewModelScope.launch {
            syncing.value = true
            try {
                transactions.syncPending()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            } finally {
                syncing.value = false
            }
        }
    }

    private fun TxRecord.matches(f: HistoryFilter) = when (f) {
        HistoryFilter.All -> true
        HistoryFilter.Received -> kind == TxKind.RECEIVED || kind == TxKind.DEPOSIT
        HistoryFilter.Sent -> kind == TxKind.SENT
        HistoryFilter.Swaps -> kind == TxKind.SWAP
    }

    private fun group(list: List<TxRecord>): List<HistorySection> {
        val fmt = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)
        val today = dayKey(System.currentTimeMillis())
        val yesterday = today - 1
        return list.groupBy { dayKey(it.timestamp) }
            .toSortedMap(compareByDescending { it })
            .map { (day, items) ->
                val title = when (day) {
                    today -> "Today"
                    yesterday -> "Yesterday"
                    else -> fmt.format(Date(items.first().timestamp))
                }
                HistorySection(title, items.sortedByDescending { it.timestamp })
            }
    }

    /** Days since epoch in the local time zone. */
    private fun dayKey(ts: Long): Long {
        val c = Calendar.getInstance().apply { timeInMillis = ts }
        val offset = c.get(Calendar.ZONE_OFFSET) + c.get(Calendar.DST_OFFSET)
        return (ts + offset) / 86_400_000L
    }

    companion object {
        fun factory(c: AppContainer) = viewModelFactory {
            initializer { HistoryViewModel(c.transactionRepository) }
        }
    }
}
