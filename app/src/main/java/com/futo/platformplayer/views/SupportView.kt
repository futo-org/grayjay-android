package com.futo.platformplayer.views

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.view.size
import com.bumptech.glide.Glide
import com.futo.platformplayer.R
import com.futo.platformplayer.fragment.mainactivity.main.PolycentricProfile
import com.futo.platformplayer.images.GlideHelper.Companion.crossfade
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.views.buttons.BigButton
import com.futo.polycentric.core.toURLInfoSystemLinkUrl
import com.google.android.material.imageview.ShapeableImageView

class SupportView : LinearLayout {
    private val _layoutStore: LinearLayout
    private val _buttonPromotion: BigButton
    private val _layoutMemberships: LinearLayout
    private val _layoutMembershipEntries: LinearLayout
    private val _layoutPromotions: LinearLayout
    private val _layoutPromotionEntries: LinearLayout
    private val _layoutDonation: LinearLayout
    private val _layoutDonationEntries: LinearLayout
    private val _buttonStore: BigButton
    private val _imagePromotion: ShapeableImageView
    private var _textNoSupportOptionsSet: TextView
    private var _polycentricProfile: PolycentricProfile? = null

    val hasSupportItems: Boolean get() {
        return (_layoutPromotions.isVisible && _buttonPromotion.isVisible) ||
                (_layoutMemberships.isVisible && _layoutMembershipEntries.isVisible && _layoutMembershipEntries.size > 0) ||
                (_layoutDonation.isVisible && _layoutDonationEntries.isVisible && _layoutDonationEntries.size > 0) ||
                _buttonStore.isVisible;
    };

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        inflate(context, R.layout.view_support, this);

        _layoutStore = findViewById(R.id.layout_store)
        _buttonStore = findViewById(R.id.button_store)
        _layoutMemberships = findViewById(R.id.layout_memberships)
        _layoutMembershipEntries = findViewById(R.id.layout_membership_entries)
        _layoutPromotions = findViewById(R.id.layout_promotions)
        _layoutPromotionEntries = findViewById(R.id.layout_promotion_entries)
        _layoutDonation = findViewById(R.id.layout_donation)
        _layoutDonationEntries = findViewById(R.id.layout_donation_entries)
        _buttonPromotion = findViewById(R.id.button_promotion)
        _imagePromotion = findViewById(R.id.image_promotion)
        _textNoSupportOptionsSet = findViewById(R.id.text_no_support_options_set)

