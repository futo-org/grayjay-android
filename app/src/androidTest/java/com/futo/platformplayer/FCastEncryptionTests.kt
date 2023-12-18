package com.futo.platformplayer

import android.util.Base64
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.futo.platformplayer.casting.FCastCastingDevice
import com.futo.platformplayer.casting.Opcode
import com.futo.platformplayer.casting.models.FCastDecryptedMessage
import com.futo.platformplayer.casting.models.FCastEncryptedMessage
import com.futo.platformplayer.casting.models.FCastKeyExchangeMessage
import com.futo.platformplayer.casting.models.FCastPlayMessage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.spec.SecretKeySpec

@RunWith(AndroidJUnit4::class)
class FCastEncryptionTests {
    @Test
    fun testDHEncryptionSelf() {
        val keyPair1 = FCastCastingDevice.generateKeyPair()
        val keyPair2 = FCastCastingDevice.generateKeyPair()
        Log.i("testDHEncryptionSelf", "privates (1: ${Base64.encodeToString(keyPair1.private.encoded, Base64.NO_WRAP)}, 2: ${Base64.encodeToString(keyPair2.private.encoded, Base64.NO_WRAP)})")

        val keyExchangeMessage1 = FCastCastingDevice.getKeyExchangeMessage(keyPair1)
        val keyExchangeMessage2 = FCastCastingDevice.getKeyExchangeMessage(keyPair2)
        Log.i("testDHEncryptionSelf", "publics (1: ${keyExchangeMessage1.publicKey}, 2: ${keyExchangeMessage2.publicKey})")

        val aesKey1 = FCastCastingDevice.computeSharedSecret(keyPair1.private, keyExchangeMessage2)
        val aesKey2 = FCastCastingDevice.computeSharedSecret(keyPair2.private, keyExchangeMessage1)

        assertEquals(Base64.encodeToString(aesKey1.encoded, Base64.NO_WRAP), Base64.encodeToString(aesKey2.encoded, Base64.NO_WRAP))
        Log.i("testDHEncryptionSelf", "aesKey ${Base64.encodeToString(aesKey1.encoded, Base64.NO_WRAP)}")

        val message = FCastPlayMessage("text/html")
        val serializedBody = Json.encodeToString(message)
        val encryptedMessage = FCastCastingDevice.encryptMessage(aesKey1, FCastDecryptedMessage(Opcode.Play.value.toLong(), serializedBody))
        Log.i("testDHEncryptionSelf", Json.encodeToString(encryptedMessage))

        val decryptedMessage = FCastCastingDevice.decryptMessage(aesKey1, encryptedMessage)

        assertEquals(Opcode.Play.value.toLong(), decryptedMessage.opcode)
        assertEquals(serializedBody, decryptedMessage.message)
    }

