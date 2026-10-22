package com.example.autostart

import android.media.browse.MediaBrowser
import android.os.Bundle
import android.service.media.MediaBrowserService

class MusicBridgeService : MediaBrowserService() {
    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
        return BrowserRoot("root", null)
    }
    override fun onLoadChildren(parentId: String, result: Result<List<MediaBrowser.MediaItem>>) {
        result.sendResult(emptyList())
    }
}
