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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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

    fun isTrackDownloaded(track: OnlineTrack): Boolean {
        return musicDownloader.isTrackDownloaded(track)
    }

    fun downloadTrack(track: OnlineTrack) {
        viewModelScope.launch {
            musicDownloader.downloadTrack(track)
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
        loadExploreTrending()
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
            val result = onlineRepository.searchSongs("Top Hits 2024 Trending")
            result.onSuccess { tracks ->
                _exploreTrending.value = tracks
            }
            _isExploreLoading.value = false
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
            repository.toggleFavorite(song)
        }
    }

    fun playSong(song: Song) {
        playbackManager.playSong(song, songs.value)
    }

    fun playQuickPicks(startSong: Song? = null) {
        val list = quickPicks.value
        if (list.isNotEmpty()) {
            val target = startSong ?: list.first()
            playbackManager.playSong(target, list)
        }
    }

    fun playOnlineTrack(track: OnlineTrack, customList: List<OnlineTrack>? = null) {
        val contextList = customList ?: _onlineSearchResults.value.ifEmpty { _exploreTrending.value }
        playbackManager.playTrack(track, contextList)
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