    @Test
    fun testAESKeyGeneration() {
        val cases = listOf(
            listOf(
                //Public other
                "MIIBHzCBlQYJKoZIhvcNAQMBMIGHAoGBAP//////////yQ/aoiFowjTExmKLgNwc0SkCTgiKZ8x0Agu+pjsTmyJRSgh5jjQE3e+VGbPNOkMbMCsKbfJfFDdP4TVtbVHCReSFtXZiXn7G9ExC6aY37WsL/1y29Aa37e44a/taiZ+lrp8kEXxLH+ZJKGZR7OZTgf//////////AgECA4GEAAKBgEnOS0oHteVA+3kND3u4yXe7GGRohy1LkR9Q5tL4c4ylC5n4iSwWSoIhcSIvUMWth6KAhPhu05sMcPY74rFMSS2AGTNCdT/5KilediipuUMdFVvjGqfNMNH1edzW5mquIw3iXKdfQmfY/qxLTI2wccyDj4hHFhLCZL3Y+shsm3KF",
                //Private self
                "MIIBIQIBADCBlQYJKoZIhvcNAQMBMIGHAoGBAP//////////yQ/aoiFowjTExmKLgNwc0SkCTgiKZ8x0Agu+pjsTmyJRSgh5jjQE3e+VGbPNOkMbMCsKbfJfFDdP4TVtbVHCReSFtXZiXn7G9ExC6aY37WsL/1y29Aa37e44a/taiZ+lrp8kEXxLH+ZJKGZR7OZTgf//////////AgECBIGDAoGAeo/ceIeH8Jt1ZRNKX5aTHkMi23GCV1LtcS2O6Tktn9k8DCv7gIoekysQUhMyWtR+MsZlq2mXjr1JFpAyxl89rqoEPU6QDsGe9q8R4O8eBZ2u+48mkUkGSh7xPGRQUBvmhH2yk4hIEA8aK4BcYi1OTsCZtmk7pQq+uaFkKovD/8M=",
                //AES
                "7dpl1/6KQTTooOrFf2VlUOSqgrFHi6IYxapX0IxFfwk="
            ),
            listOf(
                //Public other
                "MIIBHzCBlQYJKoZIhvcNAQMBMIGHAoGBAP//////////yQ/aoiFowjTExmKLgNwc0SkCTgiKZ8x0Agu+pjsTmyJRSgh5jjQE3e+VGbPNOkMbMCsKbfJfFDdP4TVtbVHCReSFtXZiXn7G9ExC6aY37WsL/1y29Aa37e44a/taiZ+lrp8kEXxLH+ZJKGZR7OZTgf//////////AgECA4GEAAKBgGvIlCP/S+xpAuNEHSn4cEDOL1esUf+uMuY2Kp5J10a7HGbwzNd+7eYsgEc4+adddgB7hJgTvjsGg7lXUhHQ7WbfbCGgt7dbkx8qkic6Rgq4f5eRYd1Cgidw4MhZt7mEIOKrHweqnV6B9rypbXjbqauc6nGgtwx+Gvl6iLpVATRK",
                //Private self
                "MIIBIQIBADCBlQYJKoZIhvcNAQMBMIGHAoGBAP//////////yQ/aoiFowjTExmKLgNwc0SkCTgiKZ8x0Agu+pjsTmyJRSgh5jjQE3e+VGbPNOkMbMCsKbfJfFDdP4TVtbVHCReSFtXZiXn7G9ExC6aY37WsL/1y29Aa37e44a/taiZ+lrp8kEXxLH+ZJKGZR7OZTgf//////////AgECBIGDAoGAMXmiIgWyutbaO+f4UiMAb09iVVSCI6Lb6xzNyD2MpUZyk4/JOT04Daj4JeCKFkF1Fq79yKhrnFlXCrF4WFX00xUOXb8BpUUUH35XG5ApvolQQLL6N0om8/MYP4FK/3PUxuZAJz45TUsI/v3u6UqJelVTNL83ltcFbZDIfEVftRA=",
                //AES
                "a2tUSxnXifKohfNocAQHkAlPffDv6ReihJ7OojBGt0Q="
            )
        )

        for (case in cases) {
            val decodedPrivateKey1 = Base64.decode(case[1], Base64.NO_WRAP)
            val keyExchangeMessage2 = FCastKeyExchangeMessage(1, case[0])

            val keyFactory = KeyFactory.getInstance("DH")
            val privateKeySpec = PKCS8EncodedKeySpec(decodedPrivateKey1)
            val privateKey = keyFactory.generatePrivate(privateKeySpec)
            val aesKey1 = FCastCastingDevice.computeSharedSecret(privateKey, keyExchangeMessage2)
            assertEquals(case[2], Base64.encodeToString(aesKey1.encoded, Base64.NO_WRAP))
        }
    }

