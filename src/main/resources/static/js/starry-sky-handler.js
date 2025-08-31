window.StarrySkyHandler = {
    canvas: null,
    ctx: null,
    stars: [],
    shootingStars: [],
    animationId: null,
    isActive: false,

    settings: {
        starCount: 300,
        speed: 2,
        starSize: 1,
        shootingEnabled: true,
        twinkleEnabled: true
    },

    init() {
        this.createCanvas();
        this.bindEvents();
    },

    createCanvas() {
        const existingCanvas = document.getElementById('starCanvas');
        if (existingCanvas) {
            existingCanvas.remove();
        }

        this.canvas = document.createElement('canvas');
        this.canvas.id = 'starCanvas';
        this.canvas.style.cssText = `
            position: fixed;
            top: 0;
            left: 0;
            z-index: -1;
            pointer-events: none;
        `;

        document.body.appendChild(this.canvas);
        this.ctx = this.canvas.getContext('2d');
        this.resizeCanvas();
    },

    createControls() {
    },

    bindEvents() {
        window.addEventListener('resize', () => {
            this.resizeCanvas();
            this.initStars();
        });
    },

    resizeCanvas() {
        if (!this.canvas) return;
        this.canvas.width = window.innerWidth;
        this.canvas.height = window.innerHeight;
    },

    createStar() {
        const self = this;
        const star = {
            x: Math.random() * this.canvas.width,
            y: Math.random() * this.canvas.height,
            z: Math.random() * 1000,
            size: Math.random() * 2 + 0.5,
            twinkle: Math.random() * Math.PI * 2,
            twinkleSpeed: Math.random() * 0.02 + 0.005,
            color: this.getRandomStarColor(),
            pulsePhase: Math.random() * Math.PI * 2,
            pulseSpeed: Math.random() * 0.02 + 0.005,

            update() {
                this.z -= self.settings.speed * 0.5;
                if (this.z <= 0) {
                    this.z = 1000;
                    this.x = Math.random() * self.canvas.width;
                    this.y = Math.random() * self.canvas.height;
                }

                if (self.settings.twinkleEnabled) {
                    this.twinkle += this.twinkleSpeed;
                }

                this.pulsePhase += this.pulseSpeed;
            },

            draw() {
                const x = (this.x - self.canvas.width / 2) * (1000 / this.z) + self.canvas.width / 2;
                const y = (this.y - self.canvas.height / 2) * (1000 / this.z) + self.canvas.height / 2;
                const size = (1000 / this.z) * this.size * self.settings.starSize;

                if (x < -10 || x > self.canvas.width + 10 || y < -10 || y > self.canvas.height + 10) return;

                let alpha = 0.8;
                if (self.settings.twinkleEnabled) {
                    alpha = 0.3 + 0.5 * Math.sin(this.twinkle);
                }

                const pulseSize = size * (0.8 + 0.3 * Math.sin(this.pulsePhase));

                self.ctx.save();
                self.ctx.globalAlpha = alpha;
                self.ctx.fillStyle = this.color;
                self.ctx.shadowColor = this.color;
                self.ctx.shadowBlur = size;

                self.ctx.beginPath();
                self.ctx.arc(x, y, pulseSize, 0, Math.PI * 2);
                self.ctx.fill();

                self.ctx.globalAlpha = alpha * 0.6;
                self.ctx.strokeStyle = this.color;
                self.ctx.lineWidth = 0.5;
                self.ctx.beginPath();
                self.ctx.moveTo(x - pulseSize * 2, y);
                self.ctx.lineTo(x + pulseSize * 2, y);
                self.ctx.moveTo(x, y - pulseSize * 2);
                self.ctx.lineTo(x, y + pulseSize * 2);
                self.ctx.stroke();

                self.ctx.restore();
            }
        };
        return star;
    },

    createShootingStar() {
        const self = this;
        const shootingStar = {
            x: Math.random() * this.canvas.width * 1.5 - this.canvas.width * 0.5,
            y: Math.random() * this.canvas.height * 0.3,
            length: Math.random() * 60 + 20,
            speed: Math.random() * 8 + 4,
            angle: Math.random() * Math.PI / 8 + Math.PI / 4,
            opacity: 1,
            trail: [],

            update() {
                this.x += Math.cos(this.angle) * this.speed * self.settings.speed;
                this.y += Math.sin(this.angle) * this.speed * self.settings.speed;

                this.trail.push({x: this.x, y: this.y});
                if (this.trail.length > this.length / 3) {
                    this.trail.shift();
                }

                if (this.x > self.canvas.width * 0.7 || this.y > self.canvas.height * 0.7) {
                    this.opacity -= 0.015;
                }
            },

            draw() {
                if (this.opacity <= 0) return;

                self.ctx.save();

                if (this.trail.length > 1) {
                    for (let i = 1; i < this.trail.length; i++) {
                        const alpha = (i / this.trail.length) * this.opacity * 0.8;
                        const width = alpha * 2;

                        self.ctx.strokeStyle = `rgba(255, 255, 255, ${alpha})`;
                        self.ctx.lineWidth = width;
                        self.ctx.lineCap = 'round';

                        self.ctx.beginPath();
                        self.ctx.moveTo(this.trail[i - 1].x, this.trail[i - 1].y);
                        self.ctx.lineTo(this.trail[i].x, this.trail[i].y);
                        self.ctx.stroke();
                    }
                }

                self.ctx.fillStyle = `rgba(255, 255, 255, ${this.opacity})`;
                self.ctx.shadowColor = '#ffffff';
                self.ctx.shadowBlur = 8;
                self.ctx.beginPath();
                self.ctx.arc(this.x, this.y, 2, 0, Math.PI * 2);
                self.ctx.fill();

                self.ctx.restore();
            },

            isDead() {
                return this.opacity <= 0 || this.x > self.canvas.width + 100 || this.y > self.canvas.height + 100;
            }
        };
        return shootingStar;
    },

    getRandomStarColor() {
        const colors = [
            '#ffffff',
            '#ffffcc',
            '#ccccff',
            '#ffcccc',
            '#ccffcc',
            '#fff8dc',
            '#f0f8ff'
        ];
        return colors[Math.floor(Math.random() * colors.length)];
    },

    initStars() {
        this.stars = [];
        for (let i = 0; i < this.settings.starCount; i++) {
            this.stars.push(this.createStar());
        }
    },

    createShootingStarIfNeeded() {
        if (this.settings.shootingEnabled && Math.random() < 0.002) {
            this.shootingStars.push(this.createShootingStar());
        }
    },

    animate() {
        if (!this.isActive || !this.canvas || !this.ctx) return;

        const gradient = this.ctx.createLinearGradient(0, 0, 0, this.canvas.height);
        gradient.addColorStop(0, '#0c0c0c');
        gradient.addColorStop(0.3, '#1a1a2e');
        gradient.addColorStop(0.7, '#16213e');
        gradient.addColorStop(1, '#0c1445');

        this.ctx.fillStyle = gradient;
        this.ctx.fillRect(0, 0, this.canvas.width, this.canvas.height);

        this.stars.forEach(star => {
            star.update();
            star.draw();
        });

        this.createShootingStarIfNeeded();
        this.shootingStars = this.shootingStars.filter(shootingStar => {
            shootingStar.update();
            shootingStar.draw();
            return !shootingStar.isDead();
        });

        this.animationId = requestAnimationFrame(() => this.animate());
    },

    start() {
        if (this.isActive) return;

        this.isActive = true;
        this.createCanvas();
        this.bindEvents();
        this.initStars();
        this.animate();
    },

    stop() {
        this.isActive = false;

        if (this.animationId) {
            cancelAnimationFrame(this.animationId);
            this.animationId = null;
        }

        const canvas = document.getElementById('starCanvas');
        if (canvas) {
            canvas.remove();
        }
    },

    destroy() {
        this.stop();
        this.stars = [];
        this.shootingStars = [];
        this.canvas = null;
        this.ctx = null;
    }
};
