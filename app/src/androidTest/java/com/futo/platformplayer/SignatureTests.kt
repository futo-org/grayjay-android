package com.futo.platformplayer

import android.util.Log
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import org.junit.Test

import org.junit.Assert.*

class SignatureTests {
    @Test
    fun roundtripTest() {
        val keys = SignatureProvider.generateKeyPair();
        Log.i(TAG, "public key: ${keys.publicKey}\nprivate key: ${keys.privateKey}");

        val signature = SignatureProvider.sign("test", keys.privateKey);
        assertTrue(SignatureProvider.verify("test", signature, keys.publicKey));
    }

    @Test
    fun decodeTest() {
        assertTrue(SignatureProvider.verify(
            "//this is just an empty script",
            "eLdlDIcmpTQmfpCumB5NQwFa0ZDNU8hkRB12/Lg+CdTwPrfTIylGeN6jpTmJrEivyLjj" +
                    "5qHWZeNmrHP++9XFwfwzcaXNspKU9YrL3+Bsy2WNnXfQDeB2t4AkzWYAEfm8/kEcK0Ov8dzy0KW" +
                    "lJsxmW+Oj3mFNVP6PV5ZQY1Gju6W8Jw0sGCxnbuhswtRDPwBKnZQUhlZEXPvbrcblW1q5fCESnf" +
                    "oiJ2MHR5epgHfAuMsoY9EAHVXuyrLvmbWADeVwC5jvWLAkJKw68rQmARqV5BBWkpqFEBQcg50CR" +
                    "vTXtPr8IDjW7yiJ6x9nTG3nokTJn3fj2D3hBEHttEG+KhTMlQ==",
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAucY14D6cl0fK5fHOTUfKMz1iQmfJMg" +
                    "Q+c4MfqlArGCv7YDTvazeQL9dsrCqlYx+o+AlYzbohGXYsYsJO474+Ia5VEcpCnMm6YPRhV0H+8bke" +
                    "lgE2vMB2MB54zIxVRVEA1CBPBrWle8qlBMmqXI8ndjQjIYZNZD0CN0ckOgLO3OX8+P6f+zYHbRINCXi" +
                    "T1L7DstJ4FacqE7b2+aNKiMogUoaq7H3dXxJXj32HMFZevrs8ZFxTvbIP4KkazRrdfnZPdWKXk9pv" +
                    "P8EI21RNKKr2NtVNJyRPxI1uWlvYtGeSLcNUioHshNRQ4SxRSG8p1VTBmUpS0cJoZSCmO/0W9doyzwI" +
                    "DAQAB"));
    }

    @Test
    fun testSignature() {
        val somePlugin = SourcePluginConfig(
            "Script",
            "A plugin that adds Script as a source",
            "FUTO",
            "https://futo.org",
            "https://futo.org",
            "./Script.js",
            1,
            "./script.png",
            "394dba51-fa0c-450c-8f17-6a00df362218",
            "eLdlDIcmpTQmfpCumB5NQwFa0ZDNU8hkRB12/Lg+CdTwPrfTIylGeN6jpTmJrEivyLjj" +
                    "5qHWZeNmrHP++9XFwfwzcaXNspKU9YrL3+Bsy2WNnXfQDeB2t4AkzWYAEfm8/kEcK0Ov8dzy0KW" +
                    "lJsxmW+Oj3mFNVP6PV5ZQY1Gju6W8Jw0sGCxnbuhswtRDPwBKnZQUhlZEXPvbrcblW1q5fCESnf" +
                    "oiJ2MHR5epgHfAuMsoY9EAHVXuyrLvmbWADeVwC5jvWLAkJKw68rQmARqV5BBWkpqFEBQcg50CR" +
                    "vTXtPr8IDjW7yiJ6x9nTG3nokTJn3fj2D3hBEHttEG+KhTMlQ==",
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAucY14D6cl0fK5fHOTUfKM" +
                    "z1iQmfJMgQ+c4MfqlArGCv7YDTvazeQL9dsrCqlYx+o+AlYzbohGXYsYsJO474+Ia5VEcpC" +
                    "nMm6YPRhV0H+8bkelgE2vMB2MB54zIxVRVEA1CBPBrWle8qlBMmqXI8ndjQjIYZNZD0CN0c" +
                    "kOgLO3OX8+P6f+zYHbRINCXiT1L7DstJ4FacqE7b2+aNKiMogUoaq7H3dXxJXj32HMFZevrs8ZF" +
                    "xTvbIP4KkazRrdfnZPdWKXk9pvP8EI21RNKKr2NtVNJyRPxI1uWlvYtGeSLcNUioHshNRQ4SxRSG8p1VTBmUpS0cJoZSCmO/0W9doyzwIDAQAB"
        );
        val script = "//this is just an empty script";

        assert(somePlugin.validate(script), { "Invalid signature" });

    }

    companion object {
        private const val TAG = "SignatureTests";
    }
}