package com.futo.platformplayer.states

import android.view.View
import android.view.WindowManager
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.Event2
import com.futo.platformplayer.dialogs.PluginUpdateDialog
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.ImageVariable
import com.futo.platformplayer.serializers.OffsetDateTimeNullableSerializer
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.stores.StringHashSetStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.OffsetDateTime
import java.util.UUID

class StateAnnouncement {
    private val _lock = Object();

    private val _sessionAnnouncementsNever = FragmentedStorage.get<StringHashSetStorage>("announcementNeverSession");
    private val _sessionAnnouncements: HashMap<String, Announcement> = hashMapOf();
    private val _sessionActions: HashMap<String, (announcement: Announcement)->Unit> = hashMapOf();

    private val _announcementsNever = FragmentedStorage.get<StringHashSetStorage>("announcementNever");
    private val _announcementsStore = FragmentedStorage.storeJson<Announcement>("announcements").load();
    private val _announcementsClosed = HashSet<String>();

    val onAnnouncementChanged = Event0();

    suspend fun loadAnnouncements() {
        Logger.i(TAG, "Loading announcements")

        withContext(Dispatchers.IO) {
            try {
                val client = ManagedHttpClient();
                val response = client.get("https://announcements.grayjay.app/grayjay.json");
                if (response.isOk && response.body != null) {
                    val body = response.body.string();
                    val announcements = Json.decodeFromString<List<Announcement>>(body);

                    synchronized(_lock) {
                        for (announcement in announcements) {
                            if (_sessionAnnouncements.containsKey(announcement.id))
                                return@synchronized;

                            if (!_announcementsStore.hasItem { it.id == announcement.id }) {
                                _announcementsStore.saveAsync(announcement);
                            }
                        }
                    }

                    withContext(Dispatchers.Main) {
                        onAnnouncementChanged.emit();
                    }
                }
            } catch (e: Throwable) {
                Logger.w(TAG, "Failed to load announcements", e)
            }
        }

        Logger.i(TAG, "Finished loading announcements")
    }

    fun registerAnnouncement(id: String?, title: String, msg: String, announceType: AnnouncementType = AnnouncementType.SESSION, time: OffsetDateTime? = null, category: String? = null, actionButton: String, action: ((announcement: Announcement)->Unit)) {
        synchronized(_lock) {
            val idActual = id ?: UUID.randomUUID().toString();
            val announcement = SessionAnnouncement(idActual, title, msg, announceType, time, category, actionButton, idActual);
            _sessionActions[idActual] = action;
            registerAnnouncementSession(announcement);
        }
    }
    fun registerAnnouncement(id: String?, title: String, msg: String, announceType: AnnouncementType = AnnouncementType.SESSION, time: OffsetDateTime? = null, category: String? = null, actionButton: String, action: ((announcement: Announcement)->Unit), cancelButton: String? = null, cancelAction: ((announcement: Announcement)-> Unit)? = null) {
        synchronized(_lock) {
            val idActual = id ?: UUID.randomUUID().toString();
            val announcement = SessionAnnouncement(idActual, title, msg, announceType, time, category, actionButton, idActual, cancelButton, if(cancelAction != null) idActual + "_cancel" else null);

            _sessionActions.put(idActual, action);
            if(cancelAction != null) {
                _sessionActions.put(idActual + "_cancel", cancelAction);
            }
            registerAnnouncementSession(announcement);
        }
    }
    fun registerAnnouncement(id: String?, title: String, msg: String, announceType: AnnouncementType = AnnouncementType.DELETABLE, time: OffsetDateTime? = null, category: String? = null, actionButton: String? = null, actionId: String? = null) {
        val newAnnouncement = Announcement(id ?: UUID.randomUUID().toString(), title, msg, announceType, time, category, actionButton, actionId);

        if(announceType == AnnouncementType.SESSION || announceType == AnnouncementType.SESSION_RECURRING) {
            registerAnnouncementSession(newAnnouncement);
        } else {
            registerAnnouncement(newAnnouncement);
        }
    }
    fun registerAnnouncementSession(announcement: Announcement) {
        synchronized(_lock) {
            _sessionAnnouncements.put(announcement.id, announcement);
        }

        onAnnouncementChanged.emit();
    }
    fun registerAnnouncement(announcement: Announcement) {
        synchronized(_lock) {
            if(_sessionAnnouncements.containsKey(announcement.id))
                return@synchronized;

            if (!_announcementsStore.hasItem { it.id == announcement.id }) {
                _announcementsStore.saveAsync(announcement);
            }
        }

        onAnnouncementChanged.emit();
    }

