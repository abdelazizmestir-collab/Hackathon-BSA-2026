package org.json
class JSONObject(s: String? = null) { private val m = HashMap<String, Any?>()
    fun put(k: String, v: Any?): JSONObject { m[k]=v; return this }
    fun getString(k: String) = m[k].toString(); fun optString(k: String, d: String) = m[k]?.toString() ?: d
    fun getLong(k: String) = (m[k] as Number).toLong(); override fun toString() = m.toString() }
