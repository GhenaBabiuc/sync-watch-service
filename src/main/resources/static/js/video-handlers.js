window.VideoHandlers = {
    init() {
        this.setupVideoEventListeners();
    },

    setupVideoEventListeners() {
        const {videoPlayer} = AppState.elements;

        videoPlayer.addEventListener('play', () => {
            if (!AppState.isSyncing && AppState.currentRoomId) {
                const currentTime = videoPlayer.currentTime;
                AppState.socket.emit('play', currentTime);
            }
        });

        videoPlayer.addEventListener('pause', () => {
            if (!AppState.isSyncing && AppState.currentRoomId) {
                const currentTime = videoPlayer.currentTime;
                AppState.socket.emit('pause', currentTime);
            }
        });

        videoPlayer.addEventListener('seeked', () => {
            if (!AppState.isSyncing && AppState.currentRoomId) {
                const currentTime = videoPlayer.currentTime;
                if (Math.abs(currentTime - AppState.lastSeekTime) > AppState.seekThreshold) {
                    AppState.lastSeekTime = currentTime;
                    AppState.socket.emit('seek', currentTime);
                }
            }
        });

        videoPlayer.addEventListener('timeupdate', () => {
            if (AppState.currentRoomId) {
                const currentTime = videoPlayer.currentTime;
                AppState.socket.emit('update-time', currentTime);
            }
        });
    }
};
