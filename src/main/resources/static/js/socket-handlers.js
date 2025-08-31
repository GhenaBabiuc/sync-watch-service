window.SocketHandlers = {
    _listenersAttached: false,
    _reconnectAttempts: 0,
    _maxReconnectAttempts: 5,

    init() {
        if (this._listenersAttached) return;
        this.setupSocketListeners();
        this._listenersAttached = true;
    },

    setupSocketListeners() {
        const socket = AppState.socket;

        socket.on('rooms-list', this._handleRoomsList.bind(this));
        socket.on('room-created', this._handleRoomCreated.bind(this));
        socket.on('room-joined', this._handleRoomJoined.bind(this));
        socket.on('load-video', this._handleLoadVideo.bind(this));
        socket.on('users-list', this._handleUsersList.bind(this));
        socket.on('error', this._handleServerError.bind(this));
        socket.on('sync-play', this._handleSyncPlay.bind(this));
        socket.on('sync-pause', this._handleSyncPause.bind(this));
        socket.on('sync-seek', this._handleSyncSeek.bind(this));
        socket.on('connect', this._handleConnect.bind(this));
        socket.on('disconnect', this._handleDisconnect.bind(this));
        socket.on('reconnect', this._handleReconnect.bind(this));
        socket.on('connect_error', this._handleConnectError.bind(this));
    },

    _handleRoomsList(roomsList) {
        console.log('Получен список комнат:', roomsList);
        UIHandlers.updateRoomsList(roomsList);
    },

    _handleRoomCreated(roomId) {
        console.log('Комната создана:', roomId);
        setTimeout(() => {
            AppState.socket.emit('join-room', {
                roomId,
                nickname: AppState.userNickname
            });
        }, 100);
    },

    _handleRoomJoined(data) {
        console.log('Присоединились к комнате:', data);

        AppState.currentRoomId = data.roomId;
        UIHandlers.updateUIState();

        if (data.video) {
            console.log('Загружаем видео:', data.video);
            UIHandlers.loadVideo(data.video, data.time, data.playing);
        } else {
            this._resetVideoStatus();
        }

        setTimeout(() => {
            AppState.socket.emit('get-rooms');
        }, 200);
    },

    _handleLoadVideo(payload) {
        console.log('Загружаем новое видео:', payload);
        if (AppState.currentRoomId) {
            UIHandlers.loadVideo(payload.video, payload.time, false);
        }
    },

    _handleUsersList(usersList) {
        console.log('Обновлен список пользователей:', usersList);
        UIHandlers.updateUsersList(usersList);
    },

    _handleServerError(message) {
        console.error('Ошибка от сервера:', message);
        this._showErrorNotification('Ошибка сервера: ' + message);
    },

    _handleSyncPlay(timestamp) {
        console.log('Синхронное воспроизведение:', timestamp);
        this._executeVideoSync(() => {
            const {videoPlayer, statusState} = AppState.elements;

            if (AppState.currentRoomId && videoPlayer.src) {
                AppState.isSyncing = true;
                videoPlayer.currentTime = timestamp;

                return videoPlayer.play().then(() => {
                    this._updateVideoStatus('▶️ играет');
                    setTimeout(() => {
                        AppState.isSyncing = false;
                    }, 100);
                }).catch(error => {
                    console.error('Ошибка при синхронном воспроизведении:', error);
                    AppState.isSyncing = false;
                    throw error;
                });
            }

            this._updateVideoStatus('▶️ играет');
            return Promise.resolve();
        });
    },

    _handleSyncPause(timestamp) {
        console.log('Синхронная пауза:', timestamp);
        this._executeVideoSync(() => {
            const {videoPlayer} = AppState.elements;

            if (AppState.currentRoomId && videoPlayer.src) {
                AppState.isSyncing = true;
                videoPlayer.currentTime = timestamp;
                videoPlayer.pause();
                setTimeout(() => {
                    AppState.isSyncing = false;
                }, 100);
            }

            this._updateVideoStatus('⏸️ на паузе');
            return Promise.resolve();
        });
    },

    _handleSyncSeek(timestamp) {
        console.log('Синхронная перемотка:', timestamp);
        this._executeVideoSync(() => {
            const {videoPlayer, statusTime} = AppState.elements;

            if (AppState.currentRoomId && videoPlayer.src &&
                Math.abs(videoPlayer.currentTime - timestamp) > AppState.seekThreshold) {

                AppState.isSyncing = true;
                videoPlayer.currentTime = timestamp;
                AppState.lastSeekTime = timestamp;

                setTimeout(() => {
                    AppState.isSyncing = false;
                }, 200);
            }

            this._updateTimeDisplay(timestamp);
            return Promise.resolve();
        });
    },

    _handleConnect() {
        console.log('Подключено к серверу');
        this._reconnectAttempts = 0;
        this._hideConnectionError();

        if (AppState.isInitialized) {
            AppState.socket.emit('get-rooms');
        }
    },

    _handleDisconnect(reason) {
        console.log('Отключено от сервера:', reason);
        AppState.currentRoomId = null;
        UIHandlers.updateUIState();

        this._showConnectionError('Соединение с сервером потеряно');
    },

    _handleReconnect() {
        console.log('Переподключение к серверу');
        this._reconnectAttempts = 0;
        this._hideConnectionError();

        if (AppState.isInitialized) {
            AppState.socket.emit('get-rooms');
        }
    },

    _handleConnectError(error) {
        console.error('Ошибка подключения:', error);
        this._reconnectAttempts++;

        if (this._reconnectAttempts >= this._maxReconnectAttempts) {
            this._showConnectionError('Не удается подключиться к серверу. Проверьте подключение.');
        } else {
            this._showConnectionError(`Переподключение... (попытка ${this._reconnectAttempts}/${this._maxReconnectAttempts})`);
        }
    },

    _executeVideoSync(syncFunction) {
        try {
            const result = syncFunction();
            if (result && typeof result.catch === 'function') {
                result.catch(error => {
                    console.error('Ошибка синхронизации видео:', error);
                    AppState.isSyncing = false;
                });
            }
        } catch (error) {
            console.error('Ошибка выполнения синхронизации:', error);
            AppState.isSyncing = false;
        }
    },

    _updateVideoStatus(status) {
        const {statusState} = AppState.elements;
        if (statusState) {
            statusState.textContent = status;
        }
    },

    _updateTimeDisplay(timestamp) {
        const {statusTime} = AppState.elements;
        if (statusTime) {
            const minutes = Math.floor(timestamp / 60);
            const seconds = Math.floor(timestamp % 60);
            statusTime.textContent = `${minutes}:${seconds.toString().padStart(2, '0')}`;
        }
    },

    _resetVideoStatus() {
        const {statusVideo, statusState, statusTime} = AppState.elements;
        if (statusVideo) statusVideo.textContent = '—';
        if (statusState) statusState.textContent = 'ожидаем выбора';
        if (statusTime) statusTime.textContent = '0:00';
    },

    _showConnectionError(message) {
        this._hideConnectionError();

        const notification = document.createElement('div');
        notification.className = 'connection-error-notification';
        notification.textContent = message;
        notification.style.cssText = `
            position: fixed;
            top: 20px;
            left: 50%;
            transform: translateX(-50%);
            background: var(--danger);
            color: white;
            padding: 12px 24px;
            border-radius: 8px;
            z-index: 10000;
            box-shadow: 0 4px 16px rgba(0, 0, 0, 0.3);
            animation: slideDown 0.3s ease;
            font-weight: 500;
        `;

        document.body.appendChild(notification);
    },

    _hideConnectionError() {
        const existing = document.querySelector('.connection-error-notification');
        if (existing) {
            existing.style.animation = 'slideUp 0.3s ease';
            setTimeout(() => existing.remove(), 300);
        }
    },

    _showErrorNotification(message) {
        const notification = document.createElement('div');
        notification.className = 'error-notification';
        notification.textContent = message;
        notification.style.cssText = `
            position: fixed;
            top: 20px;
            right: 20px;
            background: var(--danger);
            color: white;
            padding: 15px 20px;
            border-radius: 8px;
            z-index: 10000;
            box-shadow: 0 4px 16px rgba(0, 0, 0, 0.3);
            animation: slideInRight 0.3s ease;
            max-width: 300px;
            word-wrap: break-word;
        `;

        document.body.appendChild(notification);

        setTimeout(() => {
            notification.style.animation = 'slideOutRight 0.3s ease';
            setTimeout(() => notification.remove(), 300);
        }, 5000);

        notification.addEventListener('click', () => {
            notification.style.animation = 'slideOutRight 0.3s ease';
            setTimeout(() => notification.remove(), 300);
        });
    },

    destroy() {
        if (AppState.socket) {
            AppState.socket.removeAllListeners();
        }
        this._listenersAttached = false;
        this._hideConnectionError();
    }
};
