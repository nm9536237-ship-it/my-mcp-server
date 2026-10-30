package dev.local.mcpcontroller

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

object ClientAuthorizations {
    private const val PREFERENCES = "authorized_mcp_clients"

    data class Credential(val clientName: String, val token: String)

    @Synchronized
    fun issue(context: Context, clientName: String): Credential {
        val name = clientName.trim()
        require(name.isNotEmpty()) { "Enter a name for this AI client" }
        require(name.length <= 40) { "Client name must be 40 characters or fewer" }

        val preferences = preferences(context)
        require(preferences.all.keys.none { it.equals(name, ignoreCase = true) }) {
            "A client with this name is already authorized"
        }

        val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
        val token = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        check(preferences.edit().putString(name, hash(token)).commit()) { "Could not save client authorization" }
        return Credential(name, token)
    }

    @Synchronized
    fun revoke(context: Context, clientName: String): Boolean =
        preferences(context).edit().remove(clientName).commit()

    fun clients(context: Context): List<String> =
        preferences(context).all.keys.sortedBy(String::lowercase)

    fun isAuthorized(context: Context, token: String): Boolean {
        val presentedHash = hash(token).toByteArray(Charsets.US_ASCII)
        return preferences(context).all.values
            .filterIsInstance<String>()
            .any { MessageDigest.isEqual(presentedHash, it.toByteArray(Charsets.US_ASCII)) }
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    private fun hash(token: String): String = MessageDigest.getInstance("SHA-256")
        .digest(token.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}