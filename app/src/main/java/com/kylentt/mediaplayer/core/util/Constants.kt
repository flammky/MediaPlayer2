package com.kylentt.mediaplayer.core.util

object Constants {

    // Providers
    const val PROVIDER_ANDROID = "content://com.android.providers"
    const val PROVIDER_DRIVE_LEGACY = "content://com.google.android.apps.docs.storage.legacy/"
    const val PROVIDER_EXTERNAL_STORAGE = "content://com.android.externalstorage"
    const val PROVIDER_COLOROS_FM = "content://com.coloros.filemanager"

    // Song
    const val ALBUM_ART_PATH = "content://media/external/audio/albumart"

    const val SONG_DATA = "data"
    const val SONG_BYTE = "byteSize"
    const val SONG_FILE_NAME = "fileName"
    const val SONG_FILE_PARENT = "fileParent"
    const val SONG_FILE_PARENT_ID = "fileParentId"
    const val SONG_LAST_MODIFIED = "lastModified"

    // Service
    const val MEDIA_SESSION_ID = "Kylentt"
    const val NOTIFICATION_CHANNEL_ID = "Ky_NOTIFICATION_ID"
    const val NOTIFICATION_ID = 301
    const val NOTIFICATION_NAME = "MediaPlayer Notification"

    const val ACTION = "ACTION"
    const val ACTION_CANCEL = "ACTION_CANCEL"
    const val ACTION_PREV = "ACTION_PREV"
    const val ACTION_PREV_DISABLED = "ACTION_PREV_DISABLED"
    const val ACTION_PLAY = "ACTION_PLAY"
    const val ACTION_PAUSE = "ACTION_PAUSE"
    const val ACTION_NEXT = "ACTION_NEXT"
    const val ACTION_NEXT_DISABLED = "ACTION_NEXT_DISABLED"
    const val ACTION_UNIT = "ACTION_UNIT"
    const val ACTION_FADE ="ACTION_FADE"
    const val ACTION_FADE_PAUSE ="ACTION_FADE_PAUSE"

    const val ACTION_REPEAT_OFF_TO_ONE = "ACTION_REPEAT_OFF_TO_ONE"
    const val ACTION_REPEAT_ONE_TO_ALL = "ACTION_REPEAT_ONE_TO_ALL"
    const val ACTION_REPEAT_ALL_TO_OFF = "ACTION_REPEAT_ALL_TO_OFF"

    const val ACTION_CANCEL_CODE = 400
    const val ACTION_PLAY_CODE = 401
    const val ACTION_PAUSE_CODE = 402
    const val ACTION_NEXT_CODE = 403
    const val ACTION_PREV_CODE = 404
    const val ACTION_REPEAT_OFF_TO_ONE_CODE = 405
    const val ACTION_REPEAT_ONE_TO_ALL_CODE = 406
    const val ACTION_REPEAT_ALL_TO_OFF_CODE = 407

    const val PLAYBACK_INTENT = "com.kylennt.mediaplayer.PLAYBACK_INTENT"

    // UI
    const val HOME_SCREEN = "HOME_SCREEN"
    const val PERMISSION_SCREEN = "PERMISSION_SCREEN"
    const val SPLASH_SCREEN = "SPLASH_SCREEN"

}