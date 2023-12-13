package com.futo.platformplayer.states

import com.futo.platformplayer.Settings
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.api.media.models.ResultCapabilities
import com.futo.platformplayer.api.media.models.channels.IPlatformChannel
import com.futo.platformplayer.api.media.models.channels.SerializedChannel
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.api.media.structures.*
import com.futo.platformplayer.api.media.structures.ReusablePager.Companion.asReusable
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.Event2
import com.futo.platformplayer.engine.exceptions.PluginException
import com.futo.platformplayer.engine.exceptions.ScriptCaptchaRequiredException
import com.futo.platformplayer.engine.exceptions.ScriptCriticalException
import com.futo.platformplayer.exceptions.ChannelException
import com.futo.platformplayer.findNonRuntimeException
import com.futo.platformplayer.fragment.mainactivity.main.PolycentricProfile
import com.futo.platformplayer.getNowDiffDays
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.Subscription
import com.futo.platformplayer.models.SubscriptionGroup
import com.futo.platformplayer.polycentric.PolycentricCache
import com.futo.platformplayer.resolveChannelUrl
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.stores.SubscriptionStorage
import com.futo.platformplayer.stores.v2.ReconstructStore
import com.futo.platformplayer.stores.v2.ManagedStore
import com.futo.platformplayer.subscription.SubscriptionFetchAlgorithm
import com.futo.platformplayer.subscription.SubscriptionFetchAlgorithms
import kotlinx.coroutines.*
import java.time.OffsetDateTime
import java.util.concurrent.ExecutionException
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinTask
import kotlin.collections.ArrayList
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.streams.asSequence
import kotlin.streams.toList
import kotlin.system.measureTimeMillis

/***
 * Used to maintain subscription groups
 */
class StateSubscriptionGroups {
    private val _subGroups = FragmentedStorage.storeJson<SubscriptionGroup>("subscription_groups")
        .withUnique { it.id }
        .load();

    fun getSubscriptionGroup(id: String): SubscriptionGroup? {
        return _subGroups.findItem { it.id == id };
    }
    fun getSubscriptionGroups(): List<SubscriptionGroup> {
        return _subGroups.getItems();
    }
    fun updateSubscriptionGroup(subGroup: SubscriptionGroup) {
        _subGroups.save(subGroup);
    }
    fun deleteSubscriptionGroup(id: String){
        val group = getSubscriptionGroup(id);
        if(group != null)
            _subGroups.delete(group);
    }


    companion object {
        const val TAG = "StateSubscriptionGroups";
        const val VERSION = 1;

        private var _instance : StateSubscriptionGroups? = null;
        val instance : StateSubscriptionGroups
            get(){
            if(_instance == null)
                _instance = StateSubscriptionGroups();
            return _instance!!;
        };

        fun finish() {
            _instance?.let {
                _instance = null;
            }
        }
    }
}