window.UIHandlers = {
    _lastMoviesList: null,
    _lastRoomsList: null,
    _updateTimeoutId: null,
    _loadingState: new Set(),

    _debounce(func, wait) {
        let timeout;
        return function executedFunction(...args) {
            const later = () => {
                clearTimeout(timeout);
                func.apply(this, args);
            };
            clearTimeout(timeout);
            timeout = setTimeout(later, wait);
        };
    },

    init() {
        this.setupEventListeners();
        this.focusNicknameInput();
        this.startOptimizedTimeUpdater();

        this._preloadMoviesList();
    },

    setupEventListeners() {
        document.addEventListener('click', this._handleDocumentClick.bind(this));
        document.addEventListener('keypress', this._handleDocumentKeypress.bind(this));

        this._setupVideoHandlers();
    },

    _handleDocumentClick(e) {
        const {target} = e;
        const handlers = {
            'join-btn': () => this._handleJoin(),
            'create-new-room-btn': () => this._handleCreateRoomShow(),
            'create-room-btn': () => this._handleCreateRoom(),
            'cancel-create-btn': () => this._handleCancelCreate(),
            'leave-room-btn': () => this._handleLeaveRoom(),
            'btn-load': () => this._handleLoadMovie()
        };

        const handler = handlers[target.id];
        if (handler) {
            e.preventDefault();
            handler();
            return;
        }

        if (target.closest('.room-item')) {
            this._handleRoomClick(target.closest('.room-item'));
        }
    },

    _handleDocumentKeypress(e) {
        if (e.key !== 'Enter') return;

        const {target} = e;
        const enterHandlers = {
            'nickname-input': () => AppState.elements.joinBtn?.click(),
            'room-name-input': () => AppState.elements.createRoomBtn?.click()
        };

        const handler = enterHandlers[target.id];
        if (handler) {
            e.preventDefault();
            handler();
        }
    },

    _handleJoin() {
        const nickname = AppState.elements.nicknameInput.value.trim();
        if (nickname.length < 2) {
            this._showError('Никнейм должен содержать минимум 2 символа');
            return;
        }

        AppState.userNickname = nickname;
        AppState.elements.nicknameModal.classList.add('hidden');
        AppState.isInitialized = true;

        AppState.socket.emit('get-rooms');
    },

    _handleCreateRoomShow() {
        if (!AppState.isInitialized) return;
        AppState.elements.createRoomModal.classList.remove('hidden');
        AppState.elements.roomNameInput.focus();
    },

    _handleCreateRoom() {
        const roomName = AppState.elements.roomNameInput.value.trim();
        if (roomName.length < 2) {
            this._showError('Название комнаты должно содержать минимум 2 символа');
            return;
        }

        AppState.socket.emit('create-room', roomName);
        AppState.elements.createRoomModal.classList.add('hidden');
        AppState.elements.roomNameInput.value = '';
    },

    _handleCancelCreate() {
        AppState.elements.createRoomModal.classList.add('hidden');
        AppState.elements.roomNameInput.value = '';
    },

    _handleLeaveRoom() {
        if (AppState.currentRoomId) {
            AppState.socket.emit('leave-room');
            AppState.currentRoomId = null;
            this.updateUIState();
            AppState.socket.emit('get-rooms');
        }
    },

    _handleLoadMovie() {
        if (!AppState.currentRoomId) {
            this._showError('Сначала присоединитесь к комнате');
            return;
        }

        const movieId = AppState.elements.selectMovies.value;
        if (!movieId) {
            this._showError('Сначала выберите фильм из списка');
            return;
        }

        AppState.socket.emit('select-movie', parseInt(movieId));
    },

    _handleRoomClick(roomElement) {
        const roomName = roomElement.querySelector('.room-name')?.textContent;
        if (roomName && roomName !== AppState.currentRoomId && AppState.isInitialized) {
            AppState.socket.emit('join-room', {
                roomId: roomName,
                nickname: AppState.userNickname
            });
        }
    },

    _setupVideoHandlers() {
        this._preloadMoviesList();
    },

    _preloadMoviesList() {
        setTimeout(() => this.loadMoviesList(), 100);
    },

    async loadMoviesList() {
        const cacheKey = 'moviesList';
        if (this._loadingState.has(cacheKey)) return;

        this._loadingState.add(cacheKey);
        const {selectMovies} = AppState.elements;

        try {
            selectMovies.innerHTML = '<option value="">— загружаем список фильмов —</option>';

            const response = await fetch('/api/movies');
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }

            const list = await response.json();

            const listString = JSON.stringify(list);
            if (this._lastMoviesList === listString) {
                return;
            }
            this._lastMoviesList = listString;

            const fragment = document.createDocumentFragment();

            if (!Array.isArray(list) || list.length === 0) {
                const option = document.createElement('option');
                option.textContent = list.length === 0 ? 'Фильмы не найдены' : 'Ошибка загрузки списка';
                option.value = '';
                fragment.appendChild(option);
            } else {
                const headerOption = document.createElement('option');
                headerOption.textContent = '— выбрать фильм —';
                headerOption.value = '';
                fragment.appendChild(headerOption);

                list.forEach(movie => {
                    const option = document.createElement('option');
                    const title = movie.year ? `${movie.title} (${movie.year})` : movie.title;
                    option.textContent = title;
                    option.value = movie.id;
                    fragment.appendChild(option);
                });
            }

            selectMovies.innerHTML = '';
            selectMovies.appendChild(fragment);

        } catch (error) {
            console.error('Ошибка загрузки списка фильмов:', error);
            selectMovies.innerHTML = `<option value="">Ошибка: ${error.message}</option>`;
        } finally {
            this._loadingState.delete(cacheKey);
        }
    },

    focusNicknameInput() {
        requestAnimationFrame(() => {
            AppState.elements.nicknameInput?.focus();
        });
    },

    updateUIState() {
        if (this._updateTimeoutId) {
            clearTimeout(this._updateTimeoutId);
        }

        this._updateTimeoutId = setTimeout(() => {
            this._performUIUpdate();
        }, 50);
    },

    _performUIUpdate() {
        const {
            movieList, videoPlayer, statusSection,
            noRoomMessage, currentRoomName, usersContainer,
            roomsSection, usersSection
        } = AppState.elements;

        const inRoom = !!AppState.currentRoomId;

        const elementsToShow = inRoom ?
            [movieList, videoPlayer, statusSection] :
            [noRoomMessage];

        const elementsToHide = inRoom ?
            [noRoomMessage] :
            [movieList, videoPlayer, statusSection];

        const sidebarElementsToShow = inRoom ? [usersSection] : [roomsSection];
        const sidebarElementsToHide = inRoom ? [roomsSection] : [usersSection];

        requestAnimationFrame(() => {
            elementsToShow.forEach(el => el?.classList.remove('hidden'));
            elementsToHide.forEach(el => el?.classList.add('hidden'));

            sidebarElementsToShow.forEach(el => el?.classList.remove('hidden'));
            sidebarElementsToHide.forEach(el => el?.classList.add('hidden'));

            if (inRoom) {
                if (currentRoomName) currentRoomName.textContent = AppState.currentRoomId;
            } else {
                if (videoPlayer) videoPlayer.src = '';
                if (usersContainer) {
                    usersContainer.innerHTML = '<div class="empty-message">Не в комнате</div>';
                }
            }
        });
    },

    updateRoomsList(roomsList) {
        const listString = JSON.stringify(roomsList);
        if (this._lastRoomsList === listString) {
            return;
        }
        this._lastRoomsList = listString;

        const {roomsContainer} = AppState.elements;

        if (!roomsList || roomsList.length === 0) {
            roomsContainer.innerHTML = '<div class="empty-message">Нет доступных комнат</div>';
            return;
        }

        const fragment = document.createDocumentFragment();

        roomsList.forEach(room => {
            const roomDiv = this._createRoomElement(room);
            fragment.appendChild(roomDiv);
        });

        roomsContainer.innerHTML = '';
        roomsContainer.appendChild(fragment);
    },

    _createRoomElement(room) {
        const roomDiv = document.createElement('div');
        roomDiv.className = 'room-item';

        if (room.id === AppState.currentRoomId) {
            roomDiv.classList.add('active');
        }

        roomDiv.innerHTML = `
            <div class="room-name">${this._escapeHtml(room.id)}</div>
            <div class="room-info">
                <span class="room-users">👥 ${room.users}</span>
                <span class="room-video">🎬 ${this._escapeHtml(room.video)}</span>
                <span class="room-status">${room.playing ? '▶️ Воспроизводится' : '⏸️ На паузе'}</span>
            </div>
        `;

        return roomDiv;
    },

    loadMovie(movieId, startTime = 0, autoplay = false) {
        const {videoPlayer, statusVideo, statusState} = AppState.elements;

        const url = `/api/movies/${movieId}/stream`;
        videoPlayer.src = url;
        videoPlayer.load();

        this._loadMovieInfo(movieId);

        statusState.textContent = autoplay ? '▶️ играет' : '⏸️ на паузе';

        const handleLoadedData = () => {
            videoPlayer.currentTime = startTime;
            if (autoplay) {
                AppState.isSyncing = true;
                videoPlayer.play().then(() => {
                    setTimeout(() => {
                        AppState.isSyncing = false;
                    }, 100);
                }).catch(console.error);
            }
        };

        videoPlayer.addEventListener('loadeddata', handleLoadedData, {once: true});
    },

    async _loadMovieInfo(movieId) {
        try {
            const response = await fetch(`/api/movies/${movieId}`);
            if (response.ok) {
                const movie = await response.json();
                const {statusVideo} = AppState.elements;
                const title = movie.year ? `${movie.title} (${movie.year})` : movie.title;
                statusVideo.textContent = title;
            }
        } catch (error) {
            console.error('Ошибка загрузки информации о фильме:', error);
        }
    },

    updateUsersList(usersList) {
        const {usersContainer} = AppState.elements;

        if (!AppState.currentRoomId) {
            usersContainer.innerHTML = '<div class="empty-message">Не в комнате</div>';
            return;
        }

        if (usersList.length === 0) {
            usersContainer.innerHTML = '<div class="empty-message">Нет участников онлайн</div>';
            return;
        }

        const fragment = document.createDocumentFragment();

        usersList.forEach(user => {
            const userDiv = document.createElement('div');
            userDiv.className = 'user-item';

            const minutes = Math.floor(user.currentTime / 60);
            const seconds = Math.floor(user.currentTime % 60);

            userDiv.innerHTML = `
                <div class="user-nickname">${this._escapeHtml(user.nickname)}</div>
                <div class="user-time">⏱️ ${minutes}:${seconds.toString().padStart(2, '0')}</div>
            `;

            fragment.appendChild(userDiv);
        });

        usersContainer.innerHTML = '';
        usersContainer.appendChild(fragment);
    },

    startOptimizedTimeUpdater() {
        const updateTime = () => {
            const {statusTime, videoPlayer} = AppState.elements;

            if (AppState.currentRoomId && videoPlayer?.src && !AppState.isSyncing) {
                const time = videoPlayer.currentTime;
                const minutes = Math.floor(time / 60);
                const seconds = Math.floor(time % 60);
                const newTime = `${minutes}:${seconds.toString().padStart(2, '0')}`;

                if (statusTime.textContent !== newTime) {
                    statusTime.textContent = newTime;
                }
            }

            requestAnimationFrame(updateTime);
        };

        updateTime();
    },

    _showError(message) {
        const notification = document.createElement('div');
        notification.className = 'error-notification';
        notification.textContent = message;
        notification.style.cssText = `
            position: fixed;
            top: 20px;
            right: 20px;
            background: var(--danger);
            color: white;
            padding: 15px;
            border-radius: 5px;
            z-index: 10000;
            animation: slideIn 0.3s ease;
        `;

        document.body.appendChild(notification);
        setTimeout(() => {
            notification.style.animation = 'slideOut 0.3s ease';
            setTimeout(() => notification.remove(), 300);
        }, 3000);
    },

    _escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
};