        _buttonPromotion.onClick.subscribe { openPromotion() }
        _imagePromotion.setOnClickListener { openPromotion() }
        _buttonStore.onClick.subscribe {
            val storeUrl = _polycentricProfile?.systemState?.store ?: return@subscribe
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(storeUrl))
            context.startActivity(browserIntent)
        }
    }

    private fun openPromotion() {
        val promotionUrl = _polycentricProfile?.systemState?.promotion ?: return
        val uri = Uri.parse(promotionUrl)
        if (!uri.isAbsolute && (uri.scheme == "https" || uri.scheme == "http")) {
            return
        }

        val browserIntent = Intent(Intent.ACTION_VIEW, uri)
        context.startActivity(browserIntent)
    }

    private fun setMemberships(urls: List<String>) {
        _layoutMembershipEntries.removeAllViews()
        for (url in urls) {
            val button = createMembershipButton(url)
            _layoutMembershipEntries.addView(button)
        }
        _layoutMemberships.visibility = if (urls.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun createMembershipButton(url: String): BigButton {
        val uri = Uri.parse(url)
        val name: String
        val iconDrawableId: Int

        if (uri.host?.contains("patreon.com") == true) {
            name = "Patreon"
            iconDrawableId = R.drawable.patreon
        } else {
            name = uri.host.toString()
            iconDrawableId = R.drawable.ic_web_white
        }

        return BigButton(context, name, "Become a member on $name", iconDrawableId) {
            val intent = Intent(Intent.ACTION_VIEW);
            intent.data = uri;
            context.startActivity(intent);
        }.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        };
    }

    private fun setDonations(destinations: List<String>) {
        _layoutDonationEntries.removeAllViews()
        for (destination in destinations) {
            val button = createDonationButton(destination)
            _layoutDonationEntries.addView(button)
        }
        _layoutDonation.visibility = if (destinations.isEmpty()) View.GONE else View.VISIBLE
    }

    private enum class CryptoType {
        BITCOIN, ETHEREUM, LITECOIN, RIPPLE, UNKNOWN
    }

    private fun getCryptoType(address: String): CryptoType {
        val btcRegex = Regex("^(1|3)[1-9A-HJ-NP-Za-km-z]{25,34}$|^(bc1)[0-9a-zA-HJ-NP-Z]{39,59}$")
        val ethRegex = Regex("^(0x)[0-9a-fA-F]{40}$")
        val ltcRegex = Regex("^(L|M)[1-9A-HJ-NP-Za-km-z]{26,33}$|^(ltc1)[0-9a-zA-HJ-NP-Z]{39,59}$")
        val xrpRegex = Regex("^r[1-9A-HJ-NP-Za-km-z]{24,34}$")

        return when {
            ltcRegex.matches(address) -> CryptoType.LITECOIN
            btcRegex.matches(address) -> CryptoType.BITCOIN
            ethRegex.matches(address) -> CryptoType.ETHEREUM
            xrpRegex.matches(address) -> CryptoType.RIPPLE
            else -> CryptoType.UNKNOWN
        }
    }

    private fun createDonationButton(destination: String): BigButton {
        val uri = Uri.parse(destination)

        var action: (() -> Unit)?
        val (name, iconDrawableId, cryptoType) = if (uri.scheme == "http" || uri.scheme == "https") {
            val hostName = uri.host ?: ""

            action = {
                val intent = Intent(Intent.ACTION_VIEW);
                intent.data = uri;
                context.startActivity(intent);
            }

            if (hostName.contains("paypal.com")) {
                Triple("Paypal", R.drawable.paypal, null) // Replace with your actual PayPal drawable resource
            } else {
                Triple(hostName, R.drawable.ic_web_white, null) // Replace with your generic web drawable resource
            }
        } else {
            action = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Donation Address", destination)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }

            when (getCryptoType(destination)) {
                CryptoType.BITCOIN -> Triple("Bitcoin", R.drawable.bitcoin, CryptoType.BITCOIN)
                CryptoType.ETHEREUM -> Triple("Ethereum", R.drawable.ethereum, CryptoType.ETHEREUM)
                CryptoType.LITECOIN -> Triple("Litecoin", R.drawable.litecoin, CryptoType.LITECOIN)
                CryptoType.RIPPLE -> Triple("Ripple", R.drawable.ripple, CryptoType.RIPPLE)
                CryptoType.UNKNOWN -> Triple("Unknown", R.drawable.ic_paid, CryptoType.UNKNOWN)
            }
        }

        return BigButton(context, name, destination.takeIf { cryptoType != null } ?: "Donate on $name", iconDrawableId, action).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        };
    }

    private fun setPromotions(url: String?, imageUrl: String?) {
        Logger.i(TAG, "setPromotions($url, $imageUrl)")

        if (url != null) {
            _layoutPromotions.visibility = View.VISIBLE

            if (imageUrl != null) {
                _buttonPromotion.visibility = View.GONE
                _imagePromotion.visibility = View.VISIBLE

                Glide.with(_imagePromotion)
                    .load(imageUrl)
                    .crossfade()
                    .into(_imagePromotion)
            } else {
                _buttonPromotion.setSecondaryText(url)
                _buttonPromotion.visibility = View.VISIBLE
                _imagePromotion.visibility = View.GONE
            }
        } else {
            _layoutPromotions.visibility = View.GONE
        }
    }

    fun setPolycentricProfile(profile: PolycentricProfile?) {
        if (_polycentricProfile == profile) {
            return
        }

        if (profile != null) {
            setDonations(profile.systemState.donationDestinations);
            setMemberships(profile.systemState.membershipUrls);

            val imageManifest = profile.systemState.promotionBanner?.imageManifestsList?.firstOrNull()
            if (imageManifest != null) {
                val imageUrl = imageManifest.toURLInfoSystemLinkUrl(profile.system.toProto(), imageManifest.process, profile.systemState.servers.toList());
                setPromotions(profile.systemState.promotion, imageUrl);
            } else {
                setPromotions(null, null);
            }

            if (profile.systemState.store.isNotEmpty()) {
                _layoutStore.visibility = View.VISIBLE
            } else {
                _layoutStore.visibility = View.GONE
            }

            _textNoSupportOptionsSet.visibility = View.GONE
        } else {
            setDonations(listOf());
            setMemberships(listOf());
            setPromotions(null, null);
            _layoutStore.visibility = View.GONE
            _textNoSupportOptionsSet.visibility = View.VISIBLE
        }

        _polycentricProfile = profile
    }

    companion object {
        const val TAG = "SupportView";
    }
}