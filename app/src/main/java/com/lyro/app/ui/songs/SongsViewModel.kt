package com.lyro.app.ui.songs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lyro.app.LyroApplication
import com.lyro.app.data.download.DownloadStatus
import com.lyro.app.data.download.MusicDownloader
import com.lyro.app.data.model.OnlineTrack
import com.lyro.app.data.model.PlayableTrack
import com.lyro.app.data.model.Playlist
import com.lyro.app.data.model.Song
import com.lyro.app.data.repository.MusicRepository
import com.lyro.app.data.repository.OnlineMusicRepository
import com.lyro.app.data.repository.SortOrder
import com.lyro.app.service.PlaybackManager
import android.util.Log
import com.lyro.app.core.matcher.TrackMetadataNormalizer
import com.lyro.app.data.model.UnifiedTrack
import com.lyro.app.data.model.toUnifiedTrack
import com.lyro.app.recommendation.radio.RadioSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SongsViewModel(
    private val repository: MusicRepository,
    private val onlineRepository: OnlineMusicRepository,
    private val playbackManager: PlaybackManager,
    private val musicDownloader: MusicDownloader = LyroApplication.instance.musicDownloader
) : ViewModel() {

    val downloadStatuses: StateFlow<Map<String, DownloadStatus>> = musicDownloader.downloadStatuses
    val lastCompletedDownload: StateFlow<OnlineTrack?> = musicDownloader.lastCompletedTrack

    fun clearLastCompletedDownload() {
        musicDownloader.clearLastCompletedTrack()
    }

    fun isTrackDownloaded(track: PlayableTrack): Boolean {
        return musicDownloader.isTrackDownloaded(track)
    }

    fun isTrackDownloaded(track: OnlineTrack): Boolean {
        return musicDownloader.isTrackDownloaded(track)
    }

    fun downloadTrack(track: OnlineTrack) {
        viewModelScope.launch {
            musicDownloader.downloadTrack(track)
            try {
                LyroApplication.instance.listeningEventRepository.recordEvent(
                    com.lyro.app.recommendation.model.ListeningEvent(
                        playbackSessionId = "download_action",
                        videoId = track.videoId,
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        eventType = com.lyro.app.recommendation.model.EventType.DOWNLOADED
                    )
                )
            } catch (e: Exception) {
                // Non-blocking
            }
        }
    }

    fun deleteDownload(videoId: String) {
        viewModelScope.launch {
            musicDownloader.deleteDownload(videoId)
        }
    }

    fun deleteDownload(track: PlayableTrack) {
        val videoId = track.onlineVideoId ?: (track as? OnlineTrack)?.videoId
        if (videoId != null) {
            viewModelScope.launch {
                musicDownloader.deleteDownload(videoId)
            }
        }
    }

    // Search query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.TITLE)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _favoritesOnly = MutableStateFlow(false)
    val favoritesOnly: StateFlow<Boolean> = _favoritesOnly.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isOnlineMode = MutableStateFlow(false)
    val isOnlineMode: StateFlow<Boolean> = _isOnlineMode.asStateFlow()

    private val _openNowPlayingEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openNowPlayingEvent = _openNowPlayingEvent.asSharedFlow()

    fun openNowPlaying() {
        _openNowPlayingEvent.tryEmit(Unit)
    }

    fun setOnlineMode(enabled: Boolean) {
        _isOnlineMode.value = enabled
    }

    // Unified Online search states
    private val _onlineSearchResults = MutableStateFlow<List<OnlineTrack>>(emptyList())
    val onlineSearchResults: StateFlow<List<OnlineTrack>> = _onlineSearchResults.asStateFlow()

    private val _isSearchingOnline = MutableStateFlow(false)
    val isSearchingOnline: StateFlow<Boolean> = _isSearchingOnline.asStateFlow()

    private val _onlineSearchError = MutableStateFlow<String?>(null)
    val onlineSearchError: StateFlow<String?> = _onlineSearchError.asStateFlow()

    // Home Online & Unified Feed StateFlows
    private val _homeQuickPicks = MutableStateFlow<List<PlayableTrack>>(emptyList())
    val homeQuickPicks: StateFlow<List<PlayableTrack>> = _homeQuickPicks.asStateFlow()

    private val _homeTrending = MutableStateFlow<List<PlayableTrack>>(emptyList())
    val homeTrending: StateFlow<List<PlayableTrack>> = _homeTrending.asStateFlow()

    private val _homeRecommended = MutableStateFlow<List<PlayableTrack>>(emptyList())
    val homeRecommended: StateFlow<List<PlayableTrack>> = _homeRecommended.asStateFlow()

    private val _homeDiscover = MutableStateFlow<List<PlayableTrack>>(emptyList())
    val homeDiscover: StateFlow<List<PlayableTrack>> = _homeDiscover.asStateFlow()

    private val _isHomeLoading = MutableStateFlow(false)
    val isHomeLoading: StateFlow<Boolean> = _isHomeLoading.asStateFlow()

    // Pull-to-refresh state for Home
    private val _isHomeRefreshing = MutableStateFlow(false)
    val isHomeRefreshing: StateFlow<Boolean> = _isHomeRefreshing.asStateFlow()

    private val _homeRefreshError = MutableStateFlow<String?>(null)
    val homeRefreshError: StateFlow<String?> = _homeRefreshError.asStateFlow()

    fun clearHomeRefreshError() {
        _homeRefreshError.value = null
    }

    // Discovery seed pool for rotating queries on refresh
    private val discoverySeedPool = listOf(
        "Top Hits",
        "Trending Songs",
        "New Music",
        "Popular Songs",
        "Viral Hits",
        "Global Hits",
        "Chill Hits",
        "Party Hits",
        "Indie Hits",
        "Pop Hits"
    )
    private var seedRotationIndex = 0

    // Session history of recently shown online video IDs (capped at 100)
    private val recentlyShownOnlineIds = LinkedHashSet<String>()
    private val maxRecentOnlineHistory = 100

    private fun recordRecentlyShownOnlineIds(videoIds: Collection<String>) {
        for (id in videoIds) {
            recentlyShownOnlineIds.add(id)
        }
        while (recentlyShownOnlineIds.size > maxRecentOnlineHistory) {
            val oldest = recentlyShownOnlineIds.first()
            recentlyShownOnlineIds.remove(oldest)
        }
    }

    // Mood & Activity Chips for Home
    val moodChips = listOf("Relax", "Energize", "Feel good", "Party", "Workout", "Focus")
    private val _selectedMood = MutableStateFlow<String?>(null)
    val selectedMood: StateFlow<String?> = _selectedMood.asStateFlow()

    private val _moodTracks = MutableStateFlow<List<OnlineTrack>>(emptyList())
    val moodTracks: StateFlow<List<OnlineTrack>> = _moodTracks.asStateFlow()
    private val _isMoodLoading = MutableStateFlow(false)
    val isMoodLoading: StateFlow<Boolean> = _isMoodLoading.asStateFlow()

    // Explore screen discovery state
    private val _exploreTrending = MutableStateFlow<List<OnlineTrack>>(emptyList())
    val exploreTrending: StateFlow<List<OnlineTrack>> = _exploreTrending.asStateFlow()
    private val _isExploreLoading = MutableStateFlow(false)
    val isExploreLoading: StateFlow<Boolean> = _isExploreLoading.asStateFlow()

    // Pull-to-refresh state for Explore
    private val _isExploreRefreshing = MutableStateFlow(false)
    val isExploreRefreshing: StateFlow<Boolean> = _isExploreRefreshing.asStateFlow()

    private val _exploreRefreshError = MutableStateFlow<String?>(null)
    val exploreRefreshError: StateFlow<String?> = _exploreRefreshError.asStateFlow()

    fun clearExploreRefreshError() {
        _exploreRefreshError.value = null
    }

    private val exploreSeedPool = listOf(
        "Global Top Hits",
        "Viral Hits 2024",
        "New Music Trending",
        "Top Charts Songs",
        "Billboard Hot Hits",
        "International Pop Hits",
        "Dance & Electronic Hits",
        "R&B Hip Hop Hits",
        "Indie Alternative Hits",
        "Acoustic Hits"
    )
    private var exploreSeedRotationIndex = 0

    private var searchJob: Job? = null
    private var moodJob: Job? = null

    val playlists: StateFlow<List<Playlist>> = repository.playlists

    // Filtered local song list
    val songs: StateFlow<List<Song>> = combine(
        repository.allSongs,
        _searchQuery,
        _sortOrder,
        _favoritesOnly
    ) { all, query, sort, favOnly ->
        val filtered = all.filter { song ->
            val matchesQuery = query.isBlank() ||
                    song.title.contains(query, ignoreCase = true) ||
                    song.artist.contains(query, ignoreCase = true) ||
                    song.album.contains(query, ignoreCase = true)
            val matchesFav = !favOnly || song.isFavorite
            matchesQuery && matchesFav
        }

        when (sort) {
            SortOrder.TITLE -> filtered.sortedBy { it.title.lowercase() }
            SortOrder.ARTIST -> filtered.sortedBy { it.artist.lowercase() }
            SortOrder.DATE_ADDED -> filtered.sortedByDescending { it.dateAdded }
            SortOrder.DURATION -> filtered.sortedByDescending { it.duration }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Quick Picks: Curated tracks for Home (favorites first, then recent additions, up to 16)
    val quickPicks: StateFlow<List<Song>> = repository.allSongs.map { all ->
        if (all.isEmpty()) emptyList()
        else {
            val favs = all.filter { it.isFavorite }
            val recents = all.sortedByDescending { it.dateAdded }
            (favs + recents).distinctBy { it.id }.take(16)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Liked Songs
    val likedSongs: StateFlow<List<Song>> = repository.allSongs.map { all ->
        all.filter { it.isFavorite }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Recently added songs rail
    val recentlyAdded: StateFlow<List<Song>> = repository.allSongs.map { all ->
        all.sortedByDescending { it.dateAdded }.take(12)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentSong: StateFlow<Song?> = playbackManager.currentSong
    val currentTrack: StateFlow<PlayableTrack?> = playbackManager.currentTrack
    val isPlaying: StateFlow<Boolean> = playbackManager.isPlaying
    val isResolvingStream: StateFlow<Boolean> = playbackManager.isResolvingStream
    val playbackError: StateFlow<String?> = playbackManager.playbackError

    init {
        loadSongs()
        loadHomeFeeds()
        loadExploreTrending()

        // Refresh Quick Picks whenever local library or favorites change
        viewModelScope.launch {
            repository.allSongs.collect {
                val currentOnline = _homeRecommended.value.ifEmpty { _homeTrending.value }
                rebuildQuickPicks(currentOnline)
            }
        }
    }

    fun loadHomeFeeds() {
        viewModelScope.launch {
            val dbHelper = LyroApplication.instance.databaseHelper
            val localMediaIndex = LyroApplication.instance.localMediaIndex
            val recEngine = LyroApplication.instance.recommendationEngine

            // 1. Instantly load cached online feeds from SQLite so Home is never blank on launch
            val cachedTrending = withContext(Dispatchers.IO) { dbHelper.getHomeFeedCache("trending") }
            val cachedRecommended = withContext(Dispatchers.IO) { dbHelper.getHomeFeedCache("recommended") }
            val cachedDiscover = withContext(Dispatchers.IO) { dbHelper.getHomeFeedCache("discover") }

            if (cachedTrending.isNotEmpty() || cachedRecommended.isNotEmpty() || cachedDiscover.isNotEmpty()) {
                val resolvedTrending = cachedTrending.map { track ->
                    val localMatch = localMediaIndex.findLocalMatch(track)
                    track.toUnifiedTrack(localSong = localMatch, matchedUri = localMatch?.contentUri)
                }
                val resolvedRecommended = cachedRecommended.map { track ->
                    val localMatch = localMediaIndex.findLocalMatch(track)
                    track.toUnifiedTrack(localSong = localMatch, matchedUri = localMatch?.contentUri)
                }
                val resolvedDiscover = cachedDiscover.map { track ->
                    val localMatch = localMediaIndex.findLocalMatch(track)
                    track.toUnifiedTrack(localSong = localMatch, matchedUri = localMatch?.contentUri)
                }
                if (_homeTrending.value.isEmpty()) _homeTrending.value = resolvedTrending
                if (_homeRecommended.value.isEmpty()) _homeRecommended.value = resolvedRecommended
                if (_homeDiscover.value.isEmpty()) _homeDiscover.value = resolvedDiscover
                rebuildQuickPicks(resolvedRecommended.ifEmpty { resolvedTrending })
            }

            // 2. Fetch fresh personalized recommendations from RecommendationEngine
            if (_homeRecommended.value.isEmpty()) {
                _isHomeLoading.value = true
            }

            try {
                val allDownloadedMeta = withContext(Dispatchers.IO) { dbHelper.getAllDownloadedMetadata() }
                val downloadedTracks = allDownloadedMeta.map { meta ->
                    OnlineTrack(
                        videoId = meta.videoId,
                        title = meta.title,
                        artist = meta.artist,
                        album = meta.album,
                        thumbnailUrl = meta.thumbnailUri,
                        durationMs = meta.durationMs
                    )
                }

                recEngine.recomputeTasteProfile(
                    localSongs = repository.allSongs.value,
                    downloadedTracks = downloadedTracks
                )

                val quickPicksJob = async(Dispatchers.IO) { recEngine.getPersonalizedQuickPicks(16) }
                val recommendedJob = async(Dispatchers.IO) { recEngine.getPersonalizedRecommendations(16) }
                val discoverJob = async(Dispatchers.IO) { recEngine.getPersonalizedDiscover(16) }

                val freshQuickPicks = quickPicksJob.await()
                val freshRecommended = recommendedJob.await()
                val freshDiscover = discoverJob.await()

                if (freshRecommended.isNotEmpty()) {
                    val rawOnline = freshRecommended.mapNotNull { it as? OnlineTrack }
                    val processedRecommended = processAndBalanceOnlineTracks(rawOnline, targetCount = 14)
                    _homeRecommended.value = processedRecommended
                    withContext(Dispatchers.IO) {
                        val toCache = rawOnline.filter { raw ->
                            processedRecommended.any { it.onlineVideoId == raw.videoId }
                        }
                        dbHelper.saveHomeFeedCache("recommended", toCache)
                    }
                }

                if (freshDiscover.isNotEmpty()) {
                    val rawOnline = freshDiscover.mapNotNull { it as? OnlineTrack }
                    val processedDiscover = processAndBalanceOnlineTracks(rawOnline, targetCount = 14)
                    _homeDiscover.value = processedDiscover
                    withContext(Dispatchers.IO) {
                        val toCache = rawOnline.filter { raw ->
                            processedDiscover.any { it.onlineVideoId == raw.videoId }
                        }
                        dbHelper.saveHomeFeedCache("discover", toCache)
                    }
                }

                if (freshQuickPicks.isNotEmpty()) {
                    val rawOnline = freshQuickPicks.mapNotNull { it as? OnlineTrack }
                    val processedQuick = processAndBalanceOnlineTracks(rawOnline, targetCount = 14)
                    _homeTrending.value = processedQuick
                    withContext(Dispatchers.IO) {
                        val toCache = rawOnline.filter { raw ->
                            processedQuick.any { it.onlineVideoId == raw.videoId }
                        }
                        dbHelper.saveHomeFeedCache("trending", toCache)
                    }
                }

                val currentOnline = _homeRecommended.value.ifEmpty { _homeTrending.value }
                rebuildQuickPicks(currentOnline)
            } catch (e: Exception) {
                Log.e("SongsViewModel", "Error fetching personalized home feeds: ${e.message}", e)
            } finally {
                _isHomeLoading.value = false
            }
        }
    }

    /**
     * Premium pull-to-refresh for Home.
     * Uses RecommendationEngine to generate fresh candidate tracks based on the user's
     * evolved taste profile, avoiding recently shown IDs and retaining existing content gracefully on error.
     */
    fun refreshHome() {
        if (_isHomeRefreshing.value) {
            Log.d("SongsViewModel", "refreshHome ignored: already refreshing")
            return
        }

        viewModelScope.launch {
            _isHomeRefreshing.value = true
            _homeRefreshError.value = null

            val dbHelper = LyroApplication.instance.databaseHelper
            val recEngine = LyroApplication.instance.recommendationEngine

            try {
                // 1. Fetch fresh personalized recommendations from recommendation brain
                val quickPicksJob = async(Dispatchers.IO) { recEngine.getPersonalizedQuickPicks(16) }
                val recommendedJob = async(Dispatchers.IO) { recEngine.getPersonalizedRecommendations(16) }
                val discoverJob = async(Dispatchers.IO) { recEngine.getPersonalizedDiscover(16) }

                val freshQuickPicks = quickPicksJob.await()
                val freshRecommended = recommendedJob.await()
                val freshDiscover = discoverJob.await()

                if (freshQuickPicks.isEmpty() && freshRecommended.isEmpty() && freshDiscover.isEmpty()) {
                    Log.w("SongsViewModel", "Pull-to-refresh returned no songs (offline or network error)")
                    _homeRefreshError.value = "Couldn't reach online music. Retaining current feed."
                    return@launch
                }

                if (freshRecommended.isNotEmpty()) {
                    val rawOnline = freshRecommended.mapNotNull { it as? OnlineTrack }
                    val processedRecommended = processAndBalanceOnlineTracks(rawOnline, targetCount = 14)
                    _homeRecommended.value = processedRecommended
                    withContext(Dispatchers.IO) {
                        val toCache = rawOnline.filter { raw ->
                            processedRecommended.any { it.onlineVideoId == raw.videoId }
                        }
                        dbHelper.saveHomeFeedCache("recommended", toCache)
                    }
                }

                if (freshDiscover.isNotEmpty()) {
                    val rawOnline = freshDiscover.mapNotNull { it as? OnlineTrack }
                    val processedDiscover = processAndBalanceOnlineTracks(rawOnline, targetCount = 14)
                    _homeDiscover.value = processedDiscover
                    withContext(Dispatchers.IO) {
                        val toCache = rawOnline.filter { raw ->
                            processedDiscover.any { it.onlineVideoId == raw.videoId }
                        }
                        dbHelper.saveHomeFeedCache("discover", toCache)
                    }
                }

                if (freshQuickPicks.isNotEmpty()) {
                    val rawOnline = freshQuickPicks.mapNotNull { it as? OnlineTrack }
                    val processedQuick = processAndBalanceOnlineTracks(rawOnline, targetCount = 14)
                    _homeTrending.value = processedQuick
                    withContext(Dispatchers.IO) {
                        val toCache = rawOnline.filter { raw ->
                            processedQuick.any { it.onlineVideoId == raw.videoId }
                        }
                        dbHelper.saveHomeFeedCache("trending", toCache)
                    }
                }

                // Rebuild Quick Picks replacing the online recommendation portion with fresh online tracks
                val freshOnlineForQuickPicks = _homeRecommended.value.ifEmpty { _homeTrending.value }
                rebuildQuickPicks(freshOnlineForQuickPicks)

                // If a mood chip is active, refresh mood tracks as well
                _selectedMood.value?.let { currentMood ->
                    loadMoodTracks(currentMood)
                }

                Log.d("SongsViewModel", "Pull-to-refresh completed successfully with personalized tracks")
            } catch (e: Exception) {
                Log.e("SongsViewModel", "Pull-to-refresh failed: ${e.message}", e)
                _homeRefreshError.value = "Couldn't refresh feed"
            } finally {
                _isHomeRefreshing.value = false
            }
        }
    }

    /**
     * Deduplicates raw online tracks, prioritizes unseen tracks over session history,
     * checks downloaded status, and guarantees a healthy balance of ~60-80% online-only tracks.
     */
    private fun processAndBalanceOnlineTracks(
        rawTracks: List<OnlineTrack>,
        targetCount: Int = 14
    ): List<PlayableTrack> {
        val localMediaIndex = LyroApplication.instance.localMediaIndex

        // 1. Deduplicate by videoId
        val distinctTracks = rawTracks.distinctBy { it.videoId }

        // 2. Partition into unseen vs recently seen to avoid repeating same results
        val unseenTracks = distinctTracks.filter { !recentlyShownOnlineIds.contains(it.videoId) }
        val seenTracks = distinctTracks.filter { recentlyShownOnlineIds.contains(it.videoId) }
        val prioritizedCandidates = unseenTracks + seenTracks

        // 3. Resolve local downloaded state via LocalMediaIndex
        val resolvedTracks = prioritizedCandidates.map { track ->
            val localMatch = localMediaIndex.findLocalMatch(track)
            track.toUnifiedTrack(localSong = localMatch, matchedUri = localMatch?.contentUri)
        }

        // 4. Check downloaded status
        val onlineOnlyTracks = resolvedTracks.filter { !it.isDownloaded }
        val downloadedTracks = resolvedTracks.filter { it.isDownloaded }

        // 5. Prefer a healthy number of NOT-DOWNLOADED tracks (~60-80% online-only)
        val desiredOnlineCount = (targetCount * 0.75).toInt().coerceAtMost(onlineOnlyTracks.size)
        val desiredDownloadedCount = (targetCount - desiredOnlineCount).coerceAtMost(downloadedTracks.size)

        val selected = mutableListOf<PlayableTrack>()
        val selectedOnline = onlineOnlyTracks.take(desiredOnlineCount)
        val selectedDownloaded = downloadedTracks.take(desiredDownloadedCount)

        // Interleave smoothly: 2 online tracks, then 1 downloaded track
        var oIdx = 0
        var dIdx = 0
        while (oIdx < selectedOnline.size || dIdx < selectedDownloaded.size) {
            repeat(2) {
                if (oIdx < selectedOnline.size) selected.add(selectedOnline[oIdx++])
            }
            if (dIdx < selectedDownloaded.size) {
                selected.add(selectedDownloaded[dIdx++])
            }
        }

        // If targetCount is not yet reached, fill from remainder without duplicates
        if (selected.size < targetCount) {
            val remaining = (onlineOnlyTracks.drop(desiredOnlineCount) + downloadedTracks.drop(desiredDownloadedCount))
            for (t in remaining) {
                if (selected.size >= targetCount) break
                if (!selected.any { it.id == t.id }) {
                    selected.add(t)
                }
            }
        }

        // Record chosen video IDs in session history
        val chosenVideoIds = selected.mapNotNull { it.onlineVideoId }
        recordRecentlyShownOnlineIds(chosenVideoIds)

        return selected
    }

    private fun rebuildQuickPicks(onlineTracks: List<PlayableTrack>) {
        val allLocal = repository.allSongs.value
        val favs = allLocal.filter { it.isFavorite }.map { it.toUnifiedTrack() }
        val recentIds = try {
            LyroApplication.instance.databaseHelper.getRecentSongIds(20).toSet()
        } catch (e: Exception) {
            emptySet()
        }
        val recents = allLocal.filter { recentIds.contains(it.id) }.map { it.toUnifiedTrack() }

        // Local candidates (favorites, recents, general local)
        val localCandidates = (favs + recents + allLocal.map { it.toUnifiedTrack() }).distinctBy { it.id }

        // Interleave fresh online recommendations (~70%) with useful local/recent songs (~30%)
        val combined = mutableListOf<PlayableTrack>()
        val onlineSubset = onlineTracks.take(12)
        val localSubset = localCandidates.take(6)

        var oIdx = 0
        var lIdx = 0
        while (oIdx < onlineSubset.size || lIdx < localSubset.size) {
            repeat(2) {
                if (oIdx < onlineSubset.size) combined.add(onlineSubset[oIdx++])
            }
            if (lIdx < localSubset.size) combined.add(localSubset[lIdx++])
        }

        // If still empty (e.g. no network & no local yet), fall back to whatever is available
        if (combined.isEmpty()) {
            combined.addAll(onlineTracks)
            combined.addAll(localCandidates)
        }

        // Deduplicate: ensure no duplicate cards (e.g. Song X local and Song X online)
        val seenVideoIds = mutableSetOf<String>()
        val seenMetaKeys = mutableSetOf<String>()
        val deduplicated = mutableListOf<PlayableTrack>()

        for (track in combined) {
            val vid = track.onlineVideoId
            val normTitle = TrackMetadataNormalizer.normalizeTitle(track.title)
            val normArtist = TrackMetadataNormalizer.normalizeArtist(track.artist)
            val metaKey = "${normTitle}_${normArtist}"

            val isDuplicate = (vid != null && seenVideoIds.contains(vid)) || seenMetaKeys.contains(metaKey)
            if (!isDuplicate) {
                if (vid != null) seenVideoIds.add(vid)
                seenMetaKeys.add(metaKey)
                deduplicated.add(track)
            }
        }

        // Target: 16 tracks (or nearest multiple of 4 between 12 and 20)
        val finalCount = if (deduplicated.size >= 16) 16 else if (deduplicated.size >= 12) 12 else deduplicated.size
        _homeQuickPicks.value = deduplicated.take(finalCount)
    }

    fun loadSongs() {
        viewModelScope.launch {
            _isLoading.value = true
            val loaded = repository.loadSongs()
            playbackManager.validateCurrentTrack(loaded)
            delay(300)
            _isLoading.value = false
        }
    }

    /**
     * Unified search handler: updates local query and debounces online search simultaneously.
     */
    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        triggerDebouncedOnlineSearch(query)
    }

    private fun triggerDebouncedOnlineSearch(query: String) {
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _onlineSearchResults.value = emptyList()
            _isSearchingOnline.value = false
            _onlineSearchError.value = null
            return
        }

        searchJob = viewModelScope.launch {
            delay(400) // 400ms debounce
            _isSearchingOnline.value = true
            _onlineSearchError.value = null

            val result = onlineRepository.searchSongs(trimmed)
            _isSearchingOnline.value = false
            result.onSuccess { tracks ->
                _onlineSearchResults.value = tracks
                _onlineSearchError.value = if (tracks.isEmpty()) "No online tracks found for \"$trimmed\"" else null
            }.onFailure { error ->
                _onlineSearchError.value = "Search failed: ${error.message}"
            }
        }
    }

    /**
     * Immediately searches online without debounce delay (used on pull-to-refresh).
     */
    fun searchOnline(query: String) {
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return

        searchJob = viewModelScope.launch {
            _isSearchingOnline.value = true
            _onlineSearchError.value = null

            val result = onlineRepository.searchSongs(trimmed)
            _isSearchingOnline.value = false
            result.onSuccess { tracks ->
                _onlineSearchResults.value = tracks
                _onlineSearchError.value = if (tracks.isEmpty()) "No online tracks found for \"$trimmed\"" else null
            }.onFailure { error ->
                _onlineSearchError.value = "Search failed: ${error.message}"
            }
        }
    }

    /**
     * Select or toggle mood filter on Home
     */
    fun selectMood(mood: String) {
        if (_selectedMood.value == mood) {
            _selectedMood.value = null
            _moodTracks.value = emptyList()
        } else {
            _selectedMood.value = mood
            loadMoodTracks(mood)
        }
    }

    private fun loadMoodTracks(mood: String) {
        moodJob?.cancel()
        moodJob = viewModelScope.launch {
            _isMoodLoading.value = true
            val result = onlineRepository.searchSongs("$mood Songs Hits")
            result.onSuccess { tracks ->
                _moodTracks.value = tracks
            }
            _isMoodLoading.value = false
        }
    }

    /**
     * Fetch trending online music for Explore screen
     */
    fun loadExploreTrending() {
        if (_exploreTrending.value.isNotEmpty()) return
        viewModelScope.launch {
            _isExploreLoading.value = true
            val seed = exploreSeedPool[exploreSeedRotationIndex % exploreSeedPool.size]
            exploreSeedRotationIndex = (exploreSeedRotationIndex + 1) % exploreSeedPool.size
            val result = onlineRepository.searchSongs(seed)
            result.onSuccess { tracks ->
                val distinct = tracks.distinctBy { it.videoId }
                recordRecentlyShownOnlineIds(distinct.map { it.videoId })
                _exploreTrending.value = distinct
            }
            _isExploreLoading.value = false
        }
    }

    /**
     * Premium pull-to-refresh for Explore screen.
     * Rotates discovery query seeds, fetches fresh tracks, avoids recently shown history,
     * updates Trending and Popular tracks, and retains existing content gracefully on error or offline.
     */
    fun refreshExplore() {
        if (_isExploreRefreshing.value) {
            Log.d("SongsViewModel", "refreshExplore ignored: already refreshing")
            return
        }

        viewModelScope.launch {
            _isExploreRefreshing.value = true
            _exploreRefreshError.value = null

            try {
                // 1. Rotate seeds from exploreSeedPool
                val seed1 = exploreSeedPool[exploreSeedRotationIndex % exploreSeedPool.size]
                val seed2 = exploreSeedPool[(exploreSeedRotationIndex + 1) % exploreSeedPool.size]
                exploreSeedRotationIndex = (exploreSeedRotationIndex + 2) % exploreSeedPool.size
                Log.d("SongsViewModel", "Pull-to-refresh Explore seeds: '$seed1' and '$seed2'")

                // 2. Fetch fresh online tracks concurrently
                val job1 = async(Dispatchers.IO) { onlineRepository.searchSongs(seed1) }
                val job2 = async(Dispatchers.IO) { onlineRepository.searchSongs(seed2) }

                val res1 = job1.await().getOrNull().orEmpty()
                val res2 = job2.await().getOrNull().orEmpty()
                val combined = (res1 + res2).distinctBy { it.videoId }

                if (combined.isEmpty()) {
                    Log.w("SongsViewModel", "Pull-to-refresh Explore returned no songs (offline or error)")
                    _exploreRefreshError.value = "Couldn't reach online music. Retaining current feed."
                    return@launch
                }

                // 3. Prioritize unseen tracks over recent session history
                val unseen = combined.filter { !recentlyShownOnlineIds.contains(it.videoId) }
                val seen = combined.filter { recentlyShownOnlineIds.contains(it.videoId) }
                val prioritized = (unseen + seen).take(25)

                // 4. Update session history
                recordRecentlyShownOnlineIds(prioritized.map { it.videoId })

                // 5. Replace Explore trending/popular list with fresh set
                _exploreTrending.value = prioritized
                Log.d("SongsViewModel", "Pull-to-refresh Explore finished with ${prioritized.size} tracks")
            } catch (e: Exception) {
                Log.e("SongsViewModel", "Pull-to-refresh Explore failed: ${e.message}", e)
                _exploreRefreshError.value = "Couldn't refresh Explore"
            } finally {
                _isExploreRefreshing.value = false
            }
        }
    }

    fun onSortOrderChanged(order: SortOrder) {
        _sortOrder.value = order
    }

    fun setFavoritesFilter(enabled: Boolean) {
        _favoritesOnly.value = enabled
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            val wasFav = song.isFavorite
            repository.toggleFavorite(song)
            try {
                val eventType = if (!wasFav) com.lyro.app.recommendation.model.EventType.LIKED else com.lyro.app.recommendation.model.EventType.UNLIKED
                LyroApplication.instance.listeningEventRepository.recordEvent(
                    com.lyro.app.recommendation.model.ListeningEvent(
                        playbackSessionId = "favorite_toggle",
                        videoId = "local_${song.id}",
                        title = song.title,
                        artist = song.artist,
                        album = song.album,
                        eventType = eventType
                    )
                )
            } catch (e: Exception) {
                // Non-blocking
            }
        }
    }

    fun markNotInterested(track: PlayableTrack) {
        LyroApplication.instance.recommendationEngine.markNotInterested(track)
        val vid = track.onlineVideoId ?: track.id
        _homeQuickPicks.value = _homeQuickPicks.value.filter { it.id != track.id && it.onlineVideoId != vid }
        _homeRecommended.value = _homeRecommended.value.filter { it.id != track.id && it.onlineVideoId != vid }
        _homeTrending.value = _homeTrending.value.filter { it.id != track.id && it.onlineVideoId != vid }
        _homeDiscover.value = _homeDiscover.value.filter { it.id != track.id && it.onlineVideoId != vid }
    }

    // Lyro Radio StateFlows
    val isRadioActive: StateFlow<Boolean> = LyroApplication.instance.radioManager.isRadioActive
    val currentRadioSession: StateFlow<RadioSession?> = LyroApplication.instance.radioManager.currentSession

    fun startSongRadio(track: PlayableTrack) {
        LyroApplication.instance.radioManager.startSongRadio(track)
        _openNowPlayingEvent.tryEmit(Unit)
    }

    fun stopRadio() {
        LyroApplication.instance.radioManager.stopRadio()
    }

    fun playNext(track: PlayableTrack) {
        playbackManager.playNextTrack(track)
    }

    fun addToQueue(track: PlayableTrack) {
        playbackManager.addTrackToQueue(track)
    }

    fun playTrack(track: PlayableTrack, queue: List<PlayableTrack>? = null) {
        playbackManager.playTrack(track, queue)
        _openNowPlayingEvent.tryEmit(Unit)
    }

    fun playSong(song: Song, queue: List<Song> = songs.value) {
        playbackManager.playSong(song, queue)
        _openNowPlayingEvent.tryEmit(Unit)
    }

    fun playQuickPicks(startTrack: PlayableTrack? = null) {
        val list = homeQuickPicks.value
        if (list.isNotEmpty()) {
            val target = startTrack ?: list.first()
            playbackManager.playTrack(target, list)
            _openNowPlayingEvent.tryEmit(Unit)
        }
    }

    fun playQuickPicks(startSong: Song?) {
        val list = homeQuickPicks.value
        if (list.isNotEmpty()) {
            val target = if (startSong != null) {
                list.find { it.id == "local_${startSong.id}" || it.title.equals(startSong.title, ignoreCase = true) } ?: list.first()
            } else list.first()
            playbackManager.playTrack(target, list)
            _openNowPlayingEvent.tryEmit(Unit)
        }
    }

    fun playOnlineTrack(track: OnlineTrack, customList: List<OnlineTrack>? = null) {
        val contextList = customList ?: _onlineSearchResults.value.ifEmpty { _exploreTrending.value }
        if (customList == null && _onlineSearchResults.value.isNotEmpty()) {
            // Recorded searched and played signal
            try {
                LyroApplication.instance.listeningEventRepository.recordEvent(
                    com.lyro.app.recommendation.model.ListeningEvent(
                        playbackSessionId = "search_play",
                        videoId = track.videoId,
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        eventType = com.lyro.app.recommendation.model.EventType.SEARCHED_AND_PLAYED
                    )
                )
            } catch (e: Exception) {
                // Non-blocking
            }
        }
        playbackManager.playTrack(track, contextList)
        _openNowPlayingEvent.tryEmit(Unit)
    }

    suspend fun getSongsForPlaylist(playlistId: Long): List<Song> {
        return repository.getSongsForPlaylist(playlistId)
    }

    fun playPlaylist(playlist: Playlist) {
        viewModelScope.launch {
            val playlistSongs = repository.getSongsForPlaylist(playlist.id)
            if (playlistSongs.isNotEmpty()) {
                playbackManager.playSong(playlistSongs.first(), playlistSongs)
                _openNowPlayingEvent.tryEmit(Unit)
            }
        }
    }

    fun addSongToPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            repository.addSongToPlaylist(playlistId, songId)
        }
    }

    fun createPlaylist(name: String, colorIndex: Int) {
        viewModelScope.launch {
            repository.createPlaylist(name, colorIndex)
        }
    }

    fun clearPlaybackError() {
        playbackManager.clearPlaybackError()
    }
}