    @Test
    fun testDHEncryptionKnown() {
        val decodedPrivateKey1 = Base64.decode("MIIDJwIBADCCAhgGCSqGSIb3DQEDATCCAgkCggEBAJVHXPXZPllsP80dkCrdAvQn9fPHIQMTu0X7TVuy5f4cvWeM1LvdhMmDa+HzHAd3clrrbC/Di4X0gHb6drzYFGzImm+y9wbdcZiYwgg9yNiW+EBi4snJTRN7BUqNgJatuNUZUjmO7KhSoK8S34Pkdapl1OwMOKlWDVZhGG/5i5/J62Du6LAwN2sja8c746zb10/WHB0kdfowd7jwgEZ4gf9+HKVv7gZteVBq3lHtu1RDpWOSfbxLpSAIZ0YXXIiFkl68ZMYUeQZ3NJaZDLcU7GZzBOJh+u4zs8vfAI4MP6kGUNl9OQnJJ1v0rIb/yz0D5t/IraWTQkLdbTvMoqQGywsCggEAQt67naWz2IzJVuCHh+w/Ogm7pfSLiJp0qvUxdKoPvn48W4/NelO+9WOw6YVgMolgqVF/QBTTMl/Hlivx4Ek3DXbRMUp2E355Lz8NuFnQleSluTICTweezy7wnHl0UrB3DhNQeC7Vfd95SXnc7yPLlvGDBhllxOvJPJxxxWuSWVWnX5TMzxRJrEPVhtC+7kMlGwsihzSdaN4NFEQD8T6AL0FG2ILgV68ZtvYnXGZ2yPoOPKJxOjJX/Rsn0GOfaV40fY0c+ayBmibKmwTLDrm3sDWYjRW7rGUhKlUjnPx+WPrjjXJQq5mR/7yXE0Al/ozgTEOZrZZWm+kaVG9JeGk8egSCAQQCggEAECNvEczf0y6IoX/IwhrPeWZ5IxrHcpwjcdVAuyZQLLlOq0iqnYMFcSD8QjMF8NKObfZZCDQUJlzGzRsG0oXsWiWtmoRvUZ9tQK0j28hDylpbyP00Bt9NlMgeHXkAy54P7Z2v/BPCd3o23kzjgXzYaSRuCFY7zQo1g1IQG8mfjYjdE4jjRVdVrlh8FS8x4OLPeglc+cp2/kuyxaVEfXAG84z/M8019mRSfdczi4z1iidPX6HgDEEWsN42Ud60mNKy5jsQpQYkRdOLmxR3+iQEtGFjdzbVhVCUr7S5EORU9B1MOl5gyPJpjfU3baOqrg6WXVyTvMDaA05YEnAHQNOOfA==", Base64.NO_WRAP)
        val keyExchangeMessage2 = FCastKeyExchangeMessage(1, "MIIDJTCCAhgGCSqGSIb3DQEDATCCAgkCggEBAJVHXPXZPllsP80dkCrdAvQn9fPHIQMTu0X7TVuy5f4cvWeM1LvdhMmDa+HzHAd3clrrbC/Di4X0gHb6drzYFGzImm+y9wbdcZiYwgg9yNiW+EBi4snJTRN7BUqNgJatuNUZUjmO7KhSoK8S34Pkdapl1OwMOKlWDVZhGG/5i5/J62Du6LAwN2sja8c746zb10/WHB0kdfowd7jwgEZ4gf9+HKVv7gZteVBq3lHtu1RDpWOSfbxLpSAIZ0YXXIiFkl68ZMYUeQZ3NJaZDLcU7GZzBOJh+u4zs8vfAI4MP6kGUNl9OQnJJ1v0rIb/yz0D5t/IraWTQkLdbTvMoqQGywsCggEAQt67naWz2IzJVuCHh+w/Ogm7pfSLiJp0qvUxdKoPvn48W4/NelO+9WOw6YVgMolgqVF/QBTTMl/Hlivx4Ek3DXbRMUp2E355Lz8NuFnQleSluTICTweezy7wnHl0UrB3DhNQeC7Vfd95SXnc7yPLlvGDBhllxOvJPJxxxWuSWVWnX5TMzxRJrEPVhtC+7kMlGwsihzSdaN4NFEQD8T6AL0FG2ILgV68ZtvYnXGZ2yPoOPKJxOjJX/Rsn0GOfaV40fY0c+ayBmibKmwTLDrm3sDWYjRW7rGUhKlUjnPx+WPrjjXJQq5mR/7yXE0Al/ozgTEOZrZZWm+kaVG9JeGk8egOCAQUAAoIBAGlL9EYsrFz3I83NdlwhM241M+M7PA9P5WXgtdvS+pcalIaqN2IYdfzzCUfye7lchVkT9A2Y9eWQYX0OUhmjf8PPKkRkATLXrqO5HTsxV96aYNxMjz5ipQ6CaErTQaPLr3OPoauIMPVVI9zM+WT0KOGp49YMyx+B5rafT066vOVbF/0z1crq0ZXxyYBUv135rwFkIHxBMj5bhRLXKsZ2G5aLAZg0DsVam104mgN/v75f7Spg/n5hO7qxbNgbvSrvQ7Ag/rMk5T3sk7KoM23Qsjl08IZKs2jjx21MiOtyLqGuCW6GOTNK4yEEDF5gA0K13eXGwL5lPS0ilRw+Lrw7cJU=")

        val keyFactory = KeyFactory.getInstance("DH")
        val privateKeySpec = PKCS8EncodedKeySpec(decodedPrivateKey1)
        val privateKey = keyFactory.generatePrivate(privateKeySpec)
        val aesKey1 = FCastCastingDevice.computeSharedSecret(privateKey, keyExchangeMessage2)
        assertEquals("vI5LGE625zGEG350ggkyBsIAXm2y4sNohiPcED1oAEE=", Base64.encodeToString(aesKey1.encoded, Base64.NO_WRAP))

        val message = FCastPlayMessage("text/html")
        val serializedBody = Json.encodeToString(message)
        val encryptedMessage = FCastCastingDevice.encryptMessage(aesKey1, FCastDecryptedMessage(Opcode.Play.value.toLong(), serializedBody))
        val decryptedMessage = FCastCastingDevice.decryptMessage(aesKey1, encryptedMessage)

        assertEquals(Opcode.Play.value.toLong(), decryptedMessage.opcode)
        assertEquals(serializedBody, decryptedMessage.message)
    }

    @Test
    fun testDecryptMessageKnown() {
        val encryptedMessage = Json.decodeFromString<FCastEncryptedMessage>("{\"version\":1,\"iv\":\"C4H70VC5FWrNtkty9/cLIA==\",\"blob\":\"K6/N7JMyi1PFwKhU0mFj7ZJmd/tPp3NCOMldmQUtDaQ7hSmPoIMI5QNMOj+NFEiP4qTgtYp5QmBPoQum6O88pA==\"}")
        val aesKey = SecretKeySpec(Base64.decode("+hr9Jg8yre7S9WGUohv2AUSzHNQN514JPh6MoFAcFNU=", Base64.NO_WRAP), "AES")
        val decryptedMessage = FCastCastingDevice.decryptMessage(aesKey, encryptedMessage)
        assertEquals(Opcode.Play.value.toLong(), decryptedMessage.opcode)
        assertEquals("{\"container\":\"text/html\"}", decryptedMessage.message)
    }
}