package com.lyro.app.ui.songs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val playbackManager: PlaybackManager
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.TITLE)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _favoritesOnly = MutableStateFlow(false)
    val favoritesOnly: StateFlow<Boolean> = _favoritesOnly.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Online search states
    private val _isOnlineMode = MutableStateFlow(false)
    val isOnlineMode: StateFlow<Boolean> = _isOnlineMode.asStateFlow()

    private val _onlineSearchResults = MutableStateFlow<List<OnlineTrack>>(emptyList())
    val onlineSearchResults: StateFlow<List<OnlineTrack>> = _onlineSearchResults.asStateFlow()

    private val _isSearchingOnline = MutableStateFlow(false)
    val isSearchingOnline: StateFlow<Boolean> = _isSearchingOnline.asStateFlow()

    private val _onlineSearchError = MutableStateFlow<String?>(null)
    val onlineSearchError: StateFlow<String?> = _onlineSearchError.asStateFlow()

    private var searchJob: Job? = null

    val playlists: StateFlow<List<Playlist>> = repository.playlists

    // Combined filtered local song list
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

    val currentSong: StateFlow<Song?> = playbackManager.currentSong
    val currentTrack: StateFlow<PlayableTrack?> = playbackManager.currentTrack
    val isPlaying: StateFlow<Boolean> = playbackManager.isPlaying
    val isResolvingStream: StateFlow<Boolean> = playbackManager.isResolvingStream
    val playbackError: StateFlow<String?> = playbackManager.playbackError

    init {
        loadSongs()
    }

    fun loadSongs() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.loadSongs()
            _isLoading.value = false
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        if (_isOnlineMode.value) {
            triggerDebouncedOnlineSearch(query)
        }
    }

    fun setOnlineMode(enabled: Boolean) {
        _isOnlineMode.value = enabled
        if (enabled && _searchQuery.value.isNotBlank() && _onlineSearchResults.value.isEmpty()) {
            triggerDebouncedOnlineSearch(_searchQuery.value)
        }
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
            delay(500) // 500ms debounce
            _isSearchingOnline.value = true
            _onlineSearchError.value = null

            val result = onlineRepository.searchSongs(trimmed)
            _isSearchingOnline.value = false
            result.onSuccess { tracks ->
                _onlineSearchResults.value = tracks
                _onlineSearchError.value = if (tracks.isEmpty()) "No tracks found for \"$trimmed\"" else null
            }.onFailure { error ->
                _onlineSearchError.value = "Search failed: ${error.message}"
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
            repository.toggleFavorite(song)
        }
    }

    fun playSong(song: Song) {
        playbackManager.playSong(song, songs.value)
    }

    fun playOnlineTrack(track: OnlineTrack) {
        playbackManager.playTrack(track, _onlineSearchResults.value)
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
