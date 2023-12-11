package com.futo.platformplayer.states

import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.serializers.OffsetDateTimeNullableSerializer
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.stores.StringHashSetStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.OffsetDateTime
import java.util.Random
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

    fun closeAnnouncement(id: String) {
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
    fun neverAnnouncement(id: String) {
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
    fun actionAnnouncement(id: String) {
        val item = _announcementsStore.findItem { it.id == id } ?: _sessionAnnouncements[id];
        if(item != null)
            actionAnnouncement(item);
    }
    fun actionAnnouncement(item: Announcement) {
        val action = _sessionActions[item.id];
        if (action != null) {
            action(item);
        } else {
            when (item.actionId) {
                ACTION_NEVER -> neverAnnouncement(item.id);
                ACTION_SOMETHING -> actionSomething();
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

    fun registerDidYouKnow() {
        val random = Random();
        val message: String? = when (random.nextInt(4 * 18 + 1)) {
            0 -> "You can login to different platforms and unify your content experience. Check it out in the source settings!"
            1 -> "Importing your playlists and subscriptions from other platforms to Grayjay is quick and easy. Check it out in the source settings!"
            2 -> "Want to cast to a big screen? Try out FCast (https://fcast.org/)."
            3 -> "Explore Grayjay's gesture controls. When in full-screen swipe on the left to change brightness, swipe on the right to change volume."
            4 -> "Explore Grayjay's gesture controls. Swipe up in the center of a video to toggle full-screen."
            5 -> "Grayjay's multi-platform search lets you find content from various sources."
            6 -> "Grayjay's multi-platform search filters will unify filters across platforms. If your expected filters are not there, try toggling some platforms off in the search filters."
            7 -> "You can share playlists with friends on the playlist page and make full-backups in the settings page."
            8 -> "Discover Grayjay's offline playback feature. Save content for when you're on the go!"
            9 -> "Paid content from your favorite creators gets seamlessly integrated into your Grayjay feed. Login to a platform to seamlessly see content you paid for."
            10 -> "Explore Grayjay's plugin features! Login, import playlists, and tweak plugin settings for a tailored experience."
            11 -> "Directly engage with content by liking, disliking, or leaving comments on the Polycentric network."
            12 -> "With Grayjay's rotation lock, you can watch videos in your preferred orientation regardless of device settings. Check it out during playback!"
            13 -> "Grayjay supports background play. Listen to your favorite content even while multitasking!"
            14 -> "Use Grayjay's quality selection to adjust video resolution. Save data or watch in high definition – it's up to you."
            15 -> "Customize your Grayjay experience by changing playback speed. Watch content at your own pace."
            16 -> "Save time by adding videos to your 'Watch Later' list. Perfect for catching up on content during your free time."
            17 -> "On Grayjay, your playlists, subscriptions, and settings are stored offline for privacy and quick access."
            18 -> "Explore and engage with live content using Grayjay's live stream feature."
            else -> null
        };

        if (message != null) {
            registerAnnouncement(
                "did-you-know?",
                "Did you know?",
                message,
                AnnouncementType.SESSION_RECURRING
            );
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
    val actionId: String? = null
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
    val cancelActionId: String? = null
): Announcement(
    id= id,
    title = title,
    msg = msg,
    announceType = announceType,
    time = time,
    category = category,
    actionName = actionName,
    actionId = actionId
);

enum class AnnouncementType(val value : Int) {
    DELETABLE(0), //Close button deletes announcement (generally for actions)
    RECURRING(1), //Shows up till never is pressed (generally for patchnotes etc)
    PERMANENT(2), //Shows up until deleted through other means (action)
    SESSION(3), //Not persistent, only during this session
    SESSION_RECURRING(4); //Not persistent, only during this session, recurring id
}