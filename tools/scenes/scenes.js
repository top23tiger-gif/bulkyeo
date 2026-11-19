// 앱 배경 그림(1080x1920 세로)을 SVG로 그리는 스크립트.
// 실행: node tools/scenes/render.js  →  app/src/main/assets/images/*.jpg 생성
// 화면 아래쪽 1/3은 자막이 올라가므로 주요 그림은 위쪽~가운데에 둔다.

const W = 1080, H = 1920;

const rect = (x, y, w, h, fill, rx = 0, extra = '') =>
  `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${rx}" fill="${fill}" ${extra}/>`;
const circle = (cx, cy, r, fill, extra = '') => `<circle cx="${cx}" cy="${cy}" r="${r}" fill="${fill}" ${extra}/>`;
const ellipse = (cx, cy, rx, ry, fill, extra = '') =>
  `<ellipse cx="${cx}" cy="${cy}" rx="${rx}" ry="${ry}" fill="${fill}" ${extra}/>`;
const path = (d, fill, extra = '') => `<path d="${d}" fill="${fill}" ${extra}/>`;
const text = (x, y, s, size, fill, extra = '') =>
  `<text x="${x}" y="${y}" font-family="DejaVu Sans, Arial, sans-serif" font-weight="bold" font-size="${size}" fill="${fill}" text-anchor="middle" ${extra}>${s}</text>`;

function grad(id, stops, vertical = true) {
  const dir = vertical ? 'x1="0" y1="0" x2="0" y2="1"' : 'x1="0" y1="0" x2="1" y2="0"';
  return `<linearGradient id="${id}" ${dir}>${stops
    .map(([o, c]) => `<stop offset="${o}" stop-color="${c}"/>`)
    .join('')}</linearGradient>`;
}

// 상반신 인물. (x, y) = 머리 중심
function person({ x, y, s = 1, skin = '#f6c9a4', hair = '#3b2a20', shirt = '#e07a5f', style = 'short', mouth = 'smile', arms = true, flip = false, legs = null }) {
  const r = 62;
  let hairBack = '', hairFront = '';
  if (style === 'long') {
    hairBack = path(`M${-r - 8} 0 Q${-r - 14} 120 ${-r + 4} 170 L${r - 4} 170 Q${r + 14} 120 ${r + 8} 0 Z`, hair);
    hairFront = path(`M${-r - 6} 6 Q${-r} ${-r - 20} 0 ${-r - 10} Q${r} ${-r - 20} ${r + 6} 6 Q${r - 10} -40 ${10} -38 Q-30 -20 ${-r - 6} 6 Z`, hair);
  } else if (style === 'bob') {
    hairBack = path(`M${-r - 10} -10 Q${-r - 16} 70 ${-r - 2} 90 L${r + 2} 90 Q${r + 16} 70 ${r + 10} -10 Z`, hair);
    hairFront = path(`M${-r - 8} 10 Q${-r - 4} ${-r - 22} 0 ${-r - 12} Q${r + 4} ${-r - 22} ${r + 8} 10 Q${r - 4} -34 ${r - 40} -36 Q0 -28 ${-r + 30} -38 Q${-r + 2} -30 ${-r - 8} 10 Z`, hair);
  } else if (style === 'bun') {
    hairBack = circle(0, -r - 22, 32, hair);
    hairFront = path(`M${-r - 4} 4 Q${-r} ${-r - 16} 0 ${-r - 8} Q${r} ${-r - 16} ${r + 4} 4 Q${r - 14} -36 0 -34 Q${-r + 14} -36 ${-r - 4} 4 Z`, hair);
  } else if (style === 'bald') {
    hairFront = path(`M${-r - 2} 0 Q${-r - 4} -30 ${-r + 10} -34 L${-r + 14} 10 Z M${r + 2} 0 Q${r + 4} -30 ${r - 10} -34 L${r - 14} 10 Z`, hair);
  } else {
    hairFront = path(`M${-r - 4} 0 Q${-r - 6} ${-r - 24} 0 ${-r - 12} Q${r + 8} ${-r - 22} ${r + 4} 0 Q${r - 6} -30 ${r - 30} -32 Q0 -50 ${-r + 20} -30 Q${-r + 6} -24 ${-r - 4} 0 Z`, hair);
  }
  const m = mouth === 'open'
    ? ellipse(0, 26, 14, 11, '#a3423a')
    : path('M-18 20 Q0 38 18 20', 'none', 'stroke="#7a3b2e" stroke-width="5" stroke-linecap="round"');
  const body =
    path(`M-95 300 Q-100 120 -40 92 L40 92 Q100 120 95 300 Z`, shirt) +
    rect(-16, 55, 32, 42, skin, 10);
  const arm = arms
    ? path('M-92 160 Q-130 240 -110 300 L-80 300 Q-90 240 -70 175 Z', shirt, 'opacity="0.92"') +
      path('M92 160 Q130 240 110 300 L80 300 Q90 240 70 175 Z', shirt, 'opacity="0.92"')
    : '';
  const face =
    circle(0, 0, r, skin) +
    circle(-22, -4, 7, '#2d2220') +
    circle(22, -4, 7, '#2d2220') +
    circle(-19, -7, 2.4, '#fff') +
    circle(25, -7, 2.4, '#fff') +
    ellipse(-38, 16, 11, 7, '#f29a8a', 'opacity="0.55"') +
    ellipse(38, 16, 11, 7, '#f29a8a', 'opacity="0.55"') +
    m;
  const legPart = legs
    ? rect(-70, 290, 62, 300, legs, 14) + rect(8, 290, 62, 300, legs, 14) +
      ellipse(-44, 595, 46, 20, '#3a2e2a') + ellipse(44, 595, 46, 20, '#3a2e2a')
    : '';
  const t = `translate(${x} ${y}) scale(${flip ? -s : s} ${s})`;
  return `<g transform="${t}">${legPart}${hairBack}${body}${arm}${face}${hairFront}</g>`;
}

