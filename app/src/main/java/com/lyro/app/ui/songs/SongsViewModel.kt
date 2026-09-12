package com.lyro.app.ui.songs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lyro.app.data.model.Playlist
import com.lyro.app.data.model.Song
import com.lyro.app.data.repository.MusicRepository
import com.lyro.app.data.repository.SortOrder
import com.lyro.app.service.PlaybackManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SongsViewModel(
    private val repository: MusicRepository,
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

    val playlists: StateFlow<List<Playlist>> = repository.playlists

    // Combined filtered song list
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
    val isPlaying: StateFlow<Boolean> = playbackManager.isPlaying

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
}
