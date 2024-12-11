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
import com.futo.futopay.formatMoney
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

    override fun onDestroyMainView() {
        super.onDestroyMainView()
        _view = null
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

            _paymentManager = PaymentManager(StatePayment.instance, fragment, _overlayPaying) { success, _, exception ->
                if(success) {
                    UIDialogs.showDialog(context, R.drawable.ic_check, context.getString(R.string.payment_succeeded), context.getString(R.string.thanks_for_your_purchase_a_key_will_be_sent_to_your_email_after_your_payment_has_been_received), null, 0,
                        UIDialogs.Action("Ok", {}, UIDialogs.ActionStyle.PRIMARY));
                    _fragment.close(true);
                }
                else {
                    UIDialogs.showGeneralErrorDialog(context, context.getString(R.string.payment_failed), exception);
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
                    val country = StatePayment.instance.getPaymentCountryFromIP(true)?.let { c -> PaymentConfigurations.COUNTRIES.find { it.id.equals(c, ignoreCase = true) } };
                    val currency = country?.let { c -> PaymentConfigurations.CURRENCIES.find { it.id == c.defaultCurrencyId && (currencies.contains(it.id)) } };

                    if(currency != null && prices.containsKey(currency.id)) {
                        val price = prices[currency.id]!!;
                        withContext(Dispatchers.Main) {
                            _buttonBuyText.text = formatMoney(country.id, currency.id, price) + context.getString(R.string.plus_tax);
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
            val licenseInput = SlideUpMenuTextInput(context, context.getString(R.string.license));
            val productLicenseDialog = SlideUpMenuOverlay(context, findViewById<FrameLayout>(R.id.overlay_paid), context.getString(R.string.enter_license_key), context.getString(R.string.ok), true, licenseInput);
            productLicenseDialog.onOK.subscribe {
                val licenseText = licenseInput.text;
                if (licenseText.isNullOrEmpty()) {
                    UIDialogs.showDialogOk(context, R.drawable.ic_error_pred, context.getString(R.string.invalid_license_key));
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

                                UIDialogs.showDialogOk(context, R.drawable.ic_check, context.getString(R.string.your_license_key_has_been_set_an_app_restart_might_be_required));
                                _fragment.close(true);
                            }
                            else
                            {
                                UIDialogs.showDialogOk(context, R.drawable.ic_error_pred, context.getString(R.string.invalid_license_key));
                            }
                        }
                    }
                    catch(ex: Throwable) {
                        Logger.e("BuyFragment", "Failed to activate key", ex);
                        withContext(Dispatchers.Main) {
                            UIDialogs.showGeneralErrorDialog(context, context.getString(R.string.failed_to_activate_key), ex);
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