function plant(x, y, s = 1) {
  return `<g transform="translate(${x} ${y}) scale(${s})">
    ${path('M0 0 Q-60 -80 -20 -170 Q0 -90 0 0Z', '#5f9e6e')}
    ${path('M0 0 Q60 -90 30 -190 Q5 -100 0 0Z', '#4f8a5f')}
    ${path('M0 0 Q-90 -40 -110 -110 Q-30 -70 0 0Z', '#6fb07c')}
    ${path('M0 0 Q90 -30 115 -95 Q30 -60 0 0Z', '#5f9e6e')}
    ${path('M-45 0 L45 0 L35 80 L-35 80 Z', '#c0704d')}
  </g>`;
}

function cloud(x, y, s = 1, fill = '#ffffff', op = 0.95) {
  return `<g transform="translate(${x} ${y}) scale(${s})" opacity="${op}">
    ${ellipse(0, 0, 90, 45, fill)}${circle(-45, -20, 45, fill)}${circle(25, -35, 55, fill)}${circle(70, -5, 40, fill)}
  </g>`;
}

function tree(x, y, s = 1, c1 = '#4f9a5d', c2 = '#3d8350') {
  return `<g transform="translate(${x} ${y}) scale(${s})">
    ${rect(-18, -40, 36, 200, '#7a5236', 8)}
    ${circle(0, -120, 110, c1)}${circle(-80, -60, 80, c2)}${circle(80, -60, 85, c2)}${circle(0, -40, 90, c1)}
  </g>`;
}

function shadeBottom() {
  // 자막이 잘 보이도록 아래쪽을 살짝 어둡게
  return `<defs>${grad('shade', [[0, 'rgba(0,0,0,0)'], [1, 'rgba(0,0,0,0.55)']])}</defs>${rect(0, 1250, W, 670, 'url(#shade)')}`;
}

