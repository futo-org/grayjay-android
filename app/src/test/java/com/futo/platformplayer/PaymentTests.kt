package com.futo.platformplayer

import com.futo.platformplayer.states.StatePayment
import org.junit.Assert
import org.junit.Test

class PaymentTests {

    /*private val PAYMENT_KEY_TEST = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAs+PeflxecURXXi0WLVbXnUF0XwOKBleXOERBBGxW57X76mqIBC7FddogV/Z5Ym/YtxDNmAWxBRjO0iz8bz1iNT5AdGQ1y+kFvPaSKbJILmte2cWmFn2ga1uqAesUnLVVeWF9uA/nScABb7o1DaAhsONQfosfTz8X87S7r8g28A4wjYB5WR657NiSdXHOIyyIFrozM1JvHfGXQ7PlER1lBkbExDC6aLc7LRQY/++5pPWS9m/O+9EpeztqBkQZLIql6BvCZVYhOgMzLNCsYvvIN/mmY27eTYeCAm7v2fDWSZ1Badc083HOecpqDq2XCDewzf5Ea5t/SI93XhPiEi9UewIDAQAB";

    @Test
    fun `check payment validation`() {
        val lkey = "Q42S-48S8-EJJZ-N9HH-ARAZ-GKBC-F1JV-K49E";
        val activationKey = "m3VoUrXfhZumP4AoVqE6tRpxw5yJYSr49wRX_BO7-urNjjkAjWFFtNFR1Ytl8hsUo8zY_WP17uQP62W0Ocq8I8p46giyKXvFL8VbtqMSn4s4YUX6kYxgtKlzOd6-yNOF7RLd1DZJqHhpJ5juO3qmi9fKWfO9XiV368s-H7HNBWY8hSkprf40ANdKJ7m7QZ2Gf7kSM8Ld8KWmXCHooVfjfpl6vx5GXEvEumCPDKhwg-EYd2MBizUaDszdm_dufzcjC9EwpxO9SS-EjBdhpOSSRWU7gebDON8d4zTKBgkH0T6PBhF_iEuvFTmeIp7V5f_g1dCUuUCDEq6NvwikH3SrXA";
        val validator = StatePayment(PAYMENT_KEY_TEST);

        Assert.assertTrue(validator.validate(lkey, activationKey));
    }*/
}