    //Special Announcements
    fun registerPluginUpdate(oldConfig: SourcePluginConfig, newConfig: SourcePluginConfig): SessionAnnouncement {
        val announcement = SessionAnnouncement(
            "update-plugin-" + UUID.randomUUID().toString(),
            "${newConfig.name} update v${newConfig.version} available!",
            "An update is available to upgrade from ${oldConfig.version} to ${newConfig.version}.",
            AnnouncementType.SESSION,
            null, "updates", "Update", StateAnnouncement.ACTION_UPDATE_PLUGIN,
            null, null,oldConfig.id,
            newConfig?.absoluteIconUrl?.let { ImageVariable.fromUrl(it) }
        ).withExtraAction("Changelog", StateAnnouncement.ACTION_CHANGELOG, oldConfig.id);
        registerAnnouncementSession(announcement);
        return announcement;
    }
    fun registerPluginUpdated(newConfig: SourcePluginConfig) {
        registerAnnouncementSession(SessionAnnouncement(
            "updated-plugin-" + UUID.randomUUID().toString(),
            "${newConfig.name} updated to v${newConfig.version}!",
            "You have succesfully been updater to v${newConfig.version}.",
            AnnouncementType.SESSION,
            null, "updates", null, null,
            null, null,null,
            newConfig?.absoluteIconUrl?.let { ImageVariable.fromUrl(it) }
        ).withExtraAction("Changelog", StateAnnouncement.ACTION_CHANGELOG, newConfig.id));
    }

    fun registerLoading(title: String, description: String, icon: ImageVariable? = null, customId: String? = null): SessionAnnouncement {
        val id = "loading-" + UUID.randomUUID().toString();
        val announcement = SessionAnnouncement(
            customId ?: id,
            title,
            description,
            AnnouncementType.ONGOING,
            null, "loading", null, null,
            null, null,null, icon
        );
        registerAnnouncementSession(announcement);
        return announcement;
    }



    fun getVisibleAnnouncements(category: String? = null): List<Announcement> {
        synchronized(_lock) {
            if (category != null) {
                return _announcementsStore.getItems().filter { it.category == category && !_announcementsNever.contains(it.id) && !_announcementsClosed.contains(it.id) } +
                        _sessionAnnouncements.values.filter { it.category == category && !_sessionAnnouncementsNever.contains(it.id) && !_announcementsClosed.contains(it.id) }
            } else {
                return _announcementsStore.getItems().filter { !_announcementsNever.contains(it.id) && !_announcementsClosed.contains(it.id) } +
                        _sessionAnnouncements.values.filter { !_sessionAnnouncementsNever.contains(it.id) && !_announcementsClosed.contains(it.id) }
            }
        }
    }

    fun closeAnnouncement(id: String?) {
        if(id == null)
            return;
        val item: Announcement?;
        synchronized(_lock) {
            item = _announcementsStore.findItem { it.id == id };

            if (item != null) {
                when (item.announceType) {
                    AnnouncementType.DELETABLE -> {
                        neverAnnouncement(item.id);
                    }
                    AnnouncementType.SESSION -> {
                        deleteAnnouncement(item.id);
                    }
                    else -> {
                        _announcementsClosed.add(item.id);
                    }
                }
            }
            val itemSession = _sessionAnnouncements.get(id);
            if(itemSession != null) {
                when (itemSession.announceType) {
                    AnnouncementType.DELETABLE -> {
                        neverAnnouncement(itemSession.id);
                    }
                    AnnouncementType.SESSION -> {
                        deleteAnnouncement(itemSession.id);

                        if(itemSession is SessionAnnouncement)
                            cancelActionAnnouncement(itemSession);
                    }
                    else -> {
                        _announcementsClosed.add(itemSession.id);
                    }
                }
            }
        }
        if(item is SessionAnnouncement) {
            if(item.cancelActionId != null) {
                val cancelAction = _sessionActions[item.cancelActionId];
                cancelAction?.invoke(item);
            }
        }
        onAnnouncementChanged?.emit();
    }

    fun deleteAllAnnouncements() {
        synchronized(_lock) {
            val items = _announcementsStore.getItems().toList();
            for (item in items) {
                _announcementsStore.delete(item);
            }

            val sessionItems = _sessionAnnouncements.toList();
            for (item in sessionItems) {
                _sessionAnnouncements.remove(item.first);
            }
        }

        onAnnouncementChanged.emit();
    }

