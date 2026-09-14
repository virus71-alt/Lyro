/**
 * Lyro Link — Desktop Music Web Application
 * YouTube Music Grade Density, Architecture & Polish
 * 100% Embedded Vanilla JavaScript & Offline-First State Machine
 */

(function () {
  'use strict';

  // --- Core Application State ---
  const state = {
    authToken: localStorage.getItem('lyro_link_token') || '',
    activeNav: 'home',
    libraryFilter: 'all',
    homeShelves: null,
    currentList: [],
    queue: [],
    currentTrackIndex: -1,
    isPlaying: false,
    isSeeking: false,
    volume: parseFloat(localStorage.getItem('lyro_link_volume') || '0.85'),
    isMuted: false,
    isShuffle: false,
    repeatMode: 'off', // 'off' | 'all' | 'one'
    isOffline: false,
    deviceName: 'Lyro',
    playSessionId: '',
    reported50: false,
    reportedComplete: false
  };

  // --- DOM Elements Cache ---
  const el = {
    authScreen: document.getElementById('auth-screen'),
    appScreen: document.getElementById('app-screen'),
    authError: document.getElementById('auth-error'),
    pinDigits: document.querySelectorAll('.pin-digit'),
    btnAuthSubmit: document.getElementById('btn-auth-submit'),

    mainViewport: document.getElementById('main-viewport'),
    topbar: document.getElementById('topbar'),
    searchInput: document.getElementById('search-input'),
    searchClearBtn: document.getElementById('search-clear-btn'),
    connIndicatorText: document.getElementById('conn-text'),
    offlinePill: document.getElementById('offline-pill'),

    viewHome: document.getElementById('view-home'),
    viewSearch: document.getElementById('view-search'),
    viewLibrary: document.getElementById('view-library'),
    searchResultsContainer: document.getElementById('search-results-list'),
    libraryContainer: document.getElementById('library-list'),
    searchTitle: document.getElementById('search-query-title'),

    audio: document.getElementById('persistent-audio'),
    playerBar: document.getElementById('player-bar'),
    playerArtBox: document.getElementById('player-art-box'),
    playerArt: document.getElementById('player-art'),
    playerTitle: document.getElementById('player-title'),
    playerArtist: document.getElementById('player-artist'),
    playerLikeBtn: document.getElementById('player-like-btn'),
    btnPlayPause: document.getElementById('btn-play-pause'),
    iconPlay: document.getElementById('icon-play'),
    iconPause: document.getElementById('icon-pause'),
    btnPrev: document.getElementById('btn-prev'),
    btnNext: document.getElementById('btn-next'),
    btnShuffle: document.getElementById('btn-shuffle'),
    btnRepeat: document.getElementById('btn-repeat'),
    scrubber: document.getElementById('scrubber'),
    currentTimeLabel: document.getElementById('time-current'),
    totalTimeLabel: document.getElementById('time-total'),
    volumeSlider: document.getElementById('volume-slider'),
    btnMute: document.getElementById('btn-mute'),
    iconVolHigh: document.getElementById('icon-vol-high'),
    iconVolMute: document.getElementById('icon-vol-mute'),

    btnQueue: document.getElementById('btn-queue'),
    queueDrawer: document.getElementById('queue-drawer'),
    queueCloseBtn: document.getElementById('queue-close-btn'),
    queueList: document.getElementById('queue-list'),

    nowPlayingOverlay: document.getElementById('now-playing-overlay'),
    npCloseBtn: document.getElementById('np-close-btn'),
    npArt: document.getElementById('np-art'),
    npTitle: document.getElementById('np-title'),
    npArtist: document.getElementById('np-artist'),
    npAlbum: document.getElementById('np-album'),

    toast: document.getElementById('toast'),
    floatingMenu: document.getElementById('floating-menu')
  };

  let activeMenuTrack = null;
  let searchDebounceTimer = null;
  let statusPollTimer = null;

  // =========================================================================
  // 1. INITIALIZATION & AUTHENTICATION
  // =========================================================================

  function init() {
    setupAuthPinInputs();
    setupNavigation();
    setupAudioListeners();
    setupControls();
    setupSearch();
    setupKeyboardShortcuts();
    initVolume();

    // Check existing authentication token
    if (state.authToken) {
      verifySession();
    } else {
      showAuth();
    }
  }

  function setupAuthPinInputs() {
    el.pinDigits.forEach((input, idx) => {
      input.addEventListener('input', (e) => {
        const val = e.target.value.replace(/\D/g, '');
        e.target.value = val ? val[val.length - 1] : '';

        if (e.target.value && idx < el.pinDigits.length - 1) {
          el.pinDigits[idx + 1].focus();
        }

        const fullPin = Array.from(el.pinDigits).map(d => d.value).join('');
        if (fullPin.length === 6) {
          submitPairing();
        }
      });

      input.addEventListener('keydown', (e) => {
        if (e.key === 'Backspace' && !e.target.value && idx > 0) {
          el.pinDigits[idx - 1].focus();
        }
      });

      input.addEventListener('paste', (e) => {
        e.preventDefault();
        const text = (e.clipboardData || window.clipboardData).getData('text').trim().replace(/\D/g, '');
        if (text.length === 6) {
          text.split('').forEach((char, i) => {
            if (el.pinDigits[i]) el.pinDigits[i].value = char;
          });
          submitPairing();
        }
      });
    });

    el.btnAuthSubmit.addEventListener('click', submitPairing);
  }

  async function submitPairing() {
    const code = Array.from(el.pinDigits).map(d => d.value).join('');
    if (code.length !== 6) {
      showAuthError('Enter all 6 digits shown on your phone');
      return;
    }

    try {
      el.btnAuthSubmit.disabled = true;
      el.btnAuthSubmit.textContent = 'Connecting...';
      const resp = await fetch('/api/auth/pair', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ code: code })
      });
      const data = await resp.json();

      if (resp.ok && data.token) {
        state.authToken = data.token;
        localStorage.setItem('lyro_link_token', data.token);
        enterApp();
      } else {
        showAuthError(data.error || 'Incorrect pairing code');
      }
    } catch (err) {
      showAuthError('Cannot reach Lyro phone server. Check Wi-Fi connection.');
    } finally {
      el.btnAuthSubmit.disabled = false;
      el.btnAuthSubmit.textContent = 'Connect';
    }
  }

  async function verifySession() {
    try {
      const resp = await fetch('/api/auth/check', {
        headers: { 'X-Lyro-Session': state.authToken }
      });
      if (resp.ok) {
        enterApp();
      } else {
        state.authToken = '';
        localStorage.removeItem('lyro_link_token');
        showAuth();
      }
    } catch (err) {
      // If server unreachable temporarily, show app if token exists or show auth
      enterApp();
    }
  }

  function showAuth() {
    el.authScreen.classList.remove('hidden');
    el.authScreen.style.display = 'flex';
    setTimeout(() => {
      if (el.pinDigits[0]) el.pinDigits[0].focus();
    }, 100);
  }

  function enterApp() {
    el.authScreen.classList.add('hidden');
    setTimeout(() => {
      el.authScreen.style.display = 'none';
    }, 250);

    loadHome();
    pollStatus();
    if (statusPollTimer) clearInterval(statusPollTimer);
    statusPollTimer = setInterval(pollStatus, 12000);
  }

  function showAuthError(msg) {
    el.authError.textContent = msg;
    el.authError.style.display = 'block';
  }

  // =========================================================================
  // 2. NAVIGATION & VIEWS
  // =========================================================================

  function setupNavigation() {
    document.querySelectorAll('.nav-item').forEach(btn => {
      btn.addEventListener('click', (e) => {
        e.preventDefault();
        const dest = btn.getAttribute('data-nav');
        navigateTo(dest);
      });
    });

    document.querySelectorAll('.filter-chip').forEach(chip => {
      chip.addEventListener('click', () => {
        document.querySelectorAll('.filter-chip').forEach(c => c.classList.remove('active'));
        chip.classList.add('active');
        state.libraryFilter = chip.getAttribute('data-filter');
        renderLibrary();
      });
    });

    document.getElementById('btn-refresh').addEventListener('click', () => {
      if (state.activeNav === 'home') loadHome(true);
      else if (state.activeNav === 'library') loadLibrary(true);
      showToast('Catalog refreshed');
    });

    document.getElementById('btn-disconnect').addEventListener('click', () => {
      if (confirm('Disconnect from Lyro?')) {
        localStorage.removeItem('lyro_link_token');
        state.authToken = '';
        el.audio.pause();
        showAuth();
      }
    });
  }

  function navigateTo(dest) {
    state.activeNav = dest;
    document.querySelectorAll('.nav-item').forEach(b => {
      b.classList.toggle('active', b.getAttribute('data-nav') === dest);
    });

    el.viewHome.classList.remove('active');
    el.viewSearch.classList.remove('active');
    el.viewLibrary.classList.remove('active');

    if (dest === 'home') {
      el.viewHome.classList.add('active');
      if (!state.homeShelves) loadHome();
    } else if (dest === 'library' || dest === 'liked' || dest === 'downloads') {
      el.viewLibrary.classList.add('active');
      if (dest === 'liked') state.libraryFilter = 'liked';
      else if (dest === 'downloads') state.libraryFilter = 'downloads';
      else state.libraryFilter = 'all';

      document.querySelectorAll('.filter-chip').forEach(c => {
        c.classList.toggle('active', c.getAttribute('data-filter') === state.libraryFilter);
      });
      loadLibrary();
    }
  }

  // =========================================================================
  // 3. HOME VIEW & HORIZONTAL SHELVES
  // =========================================================================

  async function loadHome(force = false) {
    if (!force && state.homeShelves) {
      renderHome(state.homeShelves);
      return;
    }

    try {
      const resp = await fetch('/api/home', {
        headers: { 'X-Lyro-Session': state.authToken }
      });
      if (resp.status === 401 || resp.status === 403) {
        showAuth();
        return;
      }
      if (resp.ok) {
        const shelves = await resp.json();
        state.homeShelves = shelves;
        renderHome(shelves);
      } else {
        // Fallback: fetch individual endpoints
        await fallbackHomeLoad();
      }
    } catch (err) {
      await fallbackHomeLoad();
    }
  }

  async function fallbackHomeLoad() {
    try {
      const qpResp = await fetch('/api/quick-picks', { headers: { 'X-Lyro-Session': state.authToken } });
      const libResp = await fetch('/api/library', { headers: { 'X-Lyro-Session': state.authToken } });
      const quickPicks = qpResp.ok ? await qpResp.json() : [];
      const library = libResp.ok ? await libResp.json() : [];

      state.homeShelves = {
        quickPicks: quickPicks,
        listenAgain: [],
        downloaded: library.filter(t => t.isDownloaded && !t.isLocal),
        favorites: library.filter(t => t.isFavorite),
        library: library.filter(t => t.isLocal)
      };
      renderHome(state.homeShelves);
    } catch (e) {
      showToast('Failed to load music catalog');
    }
  }

  function renderHome(shelves) {
    el.viewHome.innerHTML = '';

    // 1. Quick Picks Shelf (4 rows high, multiple horizontal columns)
    if (shelves.quickPicks && shelves.quickPicks.length > 0) {
      const qpSection = document.createElement('section');
      qpSection.className = 'shelf-container';
      qpSection.innerHTML = `
        <div class="shelf-header">
          <h2 class="shelf-title">Quick picks</h2>
          <div class="shelf-controls">
            <button class="shelf-nav-btn" data-scroll="qp-scroll" data-dir="-1" title="Scroll left">&#8249;</button>
            <button class="shelf-nav-btn" data-scroll="qp-scroll" data-dir="1" title="Scroll right">&#8250;</button>
          </div>
        </div>
        <div class="quick-picks-scroll" id="qp-scroll"></div>
      `;

      const scrollContainer = qpSection.querySelector('#qp-scroll');
      shelves.quickPicks.forEach((track, index) => {
        const row = createQuickPickRow(track, shelves.quickPicks, index);
        scrollContainer.appendChild(row);
      });
      el.viewHome.appendChild(qpSection);
    }

    // 2. Listen Again Shelf (Artwork cards)
    if (shelves.listenAgain && shelves.listenAgain.length > 0) {
      el.viewHome.appendChild(createCarouselShelf('Listen again', shelves.listenAgain, 'listen-scroll'));
    }

    // 3. Downloaded Shelf (Artwork cards)
    if (shelves.downloaded && shelves.downloaded.length > 0) {
      el.viewHome.appendChild(createCarouselShelf('Downloaded for offline', shelves.downloaded, 'dl-scroll'));
    }

    // 4. Favorites Shelf (Artwork cards)
    if (shelves.favorites && shelves.favorites.length > 0) {
      el.viewHome.appendChild(createCarouselShelf('Your favorites', shelves.favorites, 'fav-scroll'));
    }

    // 5. From Your Library Shelf (Artwork cards)
    if (shelves.library && shelves.library.length > 0) {
      el.viewHome.appendChild(createCarouselShelf('From your device', shelves.library, 'lib-scroll'));
    }

    attachShelfScrollHandlers();
    highlightPlayingRow();
  }

  function createQuickPickRow(track, list, index) {
    const row = document.createElement('div');
    row.className = 'quick-pick-row';
    row.setAttribute('data-id', track.id);

    const artUrl = getTrackArtwork(track);
    row.innerHTML = `
      <div class="quick-pick-art-wrap">
        <img class="quick-pick-art" src="${artUrl}" loading="eager" decoding="async" alt="" onerror="this.src=getFallbackArt();">
        <div class="quick-pick-overlay-play">
          <svg viewBox="0 0 24 24"><path d="M8 5v14l11-7z"/></svg>
        </div>
      </div>
      <div class="quick-pick-info">
        <span class="quick-pick-title" title="${escapeHtml(track.title)}">${escapeHtml(track.title)}</span>
        <span class="quick-pick-artist" title="${escapeHtml(track.artist)}">${escapeHtml(track.artist)}</span>
      </div>
      <div class="quick-pick-actions">
        <span class="quick-pick-duration">${formatTime(track.durationMs / 1000)}</span>
        <button class="btn-icon-cell ${track.isFavorite ? 'liked' : ''}" data-action="like" title="Like">
          ${track.isFavorite ? '&#9829;' : '&#9825;'}
        </button>
        <button class="btn-icon-cell" data-action="more" title="More options">&#8942;</button>
      </div>
    `;

    row.addEventListener('click', (e) => {
      const action = e.target.closest('[data-action]')?.getAttribute('data-action');
      if (action === 'like') {
        toggleFavorite(track, e.target.closest('button'));
      } else if (action === 'more') {
        openContextMenu(e, track);
      } else {
        playTrack(list, index);
      }
    });

    return row;
  }

  function createCarouselShelf(title, tracks, scrollId) {
    const section = document.createElement('section');
    section.className = 'shelf-container';
    section.innerHTML = `
      <div class="shelf-header">
        <h2 class="shelf-title">${escapeHtml(title)}</h2>
        <div class="shelf-controls">
          <button class="shelf-nav-btn" data-scroll="${scrollId}" data-dir="-1" title="Scroll left">&#8249;</button>
          <button class="shelf-nav-btn" data-scroll="${scrollId}" data-dir="1" title="Scroll right">&#8250;</button>
        </div>
      </div>
      <div class="shelf-carousel" id="${scrollId}"></div>
    `;

    const carousel = section.querySelector(`#${scrollId}`);
    tracks.forEach((track, index) => {
      const card = document.createElement('div');
      card.className = 'artwork-card';
      card.setAttribute('data-id', track.id);
      const artUrl = getTrackArtwork(track);

      card.innerHTML = `
        <div class="card-artwork-box">
          <img class="card-artwork-img" src="${artUrl}" loading="lazy" decoding="async" alt="" onerror="this.src=getFallbackArt();">
          <div class="card-play-overlay">
            <div class="card-play-circle">
              <svg viewBox="0 0 24 24"><path d="M8 5v14l11-7z"/></svg>
            </div>
          </div>
        </div>
        <div class="card-title" title="${escapeHtml(track.title)}">${escapeHtml(track.title)}</div>
        <div class="card-subtitle" title="${escapeHtml(track.artist)}">${escapeHtml(track.artist)}</div>
      `;

      card.addEventListener('click', () => {
        playTrack(tracks, index);
      });

      carousel.appendChild(card);
    });

    return section;
  }

  function attachShelfScrollHandlers() {
    document.querySelectorAll('.shelf-nav-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        const targetId = btn.getAttribute('data-scroll');
        const dir = parseInt(btn.getAttribute('data-dir'), 10) || 1;
        const target = document.getElementById(targetId);
        if (target) {
          target.scrollBy({ left: dir * 420, behavior: 'smooth' });
        }
      });
    });
  }

  // =========================================================================
  // 4. SEARCH VIEW
  // =========================================================================

  function setupSearch() {
    el.searchInput.addEventListener('input', (e) => {
      const query = e.target.value.trim();
      el.searchClearBtn.style.display = query ? 'block' : 'none';

      clearTimeout(searchDebounceTimer);
      searchDebounceTimer = setTimeout(() => {
        executeSearch(query);
      }, 280);
    });

    el.searchClearBtn.addEventListener('click', () => {
      el.searchInput.value = '';
      el.searchClearBtn.style.display = 'none';
      if (state.activeNav === 'search') {
        navigateTo('home');
      }
    });
  }

  async function executeSearch(query) {
    if (!query) {
      if (state.activeNav === 'search') navigateTo('home');
      return;
    }

    state.activeNav = 'search';
    el.viewHome.classList.remove('active');
    el.viewLibrary.classList.remove('active');
    el.viewSearch.classList.add('active');
    el.searchTitle.textContent = `Matches for "${query}"`;
    el.searchResultsContainer.innerHTML = '<div style="padding: 24px 16px; color: var(--text-muted);">Searching...</div>';

    try {
      const resp = await fetch(`/api/search?q=${encodeURIComponent(query)}`, {
        headers: { 'X-Lyro-Session': state.authToken }
      });
      if (resp.ok) {
        const results = await resp.json();
        renderTrackTable(results, el.searchResultsContainer);
      } else {
        el.searchResultsContainer.innerHTML = '<div style="padding: 24px 16px; color: var(--text-muted);">No matching tracks found</div>';
      }
    } catch (err) {
      el.searchResultsContainer.innerHTML = '<div style="padding: 24px 16px; color: var(--text-muted);">Search request failed</div>';
    }
  }

  // =========================================================================
  // 5. LIBRARY VIEW
  // =========================================================================

  async function loadLibrary(force = false) {
    el.libraryContainer.innerHTML = '<div style="padding: 24px 16px; color: var(--text-muted);">Loading library tracks...</div>';

    try {
      const resp = await fetch('/api/library', {
        headers: { 'X-Lyro-Session': state.authToken }
      });
      if (resp.ok) {
        state.libraryTracks = await resp.json();
        renderLibrary();
      } else {
        el.libraryContainer.innerHTML = '<div style="padding: 24px 16px; color: var(--text-muted);">Failed to load library</div>';
      }
    } catch (err) {
      el.libraryContainer.innerHTML = '<div style="padding: 24px 16px; color: var(--text-muted);">Library unavailable</div>';
    }
  }

  function renderLibrary() {
    if (!state.libraryTracks) return;

    let filtered = state.libraryTracks;
    if (state.libraryFilter === 'downloads') {
      filtered = state.libraryTracks.filter(t => t.isDownloaded && !t.isLocal);
    } else if (state.libraryFilter === 'liked') {
      filtered = state.libraryTracks.filter(t => t.isFavorite);
    }

    renderTrackTable(filtered, el.libraryContainer);
  }

  function renderTrackTable(tracks, container) {
    container.innerHTML = '';
    if (tracks.length === 0) {
      container.innerHTML = '<div style="padding: 36px 16px; color: var(--text-muted); text-align: center;">No tracks found in this category</div>';
      return;
    }

    tracks.forEach((track, index) => {
      const row = document.createElement('div');
      row.className = 'track-row';
      row.setAttribute('data-id', track.id);
      const artUrl = getTrackArtwork(track);

      row.innerHTML = `
        <img class="track-row-art" src="${artUrl}" loading="lazy" decoding="async" alt="" onerror="this.src=getFallbackArt();">
        <div class="track-row-primary">
          <span class="track-row-title" title="${escapeHtml(track.title)}">${escapeHtml(track.title)}</span>
          <span class="track-row-artist" title="${escapeHtml(track.artist)}">${escapeHtml(track.artist)}</span>
        </div>
        <div class="track-row-album" title="${escapeHtml(track.album || '')}">${escapeHtml(track.album || '—')}</div>
        <div class="track-row-duration">${formatTime(track.durationMs / 1000)}</div>
        <div class="track-row-actions">
          <button class="btn-icon-cell ${track.isFavorite ? 'liked' : ''}" data-action="like" title="Like">
            ${track.isFavorite ? '&#9829;' : '&#9825;'}
          </button>
          <button class="btn-icon-cell" data-action="more" title="More options">&#8942;</button>
        </div>
      `;

      row.addEventListener('click', (e) => {
        const action = e.target.closest('[data-action]')?.getAttribute('data-action');
        if (action === 'like') {
          toggleFavorite(track, e.target.closest('button'));
        } else if (action === 'more') {
          openContextMenu(e, track);
        } else {
          playTrack(tracks, index);
        }
      });

      container.appendChild(row);
    });

    highlightPlayingRow();
  }

  // =========================================================================
  // 6. AUDIO PLAYBACK & PERSISTENT PLAYER BAR
  // =========================================================================

  function playTrack(list, index) {
    if (!list || index < 0 || index >= list.length) return;
    state.queue = [...list];
    state.currentTrackIndex = index;
    state.currentList = list;

    renderQueue();
    loadAndPlayCurrent();
  }

  function loadAndPlayCurrent() {
    if (state.currentTrackIndex < 0 || state.currentTrackIndex >= state.queue.length) return;
    const track = state.queue[state.currentTrackIndex];

    state.reported50 = false;
    state.reportedComplete = false;
    state.playSessionId = 'sess_' + Math.random().toString(36).substring(2, 10);

    // Update bottom player UI
    el.playerTitle.textContent = track.title;
    el.playerArtist.textContent = track.artist;
    const artUrl = getTrackArtwork(track);
    el.playerArt.src = artUrl;
    el.playerLikeBtn.classList.toggle('liked', !!track.isFavorite);
    el.playerLikeBtn.innerHTML = track.isFavorite ? '&#9829;' : '&#9825;';

    // Update Now Playing overlay
    el.npTitle.textContent = track.title;
    el.npArtist.textContent = track.artist;
    el.npAlbum.textContent = track.album || '';
    el.npArt.src = artUrl;

    // Stream URL
    const streamUrl = `${track.streamUrl}?session=${encodeURIComponent(state.authToken)}`;
    el.audio.src = streamUrl;
    el.audio.play().catch(err => {
      showToast('Playback error: ' + err.message);
    });

    sendEvent(track.id, 'PLAY_STARTED', 0, track.durationMs);
    highlightPlayingRow();
  }

  function setupAudioListeners() {
    el.audio.addEventListener('play', () => {
      state.isPlaying = true;
      updatePlayPauseButton(true);
    });

    el.audio.addEventListener('pause', () => {
      state.isPlaying = false;
      updatePlayPauseButton(false);
    });

    // Targeted timeupdate without rebuilding DOM
    el.audio.addEventListener('timeupdate', () => {
      if (state.isSeeking) return;
      const cur = el.audio.currentTime;
      const currentTrack = state.queue[state.currentTrackIndex];
      const dur = el.audio.duration || (currentTrack?.durationMs ? currentTrack.durationMs / 1000 : 0);

      el.currentTimeLabel.textContent = formatTime(cur);
      if (dur > 0) {
        el.totalTimeLabel.textContent = formatTime(dur);
        el.scrubber.value = (cur / dur) * 100;

        // Telemetry 50%
        if (!state.reported50 && cur / dur >= 0.5) {
          state.reported50 = true;
          sendEvent(currentTrack?.id, 'PLAY_50_PERCENT', Math.round(cur * 1000), Math.round(dur * 1000));
        }
      }
    });

    el.audio.addEventListener('ended', () => {
      const currentTrack = state.queue[state.currentTrackIndex];
      if (!state.reportedComplete && currentTrack) {
        state.reportedComplete = true;
        sendEvent(currentTrack.id, 'PLAY_COMPLETED', Math.round(el.audio.currentTime * 1000), currentTrack.durationMs);
      }

      if (state.repeatMode === 'one') {
        el.audio.currentTime = 0;
        el.audio.play();
      } else {
        playNext();
      }
    });

    el.audio.addEventListener('error', () => {
      showToast('Audio track failed to stream or phone offline');
      setTimeout(playNext, 1200);
    });
  }

  function setupControls() {
    el.btnPlayPause.addEventListener('click', () => {
      if (el.audio.paused) {
        if (!el.audio.src && state.queue.length > 0) {
          playTrack(state.queue, 0);
        } else {
          el.audio.play();
        }
      } else {
        el.audio.pause();
      }
    });

    el.btnNext.addEventListener('click', () => {
      const curTrack = state.queue[state.currentTrackIndex];
      if (el.audio.currentTime < 15 && curTrack) {
        sendEvent(curTrack.id, 'SKIPPED_EARLY', Math.round(el.audio.currentTime * 1000), curTrack.durationMs);
      }
      playNext();
    });

    el.btnPrev.addEventListener('click', playPrev);

    el.btnShuffle.addEventListener('click', () => {
      state.isShuffle = !state.isShuffle;
      el.btnShuffle.classList.toggle('active', state.isShuffle);
      showToast(state.isShuffle ? 'Shuffle on' : 'Shuffle off');
    });

    el.btnRepeat.addEventListener('click', () => {
      if (state.repeatMode === 'off') {
        state.repeatMode = 'all';
        el.btnRepeat.classList.add('active');
        showToast('Repeat all');
      } else if (state.repeatMode === 'all') {
        state.repeatMode = 'one';
        el.btnRepeat.classList.add('active');
        el.btnRepeat.setAttribute('title', 'Repeat one');
        showToast('Repeat one');
      } else {
        state.repeatMode = 'off';
        el.btnRepeat.classList.remove('active');
        el.btnRepeat.setAttribute('title', 'Repeat off');
        showToast('Repeat off');
      }
    });

    // Scrubber seeking
    el.scrubber.addEventListener('input', () => { state.isSeeking = true; });
    el.scrubber.addEventListener('change', () => {
      const curTrack = state.queue[state.currentTrackIndex];
      const dur = el.audio.duration || (curTrack?.durationMs ? curTrack.durationMs / 1000 : 0);
      if (dur > 0) {
        el.audio.currentTime = (el.scrubber.value / 100) * dur;
      }
      state.isSeeking = false;
    });

    // Scrubber background track fill
    el.scrubber.addEventListener('input', updateScrubberGradient);
    el.audio.addEventListener('timeupdate', updateScrubberGradient);

    // Player Bar Artwork / Title click -> expands Now Playing overlay
    el.playerArtBox.addEventListener('click', toggleNowPlayingOverlay);
    document.querySelector('.player-meta').addEventListener('click', toggleNowPlayingOverlay);
    el.npCloseBtn.addEventListener('click', toggleNowPlayingOverlay);

    // Like in player bar
    el.playerLikeBtn.addEventListener('click', () => {
      const track = state.queue[state.currentTrackIndex];
      if (track) toggleFavorite(track, el.playerLikeBtn);
    });

    // Queue Drawer toggle
    el.btnQueue.addEventListener('click', () => {
      el.queueDrawer.classList.toggle('open');
    });
    el.queueCloseBtn.addEventListener('click', () => {
      el.queueDrawer.classList.remove('open');
    });
  }

  function updateScrubberGradient() {
    const val = el.scrubber.value;
    el.scrubber.style.background = `linear-gradient(to right, var(--accent) ${val}%, rgba(255, 255, 255, 0.2) ${val}%)`;
  }

  function playNext() {
    if (state.queue.length === 0) return;

    if (state.isShuffle) {
      const nextIdx = Math.floor(Math.random() * state.queue.length);
      state.currentTrackIndex = nextIdx;
      loadAndPlayCurrent();
      renderQueue();
      return;
    }

    if (state.currentTrackIndex < state.queue.length - 1) {
      state.currentTrackIndex++;
      loadAndPlayCurrent();
      renderQueue();
    } else if (state.repeatMode === 'all') {
      state.currentTrackIndex = 0;
      loadAndPlayCurrent();
      renderQueue();
    }
  }

  function playPrev() {
    if (el.audio.currentTime > 3) {
      el.audio.currentTime = 0;
    } else if (state.currentTrackIndex > 0) {
      state.currentTrackIndex--;
      loadAndPlayCurrent();
      renderQueue();
    }
  }

  function updatePlayPauseButton(isPlaying) {
    el.iconPlay.style.display = isPlaying ? 'none' : 'block';
    el.iconPause.style.display = isPlaying ? 'block' : 'none';
  }

  function toggleNowPlayingOverlay() {
    el.nowPlayingOverlay.classList.toggle('open');
  }

  // =========================================================================
  // 7. VOLUME & MUTE
  // =========================================================================

  function initVolume() {
    el.audio.volume = state.volume;
    el.volumeSlider.value = state.volume * 100;

    el.volumeSlider.addEventListener('input', () => {
      const vol = el.volumeSlider.value / 100;
      state.volume = vol;
      state.isMuted = vol === 0;
      el.audio.volume = vol;
      el.audio.muted = state.isMuted;
      localStorage.setItem('lyro_link_volume', vol.toString());
      updateVolumeIcon();
    });

    el.btnMute.addEventListener('click', () => {
      state.isMuted = !state.isMuted;
      el.audio.muted = state.isMuted;
      updateVolumeIcon();
    });
  }

  function updateVolumeIcon() {
    const muted = state.isMuted || state.volume === 0;
    el.iconVolHigh.style.display = muted ? 'none' : 'block';
    el.iconVolMute.style.display = muted ? 'block' : 'none';
  }

  // =========================================================================
  // 8. QUEUE DRAWER
  // =========================================================================

  function renderQueue() {
    el.queueList.innerHTML = '';
    state.queue.forEach((track, idx) => {
      const item = document.createElement('div');
      item.className = 'queue-item' + (idx === state.currentTrackIndex ? ' current' : '');
      const artUrl = getTrackArtwork(track);

      item.innerHTML = `
        <img class="queue-item-art" src="${artUrl}" loading="lazy" decoding="async" alt="" onerror="this.src=getFallbackArt();">
        <div class="queue-item-meta">
          <div class="queue-item-title">${escapeHtml(track.title)}</div>
          <div class="queue-item-artist">${escapeHtml(track.artist)}</div>
        </div>
        <span class="queue-item-duration">${formatTime(track.durationMs / 1000)}</span>
      `;

      item.addEventListener('click', () => {
        state.currentTrackIndex = idx;
        loadAndPlayCurrent();
        renderQueue();
      });

      el.queueList.appendChild(item);
    });
  }

  // =========================================================================
  // 9. FAVORITES & CONTEXT MENU
  // =========================================================================

  async function toggleFavorite(track, buttonEl) {
    if (!track || !track.id) return;

    try {
      const resp = await fetch(`/api/tracks/${encodeURIComponent(track.id)}/favorite`, {
        method: 'POST',
        headers: { 'X-Lyro-Session': state.authToken }
      });
      if (resp.ok) {
        const res = await resp.json();
        const isFav = res.isFavorite;
        track.isFavorite = isFav;

        if (buttonEl) {
          buttonEl.classList.toggle('liked', isFav);
          buttonEl.innerHTML = isFav ? '&#9829;' : '&#9825;';
        }

        if (state.queue[state.currentTrackIndex]?.id === track.id) {
          el.playerLikeBtn.classList.toggle('liked', isFav);
          el.playerLikeBtn.innerHTML = isFav ? '&#9829;' : '&#9825;';
        }

        // Update card in home shelves if present
        document.querySelectorAll(`[data-id="${track.id}"] [data-action="like"]`).forEach(b => {
          b.classList.toggle('liked', isFav);
          b.innerHTML = isFav ? '&#9829;' : '&#9825;';
        });

        showToast(isFav ? 'Added to Liked Songs' : 'Removed from Liked Songs');
      }
    } catch (err) {
      showToast('Failed to update favorite status');
    }
  }

  function openContextMenu(e, track) {
    e.stopPropagation();
    activeMenuTrack = track;

    const menu = el.floatingMenu;
    menu.classList.add('open');

    // Position menu near cursor
    const x = Math.min(e.clientX, window.innerWidth - 200);
    const y = Math.min(e.clientY, window.innerHeight - 180);
    menu.style.left = `${x}px`;
    menu.style.top = `${y}px`;
  }

  document.addEventListener('click', (e) => {
    if (!e.target.closest('#floating-menu')) {
      el.floatingMenu.classList.remove('open');
    }
  });

  document.querySelectorAll('#floating-menu .menu-item').forEach(item => {
    item.addEventListener('click', () => {
      const action = item.getAttribute('data-menu-action');
      if (!activeMenuTrack) return;

      if (action === 'play-next') {
        state.queue.splice(state.currentTrackIndex + 1, 0, activeMenuTrack);
        renderQueue();
        showToast(`"${activeMenuTrack.title}" will play next`);
      } else if (action === 'add-queue') {
        state.queue.push(activeMenuTrack);
        renderQueue();
        showToast(`Added to queue`);
      } else if (action === 'toggle-fav') {
        toggleFavorite(activeMenuTrack);
      }
      el.floatingMenu.classList.remove('open');
    });
  });

  // =========================================================================
  // 10. KEYBOARD SHORTCUTS
  // =========================================================================

  function setupKeyboardShortcuts() {
    document.addEventListener('keydown', (e) => {
      // Don't trigger shortcuts when typing in search or PIN input
      if (['INPUT', 'TEXTAREA'].includes(document.activeElement?.tagName)) return;

      if (e.code === 'Space') {
        e.preventDefault();
        el.btnPlayPause.click();
      } else if (e.code === 'ArrowRight') {
        e.preventDefault();
        el.audio.currentTime = Math.min(el.audio.duration || Infinity, el.audio.currentTime + 5);
      } else if (e.code === 'ArrowLeft') {
        e.preventDefault();
        el.audio.currentTime = Math.max(0, el.audio.currentTime - 5);
      } else if (e.key === 'm' || e.key === 'M') {
        el.btnMute.click();
      }
    });
  }

  // =========================================================================
  // 11. TELEMETRY & STATUS MONITOR
  // =========================================================================

  async function sendEvent(trackId, eventType, positionMs, durationMs) {
    if (!trackId || !state.authToken) return;
    try {
      await fetch('/api/events', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Lyro-Session': state.authToken
        },
        body: JSON.stringify({
          trackId: trackId,
          eventType: eventType,
          positionMs: positionMs,
          durationMs: durationMs,
          sessionId: state.playSessionId
        })
      });
    } catch (ignored) {}
  }

  async function pollStatus() {
    try {
      const resp = await fetch('/api/status', {
        headers: { 'X-Lyro-Session': state.authToken }
      });
      if (resp.ok) {
        const data = await resp.json();
        state.deviceName = data.deviceName || 'Lyro';
        state.isOffline = !data.isOnline;

        el.connIndicatorText.textContent = `Connected to ${state.deviceName}`;
        el.offlinePill.style.display = data.isOnline ? 'none' : 'inline-flex';
      }
    } catch (ignored) {}
  }

  // =========================================================================
  // 12. UTILITIES
  // =========================================================================

  function getTrackArtwork(track) {
    if (!track.artworkUrl) return getFallbackArt();
    return track.artworkUrl + (track.artworkUrl.includes('?') ? '&' : '?') + 'session=' + encodeURIComponent(state.authToken);
  }

  function getFallbackArt() {
    return 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="200" height="200" viewBox="0 0 24 24"><rect width="24" height="24" fill="%23161616"/><circle cx="12" cy="12" r="9" fill="%231f1f1f"/><path d="M12 7v7.2c-.44-.2-.93-.32-1.45-.32-1.74 0-3.15 1.34-3.15 3s1.41 3 3.15 3 3.15-1.34 3.15-3V9.5h3.3V7H12z" fill="rgba(255,255,255,0.2)"/><circle cx="16.5" cy="7.8" r="0.9" fill="%23e0fe10"/></svg>';
  }

  function highlightPlayingRow() {
    const curId = state.queue[state.currentTrackIndex]?.id;
    document.querySelectorAll('.quick-pick-row, .track-row').forEach(row => {
      const id = row.getAttribute('data-id');
      row.classList.toggle('playing', !!(id && id === curId));
    });
  }

  function formatTime(seconds) {
    if (!seconds || isNaN(seconds) || seconds < 0) return '0:00';
    const m = Math.floor(seconds / 60);
    const s = Math.floor(seconds % 60);
    return `${m}:${s < 10 ? '0' : ''}${s}`;
  }

  function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }

  function showToast(msg) {
    el.toast.textContent = msg;
    el.toast.classList.add('visible');
    setTimeout(() => {
      el.toast.classList.remove('visible');
    }, 2800);
  }

  // Start app
  window.getFallbackArt = getFallbackArt;
  document.addEventListener('DOMContentLoaded', init);
  if (document.readyState === 'complete' || document.readyState === 'interactive') {
    init();
  }
})();
