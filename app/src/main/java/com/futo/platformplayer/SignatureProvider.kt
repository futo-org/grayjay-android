package com.futo.platformplayer

import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

data class KeyPair(val privateKey: String, val publicKey: String);

class SignatureProvider {
    companion object {
        fun sign(text: String, privateKey: String): String {
            val privateKeyBytes = Base64.getDecoder().decode(privateKey);
            val keySpec = PKCS8EncodedKeySpec(privateKeyBytes);
            val keyFactory = KeyFactory.getInstance("RSA");
            val privateKeyObject = keyFactory.generatePrivate(keySpec);

            val signature = Signature.getInstance("SHA512withRSA");
            signature.initSign(privateKeyObject);
            signature.update(text.toByteArray());
            val signatureBytes = signature.sign();

            return Base64.getEncoder().encodeToString(signatureBytes);
        }

        fun verify(text: String, signature: String, publicKey: String): Boolean {
            val publicKeyBytes = Base64.getDecoder().decode(publicKey);
            val keySpec = X509EncodedKeySpec(publicKeyBytes);
            val keyFactory = KeyFactory.getInstance("RSA");
            val publicKeyObject = keyFactory.generatePublic(keySpec);

            val signatureBytes = Base64.getDecoder().decode(signature);
            val verifySignature = Signature.getInstance("SHA512withRSA");
            verifySignature.initVerify(publicKeyObject);
            verifySignature.update(text.toByteArray());

            return verifySignature.verify(signatureBytes);
        }

        fun generateKeyPair(): KeyPair {
            val keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            val keyPair = keyPairGenerator.generateKeyPair();

            val privateKeyBytes = keyPair.private.encoded;
            val privateKey = Base64.getEncoder().encodeToString(privateKeyBytes);

            val publicKeyBytes = keyPair.public.encoded;
            val publicKey = Base64.getEncoder().encodeToString(publicKeyBytes);

            return KeyPair(privateKey, publicKey);
        }
    }
}