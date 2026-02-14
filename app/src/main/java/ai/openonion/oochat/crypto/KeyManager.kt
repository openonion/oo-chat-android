package ai.openonion.oochat.crypto

import android.content.Context
import android.content.SharedPreferences
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.Sign
import com.goterl.lazysodium.utils.KeyPair

/**
 * Ed25519 key management for agent authentication.
 * Handles key generation, storage, and message signing.
 */
class KeyManager(private val context: Context) {
    private val sodium = LazySodiumAndroid(SodiumAndroid())
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class AddressData(
        val address: String,
        val shortAddress: String,
        val publicKey: ByteArray,
        val privateKey: ByteArray
    )

    /**
     * Generate a new Ed25519 keypair.
     */
    fun generate(): AddressData {
        val keyPair = sodium.cryptoSignKeypair()
        val publicKey = keyPair.publicKey.asBytes
        val privateKey = keyPair.secretKey.asBytes

        val address = "0x" + publicKey.toHex()
        val shortAddress = "${address.take(6)}...${address.takeLast(4)}"

        return AddressData(
            address = address,
            shortAddress = shortAddress,
            publicKey = publicKey,
            privateKey = privateKey
        )
    }

    /**
     * Load existing keys from SharedPreferences.
     */
    fun load(): AddressData? {
        val address = prefs.getString(KEY_ADDRESS, null) ?: return null
        val publicKeyHex = prefs.getString(KEY_PUBLIC, null) ?: return null
        val privateKeyHex = prefs.getString(KEY_PRIVATE, null) ?: return null

        val publicKey = publicKeyHex.hexToBytes()
        val privateKey = privateKeyHex.hexToBytes()
        val shortAddress = "${address.take(6)}...${address.takeLast(4)}"

        return AddressData(
            address = address,
            shortAddress = shortAddress,
            publicKey = publicKey,
            privateKey = privateKey
        )
    }

    /**
     * Save keys to SharedPreferences.
     */
    fun save(keys: AddressData) {
        prefs.edit()
            .putString(KEY_ADDRESS, keys.address)
            .putString(KEY_PUBLIC, keys.publicKey.toHex())
            .putString(KEY_PRIVATE, keys.privateKey.toHex())
            .apply()
    }

    /**
     * Load existing keys or generate new ones.
     */
    fun loadOrGenerate(): AddressData {
        return load() ?: generate().also { save(it) }
    }

    /**
     * Sign a message with Ed25519.
     * Returns hex-encoded signature.
     */
    fun sign(keys: AddressData, message: String): String {
        val messageBytes = message.toByteArray(Charsets.UTF_8)
        val signatureBytes = ByteArray(Sign.ED25519_BYTES)

        sodium.cryptoSignDetached(
            signatureBytes,
            messageBytes,
            messageBytes.size.toLong(),
            keys.privateKey
        )

        return signatureBytes.toHex()
    }

    /**
     * Create canonical JSON with sorted keys for consistent signatures.
     */
    fun canonicalJson(map: Map<String, Any>): String {
        val sortedEntries = map.entries.sortedBy { it.key }
        val sb = StringBuilder("{")
        sortedEntries.forEachIndexed { index, (key, value) ->
            if (index > 0) sb.append(",")
            sb.append("\"$key\":")
            when (value) {
                is String -> sb.append("\"$value\"")
                is Number -> sb.append(value)
                is Boolean -> sb.append(value)
                else -> sb.append("\"$value\"")
            }
        }
        sb.append("}")
        return sb.toString()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray {
        val len = length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(this[i], 16) shl 4) + Character.digit(this[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    companion object {
        private const val PREFS_NAME = "connectonion_keys"
        private const val KEY_ADDRESS = "address"
        private const val KEY_PUBLIC = "public_key"
        private const val KEY_PRIVATE = "private_key"
    }
}
