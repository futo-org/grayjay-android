package com.futo.platformplayer.fragment.mainactivity.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.futo.futopay.PaymentConfigurations
import com.futo.futopay.PaymentManager
import com.futo.platformplayer.BuildConfig
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StatePayment
import com.futo.platformplayer.views.overlays.LoaderOverlay
import com.futo.platformplayer.views.overlays.slideup.SlideUpMenuOverlay
import com.futo.platformplayer.views.overlays.slideup.SlideUpMenuTextInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BuyFragment : MainFragment() {
    override val isMainView : Boolean = true;
    override val isTab: Boolean = false;
    override val hasBottomBar: Boolean get() = true;

    private var _view: BuyView? = null;

    override fun onCreateMainView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = BuyView(this, inflater);
        _view = view;
        return view;
    }

    class BuyView: LinearLayout {
        private val _fragment: BuyFragment;

        private val _buttonBuy: LinearLayout;
        private val _buttonBuyText: TextView;
        private val _buttonPaid: LinearLayout;

        private val _overlayPaying: FrameLayout;

        private val _paymentManager: PaymentManager;

        private val _overlayLoading: LoaderOverlay;

        constructor(fragment: BuyFragment, inflater: LayoutInflater) : super(inflater.context) {
            _fragment = fragment;
            inflater.inflate(R.layout.fragment_buy, this);

            _buttonBuy = findViewById(R.id.button_buy);
            _buttonBuyText = findViewById(R.id.button_buy_text);
            _buttonPaid = findViewById(R.id.button_paid);
            _overlayLoading = findViewById(R.id.overlay_loading);
            _overlayPaying = findViewById(R.id.overlay_paying);

            _paymentManager = PaymentManager(StatePayment.instance, fragment, _overlayPaying) { success, purchaseId, exception ->
                if(success) {
                    UIDialogs.showDialog(context, R.drawable.ic_check, "Payment succeeded", "Thanks for your purchase, a key will be sent to your email after your payment has been received!", null, 0,
                        UIDialogs.Action("Ok", {}, UIDialogs.ActionStyle.PRIMARY));
                    _fragment.close(true);
                }
                else {
                    UIDialogs.showGeneralErrorDialog(context, "Payment failed", exception);
                }
            }

            if(!BuildConfig.IS_PLAYSTORE_BUILD)
                _buttonBuy.setOnClickListener {
                    buy();
                }
            else
                _buttonBuy.visibility = View.GONE;
            _buttonPaid.setOnClickListener {
                paid();
            }

            fragment.lifecycleScope.launch(Dispatchers.IO) {
                //Calling this function will cache first call
                try {
                    val currencies = StatePayment.instance.getAvailableCurrencies("grayjay");
                    val prices = StatePayment.instance.getAvailableCurrencyPrices("grayjay");
                    val country = StatePayment.instance.getPaymentCountryFromIP()?.let { c -> PaymentConfigurations.COUNTRIES.find { it.id.equals(c, ignoreCase = true) } };
                    val currency = country?.let { c -> PaymentConfigurations.CURRENCIES.find { it.id == c.defaultCurrencyId && (currencies.contains(it.id) ?: true) } };

                    if(currency != null && prices.containsKey(currency.id)) {
                        val price = prices[currency.id]!!;
                        val priceDecimal = (price.toDouble() / 100);
                        withContext(Dispatchers.Main) {
                            _buttonBuyText.text = currency.symbol + String.format("%.2f", priceDecimal);
                        }
                    }
                }
                catch(ex: Throwable) {
                    Logger.e("BuyFragment", "Failed to prefetch payment info", ex);
                }
            }
        }


        private fun buy() {
            _paymentManager.startPayment(StatePayment.instance, _fragment.lifecycleScope, "grayjay");
        }

        private fun paid() {
            val licenseInput = SlideUpMenuTextInput(context, "License");
            val productLicenseDialog = SlideUpMenuOverlay(context, findViewById<FrameLayout>(R.id.overlay_paid), "Enter license key", "Ok", true, licenseInput);
            productLicenseDialog.onOK.subscribe {
                val licenseText = licenseInput.text;
                if (licenseText.isNullOrEmpty()) {
                    UIDialogs.showDialogOk(context, R.drawable.ic_error_pred, "Invalid license key");
                    return@subscribe;
                }

                _fragment.lifecycleScope.launch(Dispatchers.IO) {

                    try{
                        val activationResult = StatePayment.instance.setPaymentLicense(licenseText);

                        withContext(Dispatchers.Main) {
                            if(activationResult) {
                                licenseInput.deactivate();
                                licenseInput.clear();
                                productLicenseDialog.hide(true);

                                UIDialogs.showDialogOk(context, R.drawable.ic_check, "Your license key has been set!\nAn app restart might be required.");
                                _fragment.close(true);
                            }
                            else
                            {
                                UIDialogs.showDialogOk(context, R.drawable.ic_error_pred, "Invalid license key");
                            }
                        }
                    }
                    catch(ex: Throwable) {
                        Logger.e("BuyFragment", "Failed to activate key", ex);
                        withContext(Dispatchers.Main) {
                            UIDialogs.showGeneralErrorDialog(context, "Failed to activate key", ex);
                        }
                    }
                }
            };
            productLicenseDialog.show();
        }

    }



    companion object {
        fun newInstance() = BuyFragment().apply {}
    }
}