const scenes = {
  cafe() {
    return `
    <defs>${grad('cafeWall', [[0, '#f3d9b8'], [1, '#e7c39a']])}${grad('counter', [[0, '#8a5a3b'], [1, '#6b4429']])}</defs>
    ${rect(0, 0, W, H, 'url(#cafeWall)')}
    ${rect(0, 0, W, 90, '#7b4f33')}
    <!-- 칠판 메뉴판 -->
    ${rect(150, 170, 780, 420, '#2f3b36', 24, 'stroke="#8a5a3b" stroke-width="18"')}
    ${text(540, 255, 'MENU', 64, '#fdf6e3')}
    ${[0, 1, 2, 3].map(i => rect(230, 300 + i * 66, 360, 12, '#cfd8d3', 6, 'opacity="0.8"') + rect(720, 300 + i * 66, 120, 12, '#f2cc8f', 6)).join('')}
    ${circle(860, 230, 28, '#f2cc8f')}
    <!-- 조명 -->
    ${[270, 540, 810].map(x => rect(x - 3, 90, 6, 550, '#5a3a26') + path(`M${x - 55} 640 Q${x} 560 ${x + 55} 640 Z`, '#e07a5f') + ellipse(x, 642, 30, 10, '#fff3c4')).join('')}
    <!-- 바리스타 -->
    ${person({ x: 360, y: 860, s: 1.25, hair: '#4a3020', shirt: '#2f6f6a', style: 'short' })}
    ${path('M248 1090 L472 1090 L455 1240 L265 1240 Z', '#f4efe6')}
    <!-- 커피 머신 -->
    ${rect(640, 820, 300, 330, '#b9c2c9', 26)}${rect(670, 850, 240, 90, '#8e989f', 14)}
    ${circle(720, 895, 22, '#e07a5f')}${circle(790, 895, 22, '#81b29a')}
    ${rect(700, 990, 50, 60, '#555', 8)}${rect(830, 990, 50, 60, '#555', 8)}
    <!-- 카운터 -->
    ${rect(0, 1150, W, 770, 'url(#counter)')}${rect(0, 1130, W, 40, '#a8714c')}
    <!-- 라떼 -->
    ${ellipse(560, 1120, 150, 26, '#d9cbb8')}
    ${path('M430 1000 L690 1000 L660 1110 Q560 1135 460 1110 Z', '#ffffff')}
    ${path('M690 1020 Q760 1020 755 1060 Q750 1095 675 1090', 'none', 'stroke="#ffffff" stroke-width="20"')}
    ${ellipse(560, 1000, 130, 26, '#c08552')}
    ${path('M560 1018 Q520 985 538 975 Q552 970 560 984 Q568 970 582 975 Q600 985 560 1018 Z', '#f8ecdc')}
    ${[0, 1, 2].map(i => path(`M${520 + i * 40} 950 Q${505 + i * 40} 910 ${525 + i * 40} 880 Q${545 + i * 40} 850 ${528 + i * 40} 815`, 'none', 'stroke="#ffffff" stroke-width="8" stroke-linecap="round" opacity="0.7"')).join('')}
    ${plant(100, 1050, 1.2)}
    ${shadeBottom()}`;
  },

  airport() {
    return `
    <defs>${grad('sky', [[0, '#7cc3f0'], [1, '#d6eefb']])}${grad('floor', [[0, '#d7dde3'], [1, '#aab4bd']])}</defs>
    ${rect(0, 0, W, H, '#e9eef2')}
    <!-- 큰 창문 -->
    ${rect(40, 120, 1000, 760, 'url(#sky)', 12)}
    ${cloud(260, 300, 1)}${cloud(820, 240, 0.8)}${cloud(620, 520, 0.6)}
    <!-- 비행기 -->
    <g transform="translate(560 430) rotate(-12)">
      ${ellipse(0, 0, 260, 42, '#ffffff')}
      ${path('M-40 0 L-150 -170 L-100 -170 L60 0 Z', '#e8edf2')}
      ${path('M-40 10 L-130 150 L-85 150 L50 10 Z', '#dfe5ea')}
      ${path('M-230 -10 L-280 -110 L-240 -110 L-190 -10 Z', '#e07a5f')}
      ${ellipse(220, -8, 30, 16, '#4a6fa5')}
      ${[0, 1, 2, 3, 4, 5].map(i => circle(-120 + i * 52, -6, 9, '#4a6fa5')).join('')}
    </g>
    ${[290, 540, 790].map(x => rect(x - 9, 120, 18, 760, '#5b6770')).join('')}
    ${rect(40, 860, 1000, 26, '#5b6770')}
    <!-- 출발 안내판 -->
    ${rect(400, 930, 640, 300, '#1e2a33', 20)}
    ${text(600, 990, 'DEPARTURES', 40, '#f2cc8f')}
    ${['NEW YORK   12   ON TIME', 'LONDON     07   BOARDING', 'SYDNEY     21   DELAYED'].map((s, i) =>
      `<text x="425" y="${1060 + i * 60}" font-family="DejaVu Sans Mono, monospace" font-weight="bold" font-size="38" fill="${i === 2 ? '#ff9f80' : '#9fe0b0'}" xml:space="preserve">${s}</text>`).join('')}
    ${rect(0, 1250, W, 670, 'url(#floor)')}
    <!-- 여행객과 캐리어 -->
    ${person({ x: 200, y: 1080, s: 1.1, hair: '#2b2b2b', shirt: '#f2a541', style: 'long', legs: '#3d5a80' })}
    ${rect(760, 1420, 230, 320, '#e07a5f', 30)}${rect(840, 1350, 70, 80, 'none', 16, 'stroke="#2c3e57" stroke-width="16"')}
    ${rect(795, 1470, 160, 16, '#f4d6c6', 8)}${rect(795, 1560, 160, 16, '#f4d6c6', 8)}
    ${circle(800, 1745, 20, '#222')}${circle(950, 1745, 20, '#222')}
    ${shadeBottom()}`;
  },

  restaurant() {
    return `
    <defs>${grad('night', [[0, '#1f2a4a'], [1, '#3b3f6b']])}${grad('rwall', [[0, '#8c3b3b'], [1, '#6e2c2c']])}</defs>
    ${rect(0, 0, W, H, 'url(#rwall)')}
    <!-- 창문 밖 야경 -->
    ${rect(110, 140, 860, 620, 'url(#night)', 16, 'stroke="#d9a75b" stroke-width="20"')}
    ${circle(820, 260, 50, '#fdf1c7')}
    ${[[160, 520, 120, 240], [300, 460, 100, 300], [420, 560, 140, 200], [580, 430, 110, 330], [710, 520, 130, 240], [850, 480, 100, 280]].map(([x, y, w, h]) =>
      rect(x, y, w, h, '#2a2f52') + [0, 1, 2, 3].map(r => [0, 1].map(c => rect(x + 18 + c * 50, y + 30 + r * 55, 26, 30, ((x + r * 7 + c * 3) % 5) > 1 ? '#f6d57a' : '#3d4470')).join('')).join('')).join('')}
    ${rect(530, 140, 20, 620, '#d9a75b')}
    <!-- 웨이터 -->
    ${person({ x: 800, y: 900, s: 1.1, hair: '#1d1d1d', shirt: '#222831', style: 'short' })}
    ${path('M770 955 L830 955 L800 1000 Z', '#ffffff')}${path('M790 965 L810 965 L800 1040 Z', '#b23a48')}
    ${rect(640, 1020, 150, 200, '#4a2c1a', 10, 'transform="rotate(-10 715 1120)"')}${text(712, 1130, 'MENU', 28, '#f2cc8f', 'transform="rotate(-10 715 1120)"')}
    <!-- 식탁 -->
    ${ellipse(450, 1200, 520, 110, '#f8f4ec')}${rect(-70, 1200, 1040, 720, '#f8f4ec')}
    ${path('M-70 1200 L970 1200 L970 1260 L-70 1260 Z', '#ece5d8')}
    ${ellipse(420, 1180, 190, 60, '#ffffff', 'stroke="#ddd" stroke-width="4"')}
    ${ellipse(420, 1172, 120, 36, '#f2b544')}
    ${[0, 1, 2, 3, 4, 5].map(i => path(`M${340 + i * 30} 1170 q15 -20 30 0 q15 20 30 0`, 'none', 'stroke="#e9a23b" stroke-width="7"')).join('')}
    ${circle(400, 1160, 14, '#c0392b')}${circle(445, 1175, 12, '#c0392b')}${circle(470, 1155, 6, '#4caf50')}
    <!-- 와인잔, 촛불 -->
    ${[130, 700].map(x => path(`M${x - 40} 1000 L${x + 40} 1000 Q${x + 40} 1080 ${x} 1090 Q${x - 40} 1080 ${x - 40} 1000 Z`, 'rgba(255,255,255,0.55)') + path(`M${x - 36} 1040 L${x + 36} 1040 Q${x + 30} 1082 ${x} 1088 Q${x - 30} 1082 ${x - 36} 1040 Z`, '#8e1b2d') + rect(x - 4, 1088, 8, 80, 'rgba(255,255,255,0.7)') + ellipse(x, 1170, 32, 8, 'rgba(255,255,255,0.7)')).join('')}
    ${rect(890, 1060, 40, 110, '#fff8e7', 6)}${path('M910 1060 Q895 1030 910 1000 Q925 1030 910 1060 Z', '#ffb84d')}
    ${circle(910, 1030, 70, '#ffd27a', 'opacity="0.25"')}
    ${shadeBottom()}`;
  },

  street() {
    return `
    <defs>${grad('ssky', [[0, '#9ad1f5'], [1, '#e8f5fd']])}</defs>
    ${rect(0, 0, W, H, 'url(#ssky)')}
    ${cloud(220, 210, 0.9)}${cloud(820, 300, 0.7)}
    <!-- 건물 -->
    ${[[0, 420, 230, '#e8b4a0'], [230, 300, 200, '#f2d0a4'], [430, 460, 220, '#a7c4bc'], [650, 340, 200, '#e9c46a'], [850, 400, 230, '#c9b6d9']].map(([x, y, w, c]) =>
      rect(x, y, w, 1300 - y, c) + rect(x, y, w, 30, 'rgba(0,0,0,0.12)') +
      [0, 1, 2, 3, 4, 5, 6].filter(r => y + 70 + r * 120 < 1100).map(r => [0, 1].map(k => rect(x + 35 + k * (w / 2 - 10), y + 70 + r * 120, w / 2 - 70, 80, '#ffffff', 6, 'opacity="0.75"')).join('')).join('')).join('')}
    <!-- 역 표지판 -->
    ${rect(130, 1000, 18, 420, '#4d5a62')}
    ${path('M60 830 L400 830 L450 885 L400 940 L60 940 Z', '#2a9d8f')}
    ${text(240, 905, 'STATION', 50, '#ffffff')}
    ${circle(118, 885, 0, '#fff')}
    <!-- 인도와 도로 -->
    ${rect(0, 1250, W, 200, '#cfc8bd')}${rect(0, 1440, W, 480, '#5d636a')}
    ${[0, 1, 2, 3, 4].map(i => rect(40 + i * 230, 1660, 140, 24, '#f4f1de', 6)).join('')}
    <!-- 지도를 든 사람 -->
    ${person({ x: 650, y: 920, s: 1.25, hair: '#5a3825', shirt: '#e76f51', style: 'bob' })}
    <g transform="translate(650 1150) rotate(-6)">
      ${path('M-170 -60 L-60 -90 L60 -60 L170 -90 L170 110 L60 140 L-60 110 L-170 140 Z', '#fdfaf0', 'stroke="#c9c2a8" stroke-width="4"')}
      ${path('M-60 -90 L-60 110 M60 -60 L60 140', 'none', 'stroke="#c9c2a8" stroke-width="4"')}
      ${path('M-140 40 Q-60 -40 20 30 T150 -20', 'none', 'stroke="#e63946" stroke-width="8" stroke-dasharray="16 12"')}
      ${circle(150, -20, 14, '#e63946')}
    </g>
    ${path('M800 700 Q830 650 860 700 Q860 740 830 760 Q800 740 800 700 Z', '#f4a261')}${text(830, 725, '?', 44, '#fff')}
    ${shadeBottom()}`;
  },

  shop() {
    return `
    ${rect(0, 0, W, H, '#f7ede2')}
    ${rect(0, 0, W, 130, '#e5989b')}${text(540, 90, 'BOUTIQUE', 56, '#ffffff')}
    <!-- 옷걸이 봉 -->
    ${rect(60, 260, 580, 16, '#8d99ae', 8)}${rect(80, 260, 14, 640, '#8d99ae')}${rect(606, 260, 14, 640, '#8d99ae')}
    ${['#e76f51', '#2a9d8f', '#f4a261', '#264653', '#e9c46a', '#b5838d'].map((c, i) => {
      const x = 130 + i * 82;
      return path(`M${x} 268 q12 -30 24 0`, 'none', 'stroke="#6c757d" stroke-width="5"') +
        path(`M${x - 50} 300 L${x + 12} 280 L${x + 74} 300 L${x + 64} 620 L${x - 40} 620 Z`, c);
    }).join('')}
    <!-- 거울 -->
    ${ellipse(860, 560, 150, 280, '#d6eaf0', 'stroke="#c9a66b" stroke-width="22"')}
    ${path('M780 420 L840 360 M800 480 L880 400', 'none', 'stroke="#ffffff" stroke-width="12" stroke-linecap="round" opacity="0.8"')}
    <!-- 점원 + 고객 -->
    ${person({ x: 760, y: 1010, s: 1.05, hair: '#2d2d2d', shirt: '#6d597a', style: 'bun', mouth: 'open' })}
    ${person({ x: 330, y: 1030, s: 1.2, hair: '#4a3020', shirt: '#f1faee', style: 'long' })}
    <!-- 들고 있는 옷 -->
    ${path('M200 1190 L280 1160 L360 1190 L350 1400 L210 1400 Z', '#e63946')}
    ${rect(258, 1140, 44, 10, '#6c757d', 5)}
    ${rect(316, 1240, 60, 40, '#fff', 4, 'transform="rotate(12 346 1260)"')}${text(346, 1270, 'S', 28, '#e63946', 'transform="rotate(12 346 1260)"')}
    ${rect(0, 1300, W, 620, '#d4a373')}
    ${[0, 1, 2, 3, 4, 5].map(i => rect(i * 180, 1300, 4, 620, '#c08b5c')).join('')}
    ${shadeBottom()}`;
  },

  hotel() {
    return `
    <defs>${grad('hwall', [[0, '#f1e3c8'], [1, '#e3cfa7']])}${grad('desk', [[0, '#5b3a29'], [1, '#3e271b']])}</defs>
    ${rect(0, 0, W, H, 'url(#hwall)')}
    ${[0, 1, 2, 3, 4, 5].map(i => rect(i * 200 + 60, 0, 6, 1100, '#e0c99a')).join('')}
    ${rect(290, 170, 500, 110, '#3e271b', 14)}${text(540, 245, 'RECEPTION', 54, '#f2cc8f')}
    <!-- 시계들 -->
    ${[[200, 'SEOUL'], [540, 'LONDON'], [880, 'NEW YORK']].map(([x, c], i) =>
      circle(x, 420, 70, '#fffaf0', 'stroke="#b08d57" stroke-width="10"') +
      path(`M${x} 420 L${x} ${370}`, 'none', 'stroke="#333" stroke-width="7" stroke-linecap="round"', ) +
      `<path d="M${x} 420 L${x + 35} ${420 + (i - 1) * 25}" stroke="#333" stroke-width="6" stroke-linecap="round"/>` +
      text(x, 530, c, 26, '#6b4f2a')).join('')}
    <!-- 직원 -->
    ${person({ x: 540, y: 760, s: 1.25, hair: '#2a1f1a', shirt: '#1d3557', style: 'bun' })}
    ${path('M520 830 L560 830 L540 860 Z', '#e63946')}
    ${rect(600, 890, 60, 14, '#f2cc8f', 4)}
    <!-- 데스크 -->
    ${rect(60, 1080, 960, 840, 'url(#desk)', 10)}${rect(40, 1050, 1000, 50, '#7a4e33', 10)}
    ${[0, 1, 2].map(i => rect(110 + i * 310, 1150, 240, 300, 'none', 10, 'stroke="#6e4630" stroke-width="8"')).join('')}
    <!-- 벨, 열쇠, 꽃 -->
    ${ellipse(280, 1045, 80, 14, '#b8860b')}${path('M215 1040 Q215 960 280 960 Q345 960 345 1040 Z', '#e9b949')}${circle(280, 950, 12, '#e9b949')}
    ${path('M245 985 Q255 970 272 968', 'none', 'stroke="#fff6cc" stroke-width="8" stroke-linecap="round"')}
    ${rect(560, 1010, 150, 40, '#ffffff', 8, 'transform="rotate(-8 635 1030)"')}${rect(580, 1018, 40, 24, '#2a9d8f', 4, 'transform="rotate(-8 635 1030)"')}
    ${rect(840, 930, 70, 120, '#a8dadc', 12)}
    ${[[-30, -50, '#e76f51'], [10, -75, '#f4a261'], [40, -40, '#e9c46a'], [-5, -25, '#e5989b']].map(([dx, dy, c]) => circle(875 + dx, 930 + dy, 30, c)).join('')}
    ${shadeBottom()}`;
  },

  kitchen() {
    return `
    <defs>${grad('morning', [[0, '#ffe8b0'], [1, '#fff6dd']])}${grad('sun', [[0, 'rgba(255,236,160,0.75)'], [1, 'rgba(255,236,160,0)']])}</defs>
    ${rect(0, 0, W, H, '#fbefe0')}
    <!-- 창문 -->
    ${rect(90, 140, 520, 520, 'url(#morning)', 14, 'stroke="#ffffff" stroke-width="24"')}
    ${circle(480, 300, 80, '#ffcf56')}${cloud(250, 470, 0.7)}
    ${rect(338, 140, 24, 520, '#ffffff')}${rect(90, 388, 520, 24, '#ffffff')}
    ${path('M90 660 L610 660 L900 1300 L-200 1300 Z', 'url(#sun)')}
    <!-- 선반 -->
    ${rect(680, 300, 330, 22, '#b07d56', 6)}
    ${[700, 790, 880].map((x, i) => rect(x, 220, 70, 80, ['#81b29a', '#e07a5f', '#f2cc8f'][i], 10)).join('')}
    <!-- 우산(문 옆) -->
    ${rect(900, 520, 140, 780, '#d8b48a', 8)}${circle(1010, 900, 10, '#8a6a45')}
    <g transform="translate(860 920) rotate(8)">
      ${path('M0 0 L0 360', 'none', 'stroke="#555" stroke-width="10"')}${path('M0 360 Q0 400 -30 395', 'none', 'stroke="#555" stroke-width="10" stroke-linecap="round"')}
      ${path('M-45 40 Q0 -30 45 40 L20 300 L-20 300 Z', '#e63946')}
    </g>
    <!-- 앞치마 입은 엄마 -->
    ${person({ x: 470, y: 820, s: 1.25, hair: '#3a2a22', shirt: '#f4a6a6', style: 'bob', mouth: 'open' })}
    ${path('M400 960 L540 960 L560 1150 L380 1150 Z', '#ffffff', 'opacity="0.9"')}
    <!-- 식탁 -->
    ${rect(0, 1150, W, 770, '#c48a5c')}${rect(0, 1130, W, 36, '#d9a273')}
    ${ellipse(330, 1110, 150, 32, '#ffffff', 'stroke="#e6e6e6" stroke-width="4"')}
    ${rect(240, 1030, 120, 80, '#e9b872', 18)}${rect(252, 1040, 96, 60, '#f7dca5', 12)}
    ${ellipse(420, 1085, 56, 30, '#ffffff')}${circle(420, 1085, 20, '#ffb703')}
    ${path('M640 990 L760 990 L750 1115 L650 1115 Z', '#5e81ac')}${path('M760 1015 Q810 1020 800 1060 Q790 1090 755 1088', 'none', 'stroke="#5e81ac" stroke-width="16"')}
    ${[0, 1].map(i => path(`M${680 + i * 40} 970 q-15 -35 5 -70`, 'none', 'stroke="#fff" stroke-width="8" stroke-linecap="round" opacity="0.8"')).join('')}
    ${shadeBottom()}`;
  },

  park() {
    return `
    <defs>${grad('psky', [[0, '#8ecae6'], [1, '#e0f4ff']])}</defs>
    ${rect(0, 0, W, H, 'url(#psky)')}
    ${circle(170, 230, 90, '#ffd166')}
    ${cloud(560, 230, 1.1)}${cloud(900, 420, 1.0, '#9aa5b1', 0.95)}${cloud(780, 360, 0.7, '#b8c2cc', 0.9)}
    <!-- 언덕 -->
    ${ellipse(250, 1150, 700, 260, '#95d5b2')}${ellipse(900, 1180, 600, 240, '#74c69d')}
    ${rect(0, 1150, W, 770, '#74c69d')}
    ${tree(130, 980, 1.1)}${tree(920, 950, 1.0, '#52b788', '#40916c')}${tree(560, 900, 0.6, '#52b788', '#40916c')}
    <!-- 산책로 -->
    ${path('M380 1920 Q520 1500 520 1250 Q540 1080 600 1000 L680 1000 Q620 1100 640 1250 Q660 1500 760 1920 Z', '#e9d8a6')}
    <!-- 벤치 -->
    ${rect(80, 1250, 300, 26, '#9c6644', 6)}${rect(80, 1190, 300, 22, '#9c6644', 6)}${rect(100, 1276, 16, 80, '#5a3e2b')}${rect(344, 1276, 16, 80, '#5a3e2b')}
    <!-- 함께 걷는 두 사람 -->
    ${person({ x: 520, y: 900, s: 0.75, hair: '#3d2b1f', shirt: '#ef476f', style: 'long', legs: '#5e548e' })}
    ${person({ x: 680, y: 885, s: 0.78, hair: '#6b6b6b', shirt: '#118ab2', style: 'short', mouth: 'open', legs: '#4a4e69' })}
    ${[[200, 1500], [880, 1450], [300, 1700], [950, 1650]].map(([x, y]) => [0, 72, 144, 216, 288].map(a => ellipse(x + 14 * Math.cos(a * Math.PI / 180), y + 14 * Math.sin(a * Math.PI / 180), 12, 12, '#ffffff')).join('') + circle(x, y, 8, '#ffd166')).join('')}
    ${[[960, 470], [900, 510], [1000, 520]].map(([x, y]) => path(`M${x} ${y} l-14 40`, 'none', 'stroke="#5fa8d3" stroke-width="7" stroke-linecap="round"')).join('')}
    ${shadeBottom()}`;
  },

  phone() {
    return `
    <defs>${grad('lroom', [[0, '#2b2d42'], [1, '#3d405b']])}${grad('screen', [[0, '#bde0fe'], [1, '#a2d2ff']])}</defs>
    ${rect(0, 0, W, H, 'url(#lroom)')}
    <!-- 창밖 별, 달 -->
    ${rect(620, 140, 360, 420, '#1b1d33', 14, 'stroke="#5c5f7d" stroke-width="16"')}
    ${circle(860, 260, 50, '#fdf0c2')}${circle(880, 245, 46, '#1b1d33')}
    ${[[680, 220], [720, 420], [930, 470], [790, 330], [700, 500]].map(([x, y]) => circle(x, y, 5, '#fdf0c2')).join('')}
    <!-- 스탠드 -->
    ${circle(200, 520, 230, '#ffd27a', 'opacity="0.18"')}
    ${path('M120 380 L280 380 L240 480 L160 480 Z', '#f2cc8f')}${rect(194, 480, 12, 520, '#6b705c')}
    <!-- 소파 -->
    ${rect(-40, 1000, 1160, 380, '#7f5539', 60)}${rect(-40, 1180, 1160, 300, '#9c6644', 40)}
    ${rect(80, 1060, 200, 160, '#e5989b', 30, 'transform="rotate(-8 180 1140)"')}
    <!-- 영상통화 중인 큰 휴대폰 -->
    <g transform="translate(540 760) rotate(-4)">
      ${rect(-210, -420, 420, 820, '#1f1f1f', 56)}
      ${rect(-186, -380, 372, 740, 'url(#screen)', 36)}
      ${person({ x: 0, y: -60, s: 1.1, hair: '#3b2a20', shirt: '#ffb703', style: 'short', mouth: 'open' })}
      ${rect(70, -360, 100, 140, '#ffe5ec', 18)}
      ${person({ x: 120, y: -300, s: 0.33, hair: '#4a3a30', shirt: '#e07a5f', style: 'bob', arms: false })}
      ${circle(-70, 300, 36, '#e63946')}${circle(70, 300, 36, '#2a9d8f')}
      ${path('M-82 290 L-58 310 M-58 290 L-82 310', 'none', 'stroke="#fff" stroke-width="7" stroke-linecap="round"')}
      ${rect(-30, -405, 60, 10, '#444', 5)}
    </g>
    <!-- 하트 -->
    ${[[850, 720, 1], [920, 620, 0.6], [180, 760, 0.7]].map(([x, y, s]) =>
      `<path transform="translate(${x} ${y}) scale(${s})" d="M0 30 C-60 -10 -40 -70 0 -40 C40 -70 60 -10 0 30 Z" fill="#ff6b81"/>`).join('')}
    ${shadeBottom()}`;
  },

  party() {
    const balloons = [[160, 380, '#ef476f'], [290, 300, '#ffd166'], [820, 330, '#06d6a0'], [950, 420, '#118ab2'], [700, 260, '#f78c6b']];
    return `
    <defs>${grad('pwall', [[0, '#fff1e6'], [1, '#fde2e4']])}</defs>
    ${rect(0, 0, W, H, 'url(#pwall)')}
    <!-- 가랜드 -->
    ${path('M0 120 Q540 330 1080 120', 'none', 'stroke="#9c6644" stroke-width="5"')}
    ${[...Array(11)].map((_, i) => {
      const t = (i + 0.5) / 11, x = t * 1080, y = 120 + 4 * t * (1 - t) * 210;
      return path(`M${x - 34} ${y} L${x + 34} ${y} L${x} ${y + 80} Z`, ['#ef476f', '#ffd166', '#06d6a0', '#118ab2'][i % 4]);
    }).join('')}
    ${text(540, 530, 'HAPPY BIRTHDAY', 70, '#ef476f')}
    <!-- 풍선 -->
    ${balloons.map(([x, y, c]) => path(`M${x} ${y + 95} q-20 80 10 160 q-25 80 0 160`, 'none', 'stroke="#888" stroke-width="3"') + ellipse(x, y, 75, 95, c) + ellipse(x - 25, y - 35, 15, 25, '#ffffff', 'opacity="0.5"') + path(`M${x - 10} ${y + 92} L${x + 10} ${y + 92} L${x} ${y + 108} Z`, c)).join('')}
    <!-- 케이크 -->
    ${rect(0, 1180, W, 740, '#f6bd60')}${rect(0, 1160, W, 34, '#f7cf86')}
    ${ellipse(330, 1150, 210, 34, '#ffffff')}
    ${rect(170, 930, 320, 220, '#f7a8b8', 20)}${path('M170 970 Q210 1010 250 970 Q290 1010 330 970 Q370 1010 410 970 Q450 1010 490 970 L490 940 L170 940 Z', '#ffffff')}
    ${ellipse(330, 935, 160, 30, '#ffffff')}
    ${[250, 330, 410].map((x, i) => rect(x - 9, 850, 18, 80, ['#118ab2', '#06d6a0', '#ffd166'][i], 6) + path(`M${x} 850 Q${x - 14} 825 ${x} 800 Q${x + 14} 825 ${x} 850 Z`, '#ff9f1c')).join('')}
    ${[0, 1, 2, 3, 4, 5].map(i => circle(200 + i * 52, 1060, 9, ['#ef476f', '#ffd166', '#06d6a0'][i % 3])).join('')}
    <!-- 선물 든 사람 -->
    ${person({ x: 780, y: 820, s: 1.15, hair: '#3d2b1f', shirt: '#8e7dbe', style: 'long', mouth: 'open' })}
    ${rect(670, 1010, 220, 180, '#ef476f', 10)}${rect(765, 1010, 30, 180, '#ffd166')}${rect(670, 1085, 220, 30, '#ffd166')}
    ${ellipse(745, 1000, 40, 22, '#ffd166', 'transform="rotate(-20 745 1000)"')}${ellipse(815, 1000, 40, 22, '#ffd166', 'transform="rotate(20 815 1000)"')}
    <!-- 색종이 -->
    ${[...Array(40)].map((_, i) => {
      const x = (i * 197) % 1080, y = 580 + ((i * 131) % 560);
      return rect(x, y, 18, 10, ['#ef476f', '#ffd166', '#06d6a0', '#118ab2', '#8e7dbe'][i % 5], 2, `transform="rotate(${(i * 37) % 180} ${x} ${y})"`);
    }).join('')}
    ${shadeBottom()}`;
  },
};

function svg(name) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">${scenes[name]()}</svg>`;
}

module.exports = { svg, names: Object.keys(scenes), W, H };