    fun deleteAnnouncement(id: String) {
        synchronized(_lock) {
            val item = _announcementsStore.findItem { it.id == id };
            if (item != null)
                _announcementsStore.delete(item);
            val itemSession = _sessionAnnouncements.get(id);
            if(itemSession != null)
                _sessionAnnouncements.remove(id);
        }

        onAnnouncementChanged.emit();
    }
    fun neverAnnouncement(id: String?) {
        if(id == null)
            return;
        synchronized(_lock) {
            val item = _announcementsStore.findItem { it.id == id };
            if (item != null && !_announcementsNever.contains(id))
                _announcementsNever.add(id);
            val itemSession = _sessionAnnouncements.get(id);
            if(itemSession != null && !_sessionAnnouncementsNever.contains(id))
                _sessionAnnouncementsNever.add(id);
        }

        _sessionAnnouncementsNever.save();
        _announcementsNever.save();
        onAnnouncementChanged.emit();
    }
    fun actionAnnouncement(id: String?, extra: Boolean = false) {
        if(id == null)
            return;
        val item = _announcementsStore.findItem { it.id == id } ?: _sessionAnnouncements[id];
        if(item != null)
            actionAnnouncement(item, extra);
    }
    fun actionAnnouncement(item: Announcement, extra: Boolean = false) {
        val actionId = if(!extra) item.actionId else if(item is SessionAnnouncement) item.extraActionId else null;
        val actionData = if(!extra) item.actionData else if(item is SessionAnnouncement) item.extraActionData else null;

        val action = _sessionActions[item.id];
        if (action != null) {
            action(item);
        } else {
            when (actionId) {
                ACTION_NEVER -> neverAnnouncement(item.id);
                ACTION_SOMETHING -> actionSomething();
                ACTION_CHANGELOG -> actionChangelog(actionData);
                ACTION_UPDATE_PLUGIN -> actionUpdatePlugin(item.id, actionData);
            }
        }
    }
    fun cancelActionAnnouncement(id: String) {
        val item = _announcementsStore.findItem { it.id == id } ?: _sessionAnnouncements[id];
        if(item != null)
            cancelActionAnnouncement(item);
    }
    fun cancelActionAnnouncement(item: Announcement) {
        if(item is SessionAnnouncement && item.cancelActionId != null) {
            val action = _sessionActions[item.cancelActionId];
            action?.invoke(item);
        }
    }

    fun resetAnnouncements() {
        _announcementsClosed.clear();
        _announcementsNever.values.clear();
        _announcementsNever.save();
        _sessionAnnouncementsNever.values.clear();
        _sessionAnnouncementsNever.save();
        _sessionAnnouncements.clear();
        onAnnouncementChanged.emit();
    }

    //TODO Actions
    private fun actionSomething() {

    }

