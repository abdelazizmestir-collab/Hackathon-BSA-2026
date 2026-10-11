package com.example.swisswallet.data.rpc

import com.example.swisswallet.data.aa.Hex
import com.example.swisswallet.domain.aa.EthereumRpc
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** JSON-RPC error returned by a node / bundler / paymaster. [data] may contain revert data. */
class RpcException(val code: Int, message: String, val data: String? = null) : IOException(message)

/**
 * Generic JSON-RPC 2.0 client on OkHttp (cancellable coroutines).
 * Used for both the blockchain node and the Pimlico bundler/paymaster endpoint.
 */
class JsonRpcClient(
    private val endpoint: () -> String,
    private val http: OkHttpClient,
) {
    private val ids = AtomicLong(1)

    suspend fun call(method: String, vararg params: Any?): Any? {
        val payload = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", ids.getAndIncrement())
            .put("method", method)
            .put("params", JSONArray().apply { params.forEach { put(it ?: JSONObject.NULL) } })

        val request = Request.Builder()
            .url(endpoint())
            .post(payload.toString().toRequestBody(JSON))
            .build()

        val (code, text) = http.newCall(request).await()
        val json = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw RpcException(code, "HTTP $code: ${text.take(160).ifBlank { "empty response" }}")
        }
        json.optJSONObject("error")?.let { err ->
            throw RpcException(
                code = err.optInt("code", code),
                message = err.optString("message", "RPC error"),
                data = if (err.has("data") && !err.isNull("data")) err.get("data").toString() else null,
            )
        }
        return if (!json.has("result") || json.isNull("result")) null else json.get("result")
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

private suspend fun Call.await(): Pair<Int, String> = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            response.use {
                val body = try {
                    it.body?.string().orEmpty()
                } catch (e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                    return
                }
                if (cont.isActive) cont.resume(it.code to body)
            }
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}

/** Standard Ethereum node calls (reads only — writes go through the bundler). */
class EthereumRpcClient(private val rpc: JsonRpcClient) : EthereumRpc {

    override suspend fun call(to: String, data: ByteArray): ByteArray {
        val tx = JSONObject().put("to", to).put("data", Hex.of(data))
        val result = rpc.call("eth_call", tx, "latest") as? String ?: return ByteArray(0)
        return Hex.bytes(result)
    }

    override suspend fun getBalance(address: String): BigInteger {
        val result = rpc.call("eth_getBalance", address, "latest") as? String ?: return BigInteger.ZERO
        return Hex.toBigInt(result)
    }

    override suspend fun getCode(address: String): ByteArray {
        val result = rpc.call("eth_getCode", address, "latest") as? String ?: return ByteArray(0)
        return Hex.bytes(result)
    }
}
