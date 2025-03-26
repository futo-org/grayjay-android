import com.futo.platformplayer.subsexchange.ChannelRequest
import com.futo.platformplayer.subsexchange.ChannelResolve
import com.futo.platformplayer.subsexchange.ChannelResult
import com.futo.platformplayer.subsexchange.ExchangeContract
import com.futo.platformplayer.subsexchange.ExchangeContractResolve
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.spec.PKCS8EncodedKeySpec


class SubsExchangeClient(private val server: String, private val privateKey: String) {

    private val publicKey: String = extractPublicKey(privateKey)

    // Endpoints

    // Endpoint: Contract
    fun requestContract(vararg channels: ChannelRequest): ExchangeContract {
        val data = post("/api/Channel/Contract", Json.encodeToString(channels), "application/json")
        return Json.decodeFromString(data)
    }
    suspend fun requestContractAsync(vararg channels: ChannelRequest): ExchangeContract {
        val data = postAsync("/api/Channel/Contract", Json.encodeToString(channels), "application/json")
        return Json.decodeFromString(data)
    }

    // Endpoint: Resolve
    fun resolveContract(contract: ExchangeContract, vararg resolves: ChannelResolve): Array<ChannelResult> {
        val contractResolve = convertResolves(*resolves)
        val result = post("/api/Channel/Resolve?contractId=${contract.id}", Json.encodeToString(contractResolve), "application/json")
        return Json.decodeFromString(result)
    }
    suspend fun resolveContractAsync(contract: ExchangeContract, vararg resolves: ChannelResolve): Array<ChannelResult> {
        val contractResolve = convertResolves(*resolves)
        val result = postAsync("/api/Channel/Resolve?contractId=${contract.id}", Json.encodeToString(contractResolve), "application/json")
        return Json.decodeFromString(result)
    }


    private fun convertResolves(vararg resolves: ChannelResolve): ExchangeContractResolve {
        val data = Json.encodeToString(resolves)
        val signature = createSignature(data, privateKey)

        return ExchangeContractResolve(
            publicKey = publicKey,
            signature = signature,
            data = data
        )
    }

    // IO methods
    private fun post(query: String, body: String, contentType: String): String {
        val url = URL("$server$query")
        with(url.openConnection() as HttpURLConnection) {
            requestMethod = "POST"
            setRequestProperty("Content-Type", contentType)
            doOutput = true
            OutputStreamWriter(outputStream, StandardCharsets.UTF_8).use { it.write(body) }

            InputStreamReader(inputStream, StandardCharsets.UTF_8).use {
                return it.readText()
            }
        }
    }
    private suspend fun postAsync(query: String, body: String, contentType: String): String {
        return withContext(Dispatchers.IO) {
            post(query, body, contentType)
        }
    }

    // Crypto methods
    companion object {
        fun createPrivateKey(): String {
            val rsa = KeyFactory.getInstance("RSA")
            val keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            val keyPair = keyPairGenerator.generateKeyPair();
            return Base64.getEncoder().encodeToString(keyPair.private.encoded);
        }

        fun extractPublicKey(privateKey: String): String {
            val keySpec = PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKey))
            val keyFactory = KeyFactory.getInstance("RSA")
            val privateKeyObj = keyFactory.generatePrivate(keySpec) as RSAPrivateKey
            val publicKeyObj: RSAPublicKey = keyFactory.generatePublic(keySpec) as RSAPublicKey;
            return Base64.getEncoder().encodeToString(publicKeyObj.encoded)
        }

        fun createSignature(data: String, privateKey: String): String {
            val keySpec = PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKey))
            val keyFactory = KeyFactory.getInstance("RSA")
            val rsaPrivateKey = keyFactory.generatePrivate(keySpec) as RSAPrivateKey

            val signature = Signature.getInstance("SHA256withRSA")
            signature.initSign(rsaPrivateKey)
            signature.update(data.toByteArray(Charsets.UTF_8))

            val signatureBytes = signature.sign()
            return Base64.getEncoder().encodeToString(signatureBytes)
        }
    }
}