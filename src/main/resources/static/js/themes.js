window.ThemeManager = {
    _dropdownVisible: false,
    _currentTheme: 'ocean',
    _currentGenre: 'starry',

    init() {
        this.loadSavedSettings();
        this.setupEventListeners();
        this.applyTheme();
        this.applyGenre();
    },

    setupEventListeners() {
        const themeToggle = document.getElementById('theme-toggle');
        const dropdown = document.getElementById('theme-dropdown');

        themeToggle?.addEventListener('click', (e) => {
            e.stopPropagation();
            this.toggleDropdown();
        });

        document.addEventListener('click', (e) => {
            if (!dropdown?.contains(e.target) && !themeToggle?.contains(e.target)) {
                this.hideDropdown();
            }
        });

        document.querySelectorAll('.theme-option').forEach(option => {
            option.addEventListener('click', () => {
                const theme = option.dataset.theme;
                this.setTheme(theme);
            });
        });

        document.querySelectorAll('.genre-option').forEach(option => {
            option.addEventListener('click', () => {
                const genre = option.dataset.genre;
                this.setGenre(genre);
            });
        });
    },

    toggleDropdown() {
        this._dropdownVisible = !this._dropdownVisible;
        const dropdown = document.getElementById('theme-dropdown');

        if (dropdown) {
            dropdown.classList.toggle('active', this._dropdownVisible);
        }
    },

    hideDropdown() {
        this._dropdownVisible = false;
        const dropdown = document.getElementById('theme-dropdown');

        if (dropdown) {
            dropdown.classList.remove('active');
        }
    },

    setTheme(theme) {
        this._currentTheme = theme;
        this.applyTheme();
        this.saveSettings();
        this.updateActiveStates();
    },

    setGenre(genre) {
        if (this._currentGenre === 'starry' && window.StarrySkyHandler) {
            window.StarrySkyHandler.stop();
        }

        this._currentGenre = genre;
        this.applyGenre();
        this.saveSettings();
        this.updateActiveStates();

        if (genre === 'starry' && window.StarrySkyHandler) {
            window.StarrySkyHandler.start();
        }
    },

    applyTheme() {
        document.documentElement.setAttribute('data-theme', this._currentTheme);
    },

    applyGenre() {
        const genreAttributes = ['scifi', 'starry'];
        genreAttributes.forEach(genre => {
            document.documentElement.removeAttribute(`data-genre`);
        });

        if (this._currentGenre !== 'none') {
            document.documentElement.setAttribute('data-genre', this._currentGenre);
        }
    },

    updateActiveStates() {
        document.querySelectorAll('.theme-option').forEach(option => {
            const isActive = option.dataset.theme === this._currentTheme;
            option.classList.toggle('active', isActive);
        });

        document.querySelectorAll('.genre-option').forEach(option => {
            const isActive = option.dataset.genre === this._currentGenre;
            option.classList.toggle('active', isActive);
        });
    },

    saveSettings() {
        try {
            const settings = {
                theme: this._currentTheme,
                genre: this._currentGenre,
                timestamp: Date.now()
            };
            localStorage.setItem('videoSync_themeSettings', JSON.stringify(settings));
        } catch (error) {
            console.warn('Не удалось сохранить настройки темы:', error);
        }
    },

    loadSavedSettings() {
        try {
            const saved = localStorage.getItem('videoSync_themeSettings');
            if (saved) {
                const settings = JSON.parse(saved);

                const maxAge = 7 * 24 * 60 * 60 * 1000;
                if (settings.timestamp && (Date.now() - settings.timestamp) < maxAge) {
                    this._currentTheme = settings.theme || 'ocean';
                    this._currentGenre = settings.genre || 'starry';
                }
            }
        } catch (error) {
            console.warn('Не удалось загрузить сохраненные настройки темы:', error);
            this._currentTheme = 'ocean';
            this._currentGenre = 'starry';
        }
    },

    getCurrentTheme() {
        return this._currentTheme;
    },

    getCurrentGenre() {
        return this._currentGenre;
    },

    isStarryActive() {
        return this._currentGenre === 'starry';
    },

    activateStarryTheme() {
        this.setGenre('starry');
    },

    deactivateStarryTheme() {
        if (this._currentGenre === 'starry') {
            this.setGenre('scifi');
        }
    },

    initializeAfterLoad() {
        this.updateActiveStates();

        if (this._currentGenre === 'starry' && window.StarrySkyHandler) {
            setTimeout(() => {
                window.StarrySkyHandler.start();
            }, 100);
        }
    },

    destroy() {
        if (window.StarrySkyHandler) {
            window.StarrySkyHandler.destroy();
        }
    }
};
