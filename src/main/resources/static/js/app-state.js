window.AppState = {
    userNickname: '',
    currentRoomId: null,
    isInitialized: false,
    isSyncing: false,
    lastSeekTime: 0,
    seekThreshold: 0.5,
    syncDebounceTime: 100,
    _elementCache: new Map(),
    _eventListenersAttached: false,
    socket: null,

    elements: new Proxy({}, {
        get(target, prop) {
            if (!target[prop]) {
                target[prop] = AppState._getElement(prop);
            }
            return target[prop];
        }
    }),

    init() {
        if (this.isInitialized) return;

        const socketURL = 'http://localhost:3001';
        console.log('🔌 Подключаемся к Socket.IO серверу:', socketURL);

        this.socket = io(socketURL, {
            transports: ['websocket', 'polling'],
            upgrade: true,
            rememberUpgrade: true,
            timeout: 10000,
            forceNew: true,
            autoConnect: true,
            reconnection: true,
            reconnectionAttempts: 5,
            reconnectionDelay: 1000
        });

        this._initErrorHandling();
        this.isInitialized = true;
        console.log('✅ AppState инициализировано для работы с фильмами');
    },

    _getElement(elementKey) {
        if (this._elementCache.has(elementKey)) {
            return this._elementCache.get(elementKey);
        }

        const elementMap = {
            nicknameModal: 'nickname-modal',
            nicknameInput: 'nickname-input',
            joinBtn: 'join-btn',
            createRoomModal: 'create-room-modal',
            roomNameInput: 'room-name-input',
            createRoomBtn: 'create-room-btn',
            cancelCreateBtn: 'cancel-create-btn',
            createNewRoomBtn: 'create-new-room-btn',
            roomsContainer: 'rooms-container',
            roomsSection: 'rooms-section',
            usersSection: 'users-section',
            currentRoomName: 'current-room-name',
            leaveRoomBtn: 'leave-room-btn',
            selectMovies: 'movies',
            btnLoad: 'btn-load',
            videoPlayer: 'videoPlayer',
            movieList: 'movie-list',
            statusSection: 'status',
            statusVideo: 'status-video',
            statusState: 'status-state',
            statusTime: 'status-time',
            usersContainer: 'users-container',
            noRoomMessage: 'no-room-message'
        };

        const elementId = elementMap[elementKey];
        if (!elementId) {
            console.warn(`⚠ Неизвестный ключ элемента: ${elementKey}`);
            return null;
        }

        const element = document.getElementById(elementId);
        if (!element) {
            console.error(`⚠ Элемент не найден: ${elementId}`);
            return null;
        }

        this._elementCache.set(elementKey, element);
        return element;
    },

    _initErrorHandling() {
        this.socket.on('connect', () => {
            console.log('✅ Успешно подключено к Socket.IO серверу на порту 3001');
            console.log('🆔 Socket ID:', this.socket.id);
        });

        this.socket.on('connect_error', (error) => {
            console.error('⚠ Ошибка подключения к Socket.IO серверу (порт 3001):', error);
            this._showConnectionError('Не удается подключиться к Socket.IO серверу. Проверьте что сервер запущен на порту 3001.');
        });

        this.socket.on('disconnect', (reason) => {
            console.warn('⚠️ Соединение с Socket.IO сервером разорвано:', reason);
            if (reason === 'io server disconnect') {
                console.log('🔄 Пытаемся переподключиться...');
                this.socket.connect();
            }
        });

        this.socket.on('reconnect', () => {
            console.log('✅ Переподключение к Socket.IO серверу успешно');
        });

        this.socket.on('reconnect_error', (error) => {
            console.error('⚠ Ошибка переподключения:', error);
        });
    },

    _showConnectionError(message = 'Проблемы с подключением к Socket.IO серверу...') {
        const notification = document.createElement('div');
        notification.className = 'connection-error';
        notification.innerHTML = `
            <strong>Ошибка подключения</strong><br>
            ${message}<br>
            <small>Проверьте что Socket.IO сервер запущен на порту 3001</small>
        `;
        notification.style.cssText = `
            position: fixed;
            top: 20px;
            left: 50%;
            transform: translateX(-50%);
            background: var(--danger);
            color: white;
            padding: 15px 25px;
            border-radius: 8px;
            z-index: 10000;
            animation: fadeIn 0.3s ease;
            text-align: center;
            max-width: 400px;
            box-shadow: 0 4px 16px rgba(0, 0, 0, 0.3);
        `;

        document.body.appendChild(notification);

        setTimeout(() => {
            if (notification.parentNode) {
                notification.remove();
            }
        }, 10000);

        notification.addEventListener('click', () => {
            notification.remove();
        });
    },

    destroy() {
        if (this.socket) {
            this.socket.disconnect();
            this.socket.removeAllListeners();
        }
        this._elementCache.clear();
        this.isInitialized = false;
        console.log('🧹 AppState очищено');
    }
};
