/**
 * Cypher Interactive Holographic Core Engine
 * Render Modes:
 * 1. IDLE: Rotating 3D Blue Particle Sphere & Orbital Latitude Rings.
 * 2. SPEAKING: Dynamic outward breaking-square holographic grid expansion.
 */

const canvas = document.getElementById('hologramCanvas');
const ctx = canvas.getContext('2d');

let isSpeaking = false;
let rotationAngle = 0;
let pulseTimer = 0;
let breakingSquares = [];

function initCanvas() {
  const dpr = window.devicePixelRatio || 1;
  canvas.width = 320 * dpr;
  canvas.height = 320 * dpr;
  ctx.scale(dpr, dpr);
}

// Generate floating 3D sphere points
const NUM_PARTICLES = 140;
const particles = [];
const SPHERE_RADIUS = 75;

for (let i = 0; i < NUM_PARTICLES; i++) {
  const theta = Math.acos(2 * Math.random() - 1);
  const phi = 2 * Math.PI * Math.random();
  particles.push({
    x: SPHERE_RADIUS * Math.sin(theta) * Math.cos(phi),
    y: SPHERE_RADIUS * Math.sin(theta) * Math.sin(phi),
    z: SPHERE_RADIUS * Math.cos(theta),
  });
}

function spawnBreakingSquare() {
  breakingSquares.push({
    size: 20,
    maxSize: 130 + Math.random() * 20,
    alpha: 1.0,
    rot: Math.random() * Math.PI,
    rotSpeed: (Math.random() - 0.5) * 0.08,
  });
}

function render() {
  ctx.clearRect(0, 0, 320, 320);
  const cx = 160;
  const cy = 160;

  rotationAngle += isSpeaking ? 0.04 : 0.015;
  pulseTimer += 0.05;

  const cosR = Math.cos(rotationAngle);
  const sinR = Math.sin(rotationAngle);

  // If Cy is speaking, spawn breaking square pulses outward
  if (isSpeaking && Math.random() < 0.25) {
    spawnBreakingSquare();
  }

  // Draw Outward Breaking Squares (Speaking Motion)
  for (let i = breakingSquares.length - 1; i >= 0; i--) {
    const sq = breakingSquares[i];
    sq.size += 4;
    sq.alpha -= 0.025;
    sq.rot += sq.rotSpeed;

    if (sq.alpha <= 0 || sq.size >= sq.maxSize) {
      breakingSquares.splice(i, 1);
      continue;
    }

    ctx.save();
    ctx.translate(cx, cy);
    ctx.rotate(sq.rot);
    ctx.strokeStyle = `rgba(0, 240, 255, ${sq.alpha})`;
    ctx.lineWidth = 2;

    // Draw broken/dashed square
    const half = sq.size / 2;
    ctx.setLineDash([8, 12]);
    ctx.strokeRect(-half, -half, sq.size, sq.size);
    ctx.setLineDash([]);
    ctx.restore();
  }

  // Draw 3D Blue Holographic Sphere Particles
  for (let i = 0; i < particles.length; i++) {
    const p = particles[i];

    // Y-axis 3D rotation
    const rx = p.x * cosR - p.z * sinR;
    const rz = p.x * sinR + p.z * cosR;
    const ry = p.y;

    const scale = 220 / (220 + rz);
    const projX = cx + rx * scale;
    const projY = cy + ry * scale;
    const particleAlpha = Math.max(0.1, (rz + SPHERE_RADIUS) / (SPHERE_RADIUS * 2));

    ctx.beginPath();
    ctx.arc(projX, projY, 2.5 * scale, 0, Math.PI * 2);
    ctx.fillStyle = `rgba(0, 229, 255, ${particleAlpha})`;
    ctx.shadowBlur = isSpeaking ? 12 : 6;
    ctx.shadowColor = '#00f0ff';
    ctx.fill();
    ctx.shadowBlur = 0;
  }

  // Central Hologram Core Glow
  const glowRadius = 45 + Math.sin(pulseTimer) * (isSpeaking ? 12 : 4);
  const grad = ctx.createRadialGradient(cx, cy, 5, cx, cy, glowRadius);
  grad.addColorStop(0, 'rgba(0, 240, 255, 0.8)');
  grad.addColorStop(0.5, 'rgba(0, 136, 255, 0.3)');
  grad.addColorStop(1, 'rgba(0, 136, 255, 0)');

  ctx.beginPath();
  ctx.arc(cx, cy, glowRadius, 0, Math.PI * 2);
  ctx.fillStyle = grad;
  ctx.fill();

  requestAnimationFrame(render);
}

window.setHologramSpeaking = function (speaking) {
  isSpeaking = speaking;
  const statusEl = document.getElementById('statusText');
  if (statusEl) {
    statusEl.innerText = speaking ? 'SPEAKING' : 'SYSTEM IDLE';
    statusEl.style.color = speaking ? '#00f0ff' : '#81a1c1';
  }
};

window.addEventListener('load', () => {
  initCanvas();
  render();
});
