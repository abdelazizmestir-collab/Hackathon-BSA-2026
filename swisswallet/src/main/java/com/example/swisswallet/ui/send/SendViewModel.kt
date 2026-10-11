package com.example.swisswallet.ui.send

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.swisswallet.data.aa.ErrorMapper
import com.example.swisswallet.di.AppContainer
import com.example.swisswallet.domain.model.SendProgress
import com.example.swisswallet.domain.model.Token
import com.example.swisswallet.domain.model.TokenBalance
import com.example.swisswallet.domain.model.Tokens
import com.example.swisswallet.domain.model.TransferRequest
import com.example.swisswallet.domain.repository.PortfolioRepository
import com.example.swisswallet.domain.repository.TransferService
import com.example.swisswallet.domain.repository.WalletRepository
import com.example.swisswallet.util.AddressValidator
import com.example.swisswallet.util.AmountParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

enum class SendStep { Form, Review, Progress }

data class SendUiState(
    val step: SendStep = SendStep.Form,
    val token: Token = Tokens.ETH,
    val balances: List<TokenBalance> = emptyList(),
    val balancesLoading: Boolean = true,
    val balancesError: String? = null,
    val fromAddress: String? = null,
    val recipient: String = "",
    val amount: String = "",
    val recipientError: String? = null,
    val amountError: String? = null,
    /** Validated values, set when moving to Review. */
    val amountRaw: BigInteger? = null,
    val progress: SendProgress? = null,
) {
    val selectedBalance: TokenBalance? get() = balances.firstOrNull { it.token.symbol == token.symbol }

    val amountChf: BigDecimal?
        get() {
            val value = amount.replace(',', '.').toBigDecimalOrNull() ?: return null
            val price = selectedBalance?.price?.chf ?: return null
            return value.multiply(price).setScale(2, RoundingMode.HALF_UP)
        }

    val canReview: Boolean get() = recipient.isNotBlank() && amount.isNotBlank() && !balancesLoading

    /** True while nothing has been broadcast yet — leaving would abort cleanly. */
    val isBusyBeforeBroadcast: Boolean
        get() = step == SendStep.Progress && progress.let {
            it == SendProgress.Preparing || it == SendProgress.Sponsoring ||
                it == SendProgress.Signing || it == SendProgress.Submitting
        }
}

class SendViewModel(
    initialToken: Token,
    private val wallet: WalletRepository,
    private val portfolios: PortfolioRepository,
    private val transfers: TransferService,
) : ViewModel() {

    private val _state = MutableStateFlow(SendUiState(token = initialToken))
    val state: StateFlow<SendUiState> = _state.asStateFlow()

    private var sendJob: Job? = null

    init { loadBalances() }

    fun loadBalances() {
        _state.update { it.copy(balancesLoading = true, balancesError = null) }
        viewModelScope.launch {
            try {
                val account = wallet.currentAccount() ?: error("No wallet on this device")
                _state.update { it.copy(fromAddress = account.smartAccountAddress) }
                val portfolio = portfolios.loadPortfolio(account.smartAccountAddress)
                _state.update { it.copy(balances = portfolio.balances, balancesLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(balancesLoading = false, balancesError = ErrorMapper.humanize(e)) }
            }
        }
    }

    fun onTokenSelected(token: Token) = _state.update { it.copy(token = token, amountError = null) }

    fun onRecipientChanged(value: String) = _state.update { it.copy(recipient = value.trim(), recipientError = null) }

    fun onAmountChanged(value: String) = _state.update { it.copy(amount = AmountParser.sanitize(value), amountError = null) }

    fun onMax() = _state.update { s ->
        val b = s.selectedBalance ?: return@update s
        s.copy(amount = b.amount.stripTrailingZeros().toPlainString(), amountError = null)
    }

    /** Form → Review (with validation). */
    fun onReview() {
        val s = _state.value
        val recipientError = AddressValidator.validate(s.recipient, s.fromAddress)
        val parsed = AmountParser.parse(s.amount, s.token.decimals, s.selectedBalance?.raw)
        val amountError = (parsed as? AmountParser.Result.Error)?.message
        val raw = (parsed as? AmountParser.Result.Ok)?.raw
        _state.update {
            it.copy(
                recipientError = recipientError,
                amountError = amountError,
                amountRaw = raw,
                step = if (recipientError == null && raw != null) SendStep.Review else SendStep.Form,
            )
        }
    }

    fun onBackToForm() = _state.update { it.copy(step = SendStep.Form) }

    /** Review → Progress: runs the ERC-4337 flow and mirrors every step in the UI. */
    fun onConfirm() {
        if (sendJob?.isActive == true) return
        val s = _state.value
        val raw = s.amountRaw ?: return
        val request = TransferRequest(token = s.token, to = s.recipient, amountRaw = raw)
        _state.update { it.copy(step = SendStep.Progress, progress = SendProgress.Preparing) }
        sendJob = viewModelScope.launch {
            transfers.send(request).collect { p -> _state.update { it.copy(progress = p) } }
        }
    }

    /** After a failure: go back to Review to retry with the same inputs. */
    fun onRetry() {
        if (sendJob?.isActive == true) return
        _state.update { it.copy(step = SendStep.Review, progress = null) }
    }

    companion object {
        fun factory(c: AppContainer, tokenSymbol: String) = viewModelFactory {
            initializer {
                SendViewModel(Tokens.bySymbol(tokenSymbol), c.walletRepository, c.portfolioRepository, c.transferService)
            }
        }
    }
}
