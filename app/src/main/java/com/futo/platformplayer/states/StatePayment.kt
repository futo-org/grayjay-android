package com.futo.platformplayer.states

import com.futo.futopay.PaymentState
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.stores.StringStorage

const val isTestingPayment = false;
class StatePayment : PaymentState(if(!isTestingPayment) VERIFICATION_PUBLIC_KEY else VERIFICATION_PUBLIC_KEY_TESTING) {
    override val isTesting: Boolean get() = isTestingPayment;

    override fun savePaymentKey(licenseKey: String, licenseActivation: String) {
        FragmentedStorage.get<StringStorage>("paymentLicenseKey").setAndSave(licenseKey);
        FragmentedStorage.get<StringStorage>("paymentLicenseActivation").setAndSave(licenseActivation);
    }

    override fun getPaymentKey(): Pair<String, String> {
        return Pair(FragmentedStorage.get<StringStorage>("paymentLicenseKey").value, FragmentedStorage.get<StringStorage>("paymentLicenseActivation").value);
    }

    companion object {
        private val VERIFICATION_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAzJqqETLa42xw4AfbNOLQ" +
        "olMdMiGgg8DAC4RXEcH4/gytLhaqp1XsjiiMkADi1C7sDtGj6kOuAuQkqXQKpZ2d" +
        "JSZsO+GPyop6DmgfAM6MQgOgFUpwsb3Lt3SvskJcls8MeOC+jg+GjjcuJI8qOfYe" +
        "vj4/7wAOpqzAwocTYnJivlK5nrC+qNtUC2HZX93OVu69aU5yvA1SQe9GiiU7vBld" +
        "+CbzHxTcABCK/THu/BpLtGx0M7W3HNMKK1Z79dopCL9ZZWbWdkGDY8Zf39Gn/WVr" +
        "s5elBvPzU+AfNYty77vx2r+sKgyohlbz4KVYpnw8HfawKcwuRE/GUyD3F2hUcXy8" +
        "dQIDAQAB"

        private val VERIFICATION_PUBLIC_KEY_TESTING = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAqyDuxsRtD5gmBoLCNoZa" +
                "XSRTwyUxgzcPHzLZkvomXVSQqzD+3aOKngcTKAZ83rm4GvoyMlBukxQMLShannSx" +
                "k8GQGTCT7VStQKNc4lKVER5ASB6aEaypaFMIYI3rXN1xLF1LqY/j7cu5GgMsvAuU" +
                "VYFBexYFF6xcC5JDBZW6Pw/KYoJm3rswFixjPMGESmZRFCjjdAkHk47BhRPFBlvz" +
                "wv9Ez1stdHcTpa/odEXIeJWIsZk9DHtCNCZyt6B6FXojVzrXsF2TxCNHGcHhlX43" +
                "ALgQikiRcof1FsxoewTQhjLwMiDqB02mHCdRxssdnW3xadqyK678kQKfoIB1KB2N" +
                "/QIDAQAB";
        private var _instance : StatePayment? = null;
        val instance : StatePayment
            get(){
                if(_instance == null)
                    _instance = StatePayment();
                return _instance!!;
            };
    }
}
