package com.example.swisswallet.data.recovery

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.core.recovery.EncryptedKeyStore
import com.example.core.recovery.EthKeys
import com.example.core.recovery.RecoveryRequest
import com.example.swisswallet.config.ChainConfig
import com.example.swisswallet.data.aa.RecoveryModule
import com.example.swisswallet.domain.aa.EthereumRpc
import com.example.swisswallet.domain.model.Guardian
import com.example.swisswallet.domain.model.GuardianInvite
import com.example.swisswallet.domain.model.GuardianKind
import com.example.swisswallet.domain.model.GuardianReview
import com.example.swisswallet.domain.repository.GuardianModeService
import com.example.swisswallet.domain.repository.GuardianRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import org.web3j.crypto.ECKeyPair

/**
 * Names of my guardians, stored on this phone. On-chain the module only knows 4 addresses;
 * "Mom", "My iPad", "Swissquote" are just local labels.
 */
class DataStoreGuardianRepository(
    private val store: DataStore<Preferences>,
    private val keys: EncryptedKeyStore,
) : GuardianRepository {

    private val mutex = Mutex()

    override val guardians: Flow<List<Guardian>> = store.data
        .map { p -> p[K_GUARDIANS]?.let(::decode).orEmpty() }
        .distinctUntilChanged()

    override suspend fun upsert(guardian: Guardian) {
        mutex.withLock {
            store.edit { p ->
                val list = p[K_GUARDIANS]?.let(::decode).orEmpty()
                p[K_GUARDIANS] = encode(list.filterNot { it.address.equals(guardian.address, true) } + guardian)
            }
        }
    }

    override suspend fun remove(address: String) {
        mutex.withLock {
            store.edit { p ->
                val list = p[K_GUARDIANS]?.let(::decode).orEmpty()
                p[K_GUARDIANS] = encode(list.filterNot { it.address.equals(address, true) })
            }
        }
    }

    /**
     * DEMO ONLY: creates a guardian whose key lives on this phone, so the whole 3-of-4 flow can be
     * shown with one emulator. In real life each guardian's key is on THEIR phone.
     */
    override suspend fun createDemoGuardian(): Guardian {
        val ids = demoIds()
        val id = "demo-${System.currentTimeMillis()}"
        val key = keys.getOrCreate(id)
        store.edit { it[K_DEMO_IDS] = (ids + id).joinToString(",") }
        val name = DEMO_NAMES[ids.size % DEMO_NAMES.size]
        val guardian = Guardian(EthKeys.address(key), name, GuardianKind.DEMO)
        upsert(guardian)
        return guardian
    }

    override suspend fun demoGuardianKeys(): Map<String, ECKeyPair> =
        demoIds().mapNotNull { id -> keys.get(id)?.let { EthKeys.address(it).lowercase() to it } }.toMap()

    private suspend fun demoIds(): List<String> =
        store.data.first()[K_DEMO_IDS].orEmpty().split(",").filter { it.isNotBlank() }

    private fun encode(list: List<Guardian>): String = JSONArray().apply {
        list.forEach { put(JSONObject().put("a", it.address).put("n", it.name).put("k", it.kind.name)) }
    }.toString()

    private fun decode(json: String): List<Guardian> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Guardian(o.getString("a"), o.getString("n"), GuardianKind.valueOf(o.getString("k")))
        }
    }.getOrDefault(emptyList())

    private companion object {
        val K_GUARDIANS = stringPreferencesKey("guardians_v1")
        val K_DEMO_IDS = stringPreferencesKey("demo_guardian_ids")
        val DEMO_NAMES = listOf("Mom (simulated)", "Alex (simulated)", "Sam (simulated)", "Lea (simulated)")
    }
}

/**
 * "Be a guardian for someone": this phone holds a guardian key (secp256k1, encrypted by the
 * Android Keystore). It's used to protect a friend's wallet, or your OWN wallet from your second
 * device (tablet, old phone…).
 */
class KeystoreGuardianModeService(
    private val keys: EncryptedKeyStore,
    private val rpc: EthereumRpc,
) : GuardianModeService {

    private suspend fun key(): ECKeyPair = keys.getOrCreate(MY_GUARDIAN_KEY)

    override suspend fun myGuardianAddress(): String = EthKeys.address(key())

    override suspend fun inviteCode(name: String, kind: GuardianKind): String =
        GuardianInvite(myGuardianAddress(), name.trim().ifBlank { "Guardian" }, kind).encode()

    override suspend fun review(requestCode: String): GuardianReview {
        val request = try {
            RecoveryRequest.decode(requestCode)
        } catch (e: Exception) {
            throw IllegalArgumentException("This is not a valid recovery request code.")
        }
        require(request.chainId == ChainConfig.CHAIN_ID) { "This request is for another network (chain ${request.chainId})." }
        require(request.module.equals(RecoveryModule.address, true)) { "Unknown recovery module — don't approve." }
        val me = myGuardianAddress()
        val iAmGuardian = try {
            RecoveryModule.guardiansOf(rpc, request.wallet).any { it.equals(me, true) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        return GuardianReview(request, iAmGuardian)
    }

    override suspend fun approve(request: RecoveryRequest): String = EthKeys.approve(key(), request).encode()

    private companion object {
        const val MY_GUARDIAN_KEY = "my-guardian-key"
    }
}
