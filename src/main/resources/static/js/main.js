document.addEventListener('DOMContentLoaded', () => {
    AppState.init();
    ThemeManager.init();
    UIHandlers.init();
    SocketHandlers.init();
    VideoHandlers.init();

    if (window.StarrySkyHandler) {
        StarrySkyHandler.init();
    }

    UIHandlers.updateUIState();

    ThemeManager.initializeAfterLoad();
});

window.addEventListener('beforeunload', () => {
    if (window.ThemeManager) {
        ThemeManager.destroy();
    }

    if (window.AppState) {
        AppState.destroy();
    }

    if (window.SocketHandlers) {
        SocketHandlers.destroy();
    }
});

window.addEventListener('error', (event) => {
    console.error('JavaScript Error:', event.error);

    if (event.error && event.error.name !== 'NetworkError') {
        const notification = document.createElement('div');
        notification.className = 'error-notification';
        notification.textContent = 'Произошла техническая ошибка. Попробуйте обновить страницу.';
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
            cursor: pointer;
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
    }
});

window.toggleStarryTheme = () => {
    if (window.ThemeManager) {
        if (ThemeManager.getCurrentGenre() === 'starry') {
            ThemeManager.deactivateStarryTheme();
            console.log('Звездная тема деактивирована');
        } else {
            ThemeManager.activateStarryTheme();
            console.log('Звездная тема активирована');
        }
    }
};