    private fun actionChangelog(id: String?) {
        if(id == null)
            return;

        StateApp.instance.contextOrNull?.let { context ->
            StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
                val plugin = StatePlugins.instance.getPlugin(id);
                if (plugin == null)
                    return@launch
                val update = StatePlugins.instance.checkForUpdates(plugin.config);
                if(update == null)
                    return@launch;

                StateApp.instance.scopeOrNull?.launch(Dispatchers.Main) {
                    UIDialogs.showChangelogDialog(context, update.version, update.changelog!!.filterKeys { it.toIntOrNull() != null }
                        .mapKeys { it.key.toInt() }
                        .mapValues { update.getChangelogString(it.key.toString()) ?: "" });
                }
            }
        }
    }
    private fun actionUpdatePlugin(notifId: String?, id: String?) {
        if(id == null)
            return;
        val plugin = StatePlugins.instance.getPlugin(id);
        if (plugin == null)
            return

        closeAnnouncement(notifId);
        val loadingAnnouncement = registerLoading("Updating ${plugin.config.name}..", "An update is in progress for ${plugin.config.name}.",
            if(plugin.config.absoluteIconUrl != null) ImageVariable.fromUrl(plugin.config.absoluteIconUrl!!) else null);

        val loadingId = loadingAnnouncement.id;

        StateApp.instance.contextOrNull?.let { context ->

            StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
                try {
                    val update = StatePlugins.instance.checkForUpdates(plugin.config);
                    if (update == null)
                        return@launch;

                    val client = ManagedHttpClient();
                    client.setTimeout(10000);
                    val script = StatePlugins.instance.getScript(plugin.config.id) ?: "";
                    val newScript = client.get(update.absoluteScriptUrl)?.body?.string();
                    if(newScript.isNullOrEmpty())
                        throw IllegalStateException("No script found");

                    if(true || plugin.config.isLowRiskUpdate(script, update, newScript)) {
                        StatePlugins.instance.installPluginBackground(context, StateApp.instance.scope, update, newScript,
                            { text: String, progress: Double -> },
                            { ex ->
                                if(ex == null) {
                                    registerPluginUpdated(update);
                                }
                                else {
                                    UIDialogs.appToast("Update for ${update.name} failed\n" + ex.message);
                                }
                                StateApp.instance.scopeOrNull?.launch(Dispatchers.Main) {
                                    closeAnnouncement(loadingId);
                                }
                            });
                    }
                    else {
                        StateApp.instance.scopeOrNull?.launch(Dispatchers.Main) {
                            closeAnnouncement(loadingId);
                            UIDialogs.showPluginUpdateDialog(context, plugin.config, update);
                        }
                    }
                }
                catch(ex: Throwable) {
                    Logger.e(TAG, "Failed to trigger update from announcement", ex);
                }
            }
        }
    }

    fun registerDefaultHandlerAnnouncement() {
        registerAnnouncement(
            "default-url-handler",
            "Allow Grayjay to open URLs",
            "Click here to allow Grayjay to open URLs",
            AnnouncementType.SESSION_RECURRING,
            null,
            null,
            "Allow"
        ) {
            UIDialogs.showUrlHandlingPrompt(StateApp.instance.context) {
                instance.neverAnnouncement("default-url-handler")
                instance.onAnnouncementChanged.emit()
            }
        }
    }

    companion object {
        private var _instance: StateAnnouncement? = null;
        val instance: StateAnnouncement
            get(){
                if(_instance == null)
                    _instance = StateAnnouncement();
                return _instance!!;
            };


        const val ACTION_SOMETHING = "SOMETHING";
        const val ACTION_CHANGELOG = "CHANGELOG";
        const val ACTION_UPDATE_PLUGIN = "UPDATE_PLUGIN";
        const val ACTION_NEVER = "NEVER";
        private const val TAG = "StateAnnouncement";
    }
}

@Serializable
open class Announcement(
    val id: String,
    val title: String,
    val msg: String,
    val announceType: AnnouncementType,
    @Serializable(with = OffsetDateTimeNullableSerializer::class)
    val time: OffsetDateTime? = null,
    val category: String? = null,
    val actionName: String? = null,
    val actionId: String? = null,
    val actionData: String? = null
);
class SessionAnnouncement(
    id: String,
    title: String,
    msg: String,
    announceType: AnnouncementType,
    time: OffsetDateTime? = null,
    category: String? = null,
    actionName: String? = null,
    actionId: String? = null,
    val cancelName: String? = null,
    val cancelActionId: String? = null,
    actionData: String? = null,
    val icon: ImageVariable? = null
): Announcement(
    id= id,
    title = title,
    msg = msg,
    announceType = announceType,
    time = time,
    category = category,
    actionName = actionName,
    actionId = actionId,
    actionData = actionData
) {
    var extraActionName: String? = null;
    var extraActionId: String? = null;
    var extraActionData: String? = null;

    var extraObj: Any? = null;

    var progress: Double? = null;
    val onProgressChanged = Event1<SessionAnnouncement>();

    fun withExtraAction(name: String, id: String, data: String? = null): SessionAnnouncement {
        extraActionName = name;
        extraActionId = id;
        extraActionData = data;
        return this;
    }

    fun setProgress(progress: Double) {
        this.progress = progress;
        onProgressChanged?.emit(this);
    }
    fun setProgress(progress: Int) {
        this.progress = progress.toDouble().div(100);
        onProgressChanged?.emit(this);
    }
}

enum class AnnouncementType(val value : Int) {
    DELETABLE(0), //Close button deletes announcement (generally for actions)
    RECURRING(1), //Shows up till never is pressed (generally for patchnotes etc)
    PERMANENT(2), //Shows up until deleted through other means (action)
    SESSION(3), //Not persistent, only during this session
    SESSION_RECURRING(4), //Not persistent, only during this session, recurring id
    ONGOING(5);
}