import com.futo.platformplayer.api.media.Serializer
import com.futo.platformplayer.getNowDiffMiliseconds
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.subscription.SubscriptionFetchAlgorithm.Companion.TAG
import com.futo.platformplayer.subsexchange.ChannelRequest
import com.futo.platformplayer.subsexchange.ChannelResolve
import com.futo.platformplayer.subsexchange.ChannelResult
import com.futo.platformplayer.subsexchange.ExchangeContract
import com.futo.platformplayer.subsexchange.ExchangeContractResolve
import com.futo.platformplayer.toGzip
import com.futo.platformplayer.toHumanBytesSize
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
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.time.OffsetDateTime


class SubsExchangeClient(private val server: String, private val privateKey: String, private val contractTimeout: Int = 1000) {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val publicKey: String = extractPublicKey(privateKey)

    // Endpoints

    // Endpoint: Contract
    fun requestContract(vararg channels: ChannelRequest): ExchangeContract {
        val data = post("/api/Channel/Contract", Json.encodeToString(channels).toByteArray(Charsets.UTF_8), "application/json", contractTimeout)
        return Json.decodeFromString(data)
    }
    suspend fun requestContractAsync(vararg channels: ChannelRequest): ExchangeContract {
        val data = postAsync("/api/Channel/Contract", Json.encodeToString(channels).toByteArray(Charsets.UTF_8), "application/json")
        return Json.decodeFromString(data)
    }

    // Endpoint: Resolve
    fun resolveContract(contract: ExchangeContract, vararg resolves: ChannelResolve): Array<ChannelResult> {
        val contractResolve = convertResolves(*resolves)
        val contractResolveJson = Serializer.json.encodeToString(contractResolve);
        val contractResolveTimeStart = OffsetDateTime.now();
        val result = post("/api/Channel/Resolve?contractId=${contract.id}", contractResolveJson.toByteArray(Charsets.UTF_8), "application/json", 0, true)
        val contractResolveTime = contractResolveTimeStart.getNowDiffMiliseconds();
        Logger.v("SubsExchangeClient", "Subscription Exchange Resolve Request [${contractResolveTime}ms]:" + result);
        return Serializer.json.decodeFromString(result)
    }
    suspend fun resolveContractAsync(contract: ExchangeContract, vararg resolves: ChannelResolve): Array<ChannelResult> {
        val contractResolve = convertResolves(*resolves)
        val result = postAsync("/api/Channel/Resolve?contractId=${contract.id}", Serializer.json.encodeToString(contractResolve).toByteArray(Charsets.UTF_8), "application/json", true)
        return Serializer.json.decodeFromString(result)
    }


    private fun convertResolves(vararg resolves: ChannelResolve): ExchangeContractResolve {
        val data = Serializer.json.encodeToString(resolves)
        val signature = createSignature(data, privateKey)

        return ExchangeContractResolve(
            publicKey = publicKey,
            signature = signature,
            data = data
        )
    }

    // IO methods
    private fun post(query: String, body: ByteArray, contentType: String, timeout: Int = 0, gzip: Boolean = false): String {
        val url = URL("${server.trim('/')}$query")
        with(url.openConnection() as HttpURLConnection) {
            if(timeout > 0)
                this.connectTimeout = timeout
            requestMethod = "POST"
            setRequestProperty("Content-Type", contentType)
            doOutput = true


            if(gzip) {
                val gzipData = body.toGzip();
                setRequestProperty("Content-Encoding", "gzip");
                outputStream.write(gzipData);
                Logger.i("SubsExchangeClient", "SubsExchange using gzip (${body.size.toHumanBytesSize()} => ${gzipData.size.toHumanBytesSize()}");
            }
            else
                outputStream.write(body);

            val status = responseCode;
            Logger.i("SubsExchangeClient", "POST [${url}]: ${status}");

            if(status == 200)
                InputStreamReader(inputStream, StandardCharsets.UTF_8).use {
                    return it.readText()
                }
            else {
                var errorStr = "";
                try {
                    errorStr = InputStreamReader(errorStream, StandardCharsets.UTF_8).use {
                        return@use it.readText()
                    }
                }
                catch(ex: Throwable){}

                throw Exception("Exchange server resulted in code ${status}:\n" + errorStr);

            }
        }
    }
    private suspend fun postAsync(query: String, body: ByteArray, contentType: String, gzip: Boolean = false): String {
        return withContext(Dispatchers.IO) {
            post(query, body, contentType, 0, gzip)
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
            val publicKeyObj: PublicKey? = keyFactory.generatePublic(RSAPublicKeySpec(privateKeyObj.modulus, BigInteger.valueOf(65537)));
            var publicKeyBase64 = Base64.getEncoder().encodeToString(publicKeyObj?.encoded);
            var pem = "-----BEGIN PUBLIC KEY-----"
            while(publicKeyBase64.length > 0) {
                val length = Math.min(publicKeyBase64.length, 64);
                pem += "\n" + publicKeyBase64.substring(0, length);
                publicKeyBase64 = publicKeyBase64.substring(length);
            }
            return  pem + "\n-----END PUBLIC KEY-----";
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