// 锻魂 / SoulForge 材质生成器。
//
// 运行：node tools/gen_textures.mjs
//
// 生成 src/main/resources/assets/soulforge/textures 下的全部方块、物品、GUI 与流体材质。
// 材质是程序化像素画，参数集中在 PALETTES 与各 pattern 函数里；改配色只改调色板，
// 改造型改对应的 pattern 函数。输出是确定性 PNG（无时间戳、无随机源），重复运行结果一致。
//
// 流体的色调直接从 SoulForgeFluids.java 解析，避免生成器与 Java 注册表各写一份颜色而漂移。

import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const ASSETS = path.join(ROOT, 'src/main/resources/assets/soulforge');
const TEX = path.join(ASSETS, 'textures');
const FLUID_SRC = path.join(ROOT, 'src/main/java/com/soulforge/soulforge/fluid/SoulForgeFluids.java');

// ---------------------------------------------------------------- PNG 编码

const CRC_TABLE = (() => {
    const t = new Int32Array(256);
    for (let n = 0; n < 256; n++) {
        let c = n;
        for (let k = 0; k < 8; k++) c = c & 1 ? (0xEDB88320 ^ (c >>> 1)) >>> 0 : (c >>> 1) >>> 0;
        t[n] = c;
    }
    return t;
})();

function crc32(buf) {
    let c = 0xFFFFFFFF;
    for (const b of buf) c = (CRC_TABLE[(c ^ b) & 255] ^ (c >>> 8)) >>> 0;
    return (c ^ 0xFFFFFFFF) >>> 0;
}

function chunk(type, data) {
    const t = Buffer.from(type, 'latin1');
    const len = Buffer.alloc(4);
    len.writeUInt32BE(data.length);
    const crc = Buffer.alloc(4);
    crc.writeUInt32BE(crc32(Buffer.concat([t, data])));
    return Buffer.concat([len, t, data, crc]);
}

/** RGBA8、非隔行、filter 0 的 PNG。 */
function encodePNG(w, h, px) {
    const ihdr = Buffer.alloc(13);
    ihdr.writeUInt32BE(w, 0);
    ihdr.writeUInt32BE(h, 4);
    ihdr[8] = 8;
    ihdr[9] = 6;
    const raw = Buffer.alloc(h * (w * 4 + 1));
    for (let y = 0; y < h; y++) {
        raw[y * (w * 4 + 1)] = 0;
        Buffer.from(px.buffer, px.byteOffset + y * w * 4, w * 4).copy(raw, y * (w * 4 + 1) + 1);
    }
    return Buffer.concat([
        Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
        chunk('IHDR', ihdr),
        chunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
        chunk('IEND', Buffer.alloc(0)),
    ]);
}

// ---------------------------------------------------------------- 颜色与噪声

const rgbCache = new Map();
function rgb(hex) {
    let c = rgbCache.get(hex);
    if (!c) {
        c = [parseInt(hex.slice(1, 3), 16), parseInt(hex.slice(3, 5), 16), parseInt(hex.slice(5, 7), 16)];
        rgbCache.set(hex, c);
    }
    return c;
}

/** amt > 0 提亮，< 0 压暗，范围约 -1..1。 */
function shade(hex, amt) {
    const [r, g, b] = rgb(hex);
    const f = v => Math.max(0, Math.min(255, Math.round(amt >= 0 ? v + (255 - v) * amt : v * (1 + amt))));
    return `#${[f(r), f(g), f(b)].map(v => v.toString(16).padStart(2, '0')).join('')}`;
}

function mix(hexA, hexB, t) {
    const a = rgb(hexA), b = rgb(hexB);
    return `#${[0, 1, 2].map(i => Math.round(a[i] + (b[i] - a[i]) * t).toString(16).padStart(2, '0')).join('')}`;
}

function hash2(x, y, s) {
    let h = Math.imul(x | 0, 374761393) + Math.imul(y | 0, 668265263) + Math.imul(s | 0, 1013904223);
    h = Math.imul(h ^ (h >>> 13), 1274126177);
    return ((h ^ (h >>> 16)) >>> 0) / 4294967296;
}

/** period 让格点回绕，得到的噪声在 period 像素上无缝，方块材质平铺才不会出现接缝。 */
function valueNoise(x, y, s, period) {
    const xi = Math.floor(x), yi = Math.floor(y), xf = x - xi, yf = y - yi;
    const u = xf * xf * (3 - 2 * xf), v = yf * yf * (3 - 2 * yf);
    const w = i => period ? ((i % period) + period) % period : i;
    const a = hash2(w(xi), w(yi), s), b = hash2(w(xi + 1), w(yi), s);
    const c = hash2(w(xi), w(yi + 1), s), d = hash2(w(xi + 1), w(yi + 1), s);
    return (a * (1 - u) + b * u) * (1 - v) + (c * (1 - u) + d * u) * v;
}

function fbm(x, y, s, oct = 3, period = 0) {
    let sum = 0, amp = 0.5, f = 1, norm = 0;
    for (let i = 0; i < oct; i++) {
        sum += amp * valueNoise(x * f, y * f, s + i * 97, period * f);
        norm += amp;
        amp *= 0.5;
        f *= 2;
    }
    return sum / norm;
}

// ---------------------------------------------------------------- 画布

class Canvas {
    constructor(w, h) {
        this.w = w;
        this.h = h;
        this.px = new Uint8Array(w * h * 4);
    }

    set(x, y, hex, a = 255) {
        if (x < 0 || y < 0 || x >= this.w || y >= this.h) return;
        const i = (y * this.w + x) * 4, c = rgb(hex);
        this.px[i] = c[0];
        this.px[i + 1] = c[1];
        this.px[i + 2] = c[2];
        this.px[i + 3] = a;
    }

    /** 覆盖绘制：仅当目标像素当前不透明时跳过，用来在不破坏轮廓的前提下叠纹理。 */
    over(x, y, hex, a = 255) {
        if (x < 0 || y < 0 || x >= this.w || y >= this.h) return;
        if (this.px[(y * this.w + x) * 4 + 3] === 0) return;
        this.set(x, y, hex, a);
    }

    fill(x, y, w, h, hex) {
        for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) this.set(x + i, y + j, hex);
    }

    frame(x, y, w, h, hex) {
        for (let i = 0; i < w; i++) {
            this.set(x + i, y, hex);
            this.set(x + i, y + h - 1, hex);
        }
        for (let j = 0; j < h; j++) {
            this.set(x, y + j, hex);
            this.set(x + w - 1, y + j, hex);
        }
    }

    /** 上/左提亮、下/右压暗的 1px 斜面，像素画里制造体积感的标准做法。 */
    bevel(x, y, w, h, lightHex, darkHex) {
        for (let i = 0; i < w; i++) {
            this.set(x + i, y, lightHex);
            this.set(x + i, y + h - 1, darkHex);
        }
        for (let j = 0; j < h; j++) {
            this.set(x, y + j, lightHex);
            this.set(x + w - 1, y + j, darkHex);
        }
    }

    /** 周期性亮度抖动。周期取画布尺寸，保证平铺无缝。 */
    grain(x, y, w, h, seed, amount) {
        for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) {
            const g = (fbm((x + i) / 3.2, (y + j) / 3.2, seed, 3, 16) - 0.5) * 2 * amount;
            const px = x + i, py = y + j;
            if (px < 0 || py < 0 || px >= this.w || py >= this.h) continue;
            const k = (py * this.w + px) * 4;
            if (this.px[k + 3] === 0) continue;
            const f = v => Math.max(0, Math.min(255, Math.round(v * (1 + g))));
            this.px[k] = f(this.px[k]);
            this.px[k + 1] = f(this.px[k + 1]);
            this.px[k + 2] = f(this.px[k + 2]);
        }
    }

    /** 圆形描边，用于阀门、舱盖、坩埚口。 */
    ring(cx, cy, r, hex, thickness = 1) {
        for (let y = -r - 1; y <= r + 1; y++) for (let x = -r - 1; x <= r + 1; x++) {
            const d = Math.hypot(x, y);
            if (d <= r + 0.5 && d >= r + 0.5 - thickness) this.set(cx + x, cy + y, hex);
        }
    }

    disc(cx, cy, r, hex) {
        for (let y = -r - 1; y <= r + 1; y++) for (let x = -r - 1; x <= r + 1; x++) {
            if (Math.hypot(x, y) <= r + 0.4) this.set(cx + x, cy + y, hex);
        }
    }

    /** 从 ASCII 图取形。'.' 与空格为透明，其余字符查 pal。 */
    static fromMap(rows, pal, name) {
        if (rows.length !== 16) throw new Error(`${name}: 需要 16 行，得到 ${rows.length}`);
        const c = new Canvas(16, 16);
        for (let y = 0; y < 16; y++) {
            if (rows[y].length !== 16) throw new Error(`${name}: 第 ${y} 行长度 ${rows[y].length}，需要 16`);
            for (let x = 0; x < 16; x++) {
                const ch = rows[y][x];
                if (ch === '.' || ch === ' ') continue;
                const col = pal[ch];
                if (!col) throw new Error(`${name}: 字符 '${ch}' 未映射颜色（${x},${y}）`);
                c.set(x, y, col);
            }
        }
        return c;
    }
}

// ---------------------------------------------------------------- 调色板

const P = {
    // 冶炼炉砖：工业灰钢
    smeltery: { frame: '#1b1b1b', mortar: '#2b2b2b', base: '#4a4a4a', light: '#5e5e5e', dark: '#373737', hi: '#787878', accent: '#ff8a2b', glow: '#ffd28a' },
    // 下界合金砖：带紫的暗合金
    netherite: { frame: '#141016', mortar: '#241d27', base: '#3b3541', light: '#4d4653', dark: '#2a2530', hi: '#635a6b', accent: '#9a5cd8', glow: '#d9b4ff' },
    // 三位一体合金：紫色
    trinity: { frame: '#1a0d26', mortar: '#2b1742', base: '#5b2b88', light: '#7539aa', dark: '#3f1c61', hi: '#9d57d0', accent: '#e0a0ff', glow: '#f6dcff' },
    // 奇点砖：近黑的深蓝
    singularity: { frame: '#07070e', mortar: '#101020', base: '#1b1b33', light: '#28284c', dark: '#111126', hi: '#3d3d6e', accent: '#7a68ff', glow: '#c0b6ff' },
    // 超抗金属块：冷灰蓝
    superResistant: { frame: '#15151c', mortar: '#1f1f29', base: '#2d2d39', light: '#3c3c4b', dark: '#21212b', hi: '#4e4e63', accent: '#8496ad', glow: '#c8d4e4' },
    // 磁真空合金：钴蓝
    magvac: { frame: '#0e1830', mortar: '#16243f', base: '#2a4a8a', light: '#3662ae', dark: '#1c3566', hi: '#4d82d4', accent: '#66d9ff', glow: '#c8f2ff' },
    // 铸造台：青铜铸铁
    castingTable: { frame: '#171310', mortar: '#2a221a', base: '#5c4c2c', light: '#75613a', dark: '#453820', hi: '#8f7847', accent: '#c9a25c', glow: '#f0d8a8' },
    // 铸造盆：更暗的铸铁
    castingBasin: { frame: '#141110', mortar: '#241d18', base: '#4c4034', light: '#615345', dark: '#382e25', hi: '#7a6857', accent: '#c9a25c', glow: '#f0d8a8' },
};

/** 五种控制器：结构相同，配色与辉光不同。 */
const CONTROLLERS = {
    smeltery_controller: { p: P.smeltery, glow: '#ffb347', core: '#ff7a1a' },
    intermediate_smeltery_controller: { p: { ...P.smeltery, base: '#342f33', light: '#453f44', dark: '#262226', hi: '#565054', mortar: '#1e1a1d', frame: '#131013' }, glow: '#ff9a4d', core: '#e05a10' },
    advanced_smeltery_controller: { p: { ...P.trinity, base: '#642584', light: '#7e35a2', dark: '#4a1a63', mortar: '#2f1244', frame: '#1b0c26' }, glow: '#e8b0ff', core: '#b44ce8' },
    hybrid_furnace_controller: { p: { ...P.magvac, base: '#244484', light: '#2f57a4', dark: '#182f5e', mortar: '#132444', frame: '#0b1226' }, glow: '#a8e8ff', core: '#38b6f0' },
    fusion_furnace_controller: { p: { ...P.singularity, base: '#141428', light: '#1f1f3d', dark: '#0c0c1a', mortar: '#0d0d1c', frame: '#06060c' }, glow: '#d8f4ff', core: '#68d8ff' },
};

// ---------------------------------------------------------------- 方块 pattern

/** 错缝砖墙。8 宽 4 高的砖，逐行错开半块，砖块自带斜面高光。 */
function bricks(pal, seed = 11) {
    const c = new Canvas(16, 16);
    for (let y = 0; y < 16; y++) {
        for (let x = 0; x < 16; x++) {
            const row = Math.floor(y / 4);
            const off = (row % 2) * 4;
            const mortarH = y % 4 === 3;
            const bx = (x + off) % 8;
            const mortarV = bx === 7;
            if (mortarH || mortarV) {
                c.set(x, y, pal.mortar);
                continue;
            }
            let col = pal.base;
            if (y % 4 === 0) col = pal.light;
            else if (y % 4 === 2) col = pal.dark;
            if (bx === 0) col = shade(col, 0.14);
            else if (bx === 6) col = shade(col, -0.14);
            c.set(x, y, col);
        }
    }
    c.grain(0, 0, 16, 16, seed, 0.10);
    // 零星深色斑点，避免大片纯色看起来是贴图糊了
    for (let i = 0; i < 10; i++) {
        const x = Math.floor(hash2(i, 3, seed) * 16), y = Math.floor(hash2(i, 7, seed) * 16);
        if (c.px[(y * 16 + x) * 4 + 3]) c.set(x, y, shade(pal.base, -0.22));
    }
    return c;
}

/** 金属板：外框 + 内斜面 + 四角铆钉 + 斜向拉丝。 */
function plate(pal, seed = 21) {
    const c = new Canvas(16, 16);
    c.fill(0, 0, 16, 16, pal.base);
    // 斜向拉丝
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
        const s = ((x * 5 + y * 3) % 9) / 8;
        c.set(x, y, shade(pal.base, (s - 0.5) * 0.22));
    }
    c.grain(0, 0, 16, 16, seed, 0.10);
    c.bevel(0, 0, 16, 16, pal.light, pal.dark);
    c.frame(0, 0, 16, 16, pal.frame);
    // 内凹面板线
    c.frame(2, 2, 12, 12, shade(pal.base, -0.28));
    c.frame(3, 3, 10, 10, shade(pal.base, 0.16));
    // 中心菱形徽记
    for (let i = 0; i < 4; i++) {
        c.set(8 - 3 + i, 8, pal.hi);
        c.set(8 + 3 - i, 8, pal.hi);
        c.set(8, 8 - 3 + i, pal.hi);
        c.set(8, 8 + 3 - i, pal.hi);
    }
    c.set(8, 8, pal.accent);
    // 四角铆钉
    for (const [rx, ry] of [[1, 1], [14, 1], [1, 14], [14, 14]]) {
        c.set(rx, ry, pal.hi);
    }
    return c;
}

/** 燃料罐：金属罐体 + 中间竖向观察窗。 */
function tank(pal, fluidHex, seed = 31) {
    const c = new Canvas(16, 16);
    c.fill(0, 0, 16, 16, pal.base);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
        c.set(x, y, shade(pal.base, ((x % 4) - 1.5) * 0.05));
    }
    c.grain(0, 0, 16, 16, seed, 0.08);
    // 上下端盖
    c.fill(1, 1, 14, 2, pal.light);
    c.fill(1, 13, 14, 2, pal.dark);
    c.frame(0, 0, 16, 16, pal.frame);
    c.bevel(1, 1, 14, 14, pal.hi, shade(pal.dark, -0.2));
    c.frame(0, 3, 16, 1, pal.frame);
    c.frame(0, 12, 16, 1, pal.frame);
    // 观察窗
    c.fill(6, 4, 4, 8, shade(fluidHex, -0.55));
    for (let y = 4; y < 12; y++) {
        c.set(6, y, shade(fluidHex, -0.15));
        c.set(9, y, shade(fluidHex, -0.65));
    }
    c.set(7, 5, shade(fluidHex, 0.25));
    c.set(7, 6, shade(fluidHex, 0.1));
    c.frame(5, 3, 6, 10, pal.frame);
    // 铆钉
    for (const [rx, ry] of [[2, 2], [13, 2], [2, 13], [13, 13]]) c.set(rx, ry, pal.hi);
    return c;
}

/** 控制器正面：外壳 + 格栅 + 中央发光核心。 */
function controllerFront(pal, glow, core, seed = 41) {
    const c = new Canvas(16, 16);
    c.fill(0, 0, 16, 16, pal.base);
    c.grain(0, 0, 16, 16, seed, 0.09);
    c.bevel(0, 0, 16, 16, pal.light, pal.dark);
    c.frame(0, 0, 16, 16, pal.frame);
    c.frame(2, 2, 12, 12, pal.frame);
    // 顶部散热格栅
    for (let i = 0; i < 3; i++) {
        const y = 4 + i;
        c.fill(4, y, 8, 1, shade(pal.base, -0.45));
        c.fill(4, y - 1 < 3 ? 3 : y - 1, 8, 0, pal.hi);
    }
    // 中央核心
    c.disc(8, 9, 3, shade(core, -0.55));
    c.disc(8, 9, 2, core);
    c.disc(8, 9, 1, glow);
    c.set(8, 9, shade(glow, 0.5));
    c.ring(8, 9, 4, pal.hi);
    // 四角螺栓
    for (const [bx, by] of [[1, 1], [14, 1], [1, 14], [14, 14]]) {
        c.set(bx, by, pal.hi);
        c.set(bx, by + (by === 1 ? 1 : -1), shade(pal.hi, -0.3));
    }
    return c;
}

/** 控制器侧面：竖向散热缝。 */
function controllerSide(pal, seed = 51) {
    const c = new Canvas(16, 16);
    c.fill(0, 0, 16, 16, pal.base);
    c.grain(0, 0, 16, 16, seed, 0.09);
    c.bevel(0, 0, 16, 16, pal.light, pal.dark);
    c.frame(0, 0, 16, 16, pal.frame);
    for (let g = 0; g < 4; g++) {
        const x = 2 + g * 4;
        c.fill(x, 3, 2, 10, shade(pal.base, -0.5));
        c.fill(x, 3, 1, 10, shade(pal.base, -0.7));
        c.fill(x + 2, 3, 1, 10, pal.hi);
    }
    c.frame(1, 1, 14, 14, shade(pal.base, 0.1));
    for (const [bx, by] of [[1, 1], [14, 1], [1, 14], [14, 14]]) c.set(bx, by, pal.hi);
    return c;
}

/** 控制器顶面：环形舱盖。 */
function controllerTop(pal, accent, seed = 61) {
    const c = new Canvas(16, 16);
    c.fill(0, 0, 16, 16, shade(pal.base, 0.08));
    c.grain(0, 0, 16, 16, seed, 0.09);
    c.bevel(0, 0, 16, 16, pal.light, pal.dark);
    c.frame(0, 0, 16, 16, pal.frame);
    c.ring(8, 8, 5, shade(pal.base, -0.35));
    c.ring(8, 8, 4, pal.hi);
    c.disc(8, 8, 2, shade(pal.base, -0.2));
    c.set(8, 8, accent);
    c.set(8, 7, shade(accent, 0.4));
    // 十字加强筋
    for (const [dx, dy] of [[0, -1], [0, 1], [-1, 0], [1, 0]]) {
        c.set(8 + dx * 6, 8 + dy * 6, pal.hi);
    }
    for (const [bx, by] of [[1, 1], [14, 1], [1, 14], [14, 14]]) c.set(bx, by, pal.hi);
    return c;
}

/** 控制器正面（工作状态）：与待机同构，但核心与格栅透出强光。 */
function controllerFrontActive(pal, glow, core, seed = 42) {
    const c = controllerFront(pal, glow, core, seed);
    // 格栅缝隙透出炉火
    for (let i = 0; i < 3; i++) {
        const y = 4 + i;
        c.fill(4, y, 8, 1, shade(core, -0.15));
    }
    for (let x = 4; x < 12; x += 3) c.set(x, 4, glow);
    // 核心扩大并整体提亮
    c.disc(8, 9, 4, shade(core, -0.3));
    c.disc(8, 9, 3, core);
    c.disc(8, 9, 2, glow);
    c.disc(8, 9, 1, shade(glow, 0.6));
    c.ring(8, 9, 5, shade(glow, -0.25));
    // 顶面与底面的受光边
    for (let x = 3; x <= 12; x++) {
        c.set(x, 2, shade(core, -0.4));
        c.set(x, 13, shade(core, -0.55));
    }
    return c;
}

/** 铸造台顶面：台面 + 中央铸模凹槽。 */
function castingTableTop(pal, seed = 71) {
    const c = new Canvas(16, 16);
    c.fill(0, 0, 16, 16, pal.base);
    c.grain(0, 0, 16, 16, seed, 0.10);
    c.bevel(0, 0, 16, 16, pal.light, pal.dark);
    c.frame(0, 0, 16, 16, pal.frame);
    c.frame(1, 1, 14, 14, shade(pal.base, 0.14));
    // 铸模凹槽（锭形）
    c.fill(4, 5, 8, 6, shade(pal.base, -0.55));
    c.frame(3, 4, 10, 8, pal.frame);
    c.fill(5, 6, 6, 1, shade(pal.base, -0.35));
    c.fill(4, 10, 8, 1, shade(pal.base, -0.15));
    c.set(5, 6, pal.accent);
    // 四角固定螺栓
    for (const [bx, by] of [[2, 2], [13, 2], [2, 13], [13, 13]]) {
        c.set(bx, by, pal.hi);
        c.set(bx + (bx === 2 ? 1 : -1), by, shade(pal.hi, -0.25));
    }
    return c;
}

/** 铸造台侧面：台沿 + 下方支撑。 */
function castingTableSide(pal, seed = 81) {
    const c = new Canvas(16, 16);
    c.fill(0, 0, 16, 16, pal.base);
    c.grain(0, 0, 16, 16, seed, 0.10);
    c.bevel(0, 0, 16, 16, pal.light, pal.dark);
    c.frame(0, 0, 16, 16, pal.frame);
    // 台面厚度
    c.fill(1, 1, 14, 4, pal.light);
    c.fill(1, 5, 14, 1, pal.frame);
    c.bevel(1, 1, 14, 4, shade(pal.light, 0.12), shade(pal.light, -0.3));
    // 下方镂空与立柱
    c.fill(2, 6, 12, 9, shade(pal.dark, -0.35));
    c.fill(3, 6, 3, 9, pal.base);
    c.fill(10, 6, 3, 9, pal.base);
    c.bevel(3, 6, 3, 9, pal.hi, shade(pal.dark, -0.3));
    c.bevel(10, 6, 3, 9, pal.hi, shade(pal.dark, -0.3));
    for (const y of [8, 11]) c.fill(3, y, 10, 1, shade(pal.base, -0.25));
    return c;
}

/** 铸造台底面：底板 + 十字加强筋。 */
function castingBottom(pal, seed = 91) {
    const c = new Canvas(16, 16);
    c.fill(0, 0, 16, 16, shade(pal.base, -0.2));
    c.grain(0, 0, 16, 16, seed, 0.10);
    c.bevel(0, 0, 16, 16, pal.base, shade(pal.dark, -0.3));
    c.frame(0, 0, 16, 16, pal.frame);
    c.fill(2, 7, 12, 2, shade(pal.base, -0.4));
    c.fill(7, 2, 2, 12, shade(pal.base, -0.4));
    c.frame(2, 2, 12, 12, shade(pal.base, -0.25));
    for (const [bx, by] of [[2, 2], [13, 2], [2, 13], [13, 13]]) c.set(bx, by, pal.hi);
    return c;
}

/** 铸造盆顶面：坩埚口。 */
function castingBasinTop(pal, seed = 101) {
    const c = new Canvas(16, 16);
    c.fill(0, 0, 16, 16, pal.base);
    c.grain(0, 0, 16, 16, seed, 0.10);
    c.bevel(0, 0, 16, 16, pal.light, pal.dark);
    c.frame(0, 0, 16, 16, pal.frame);
    c.ring(8, 8, 6, pal.hi);
    c.ring(8, 8, 5, shade(pal.base, -0.5));
    c.disc(8, 8, 4, '#0a0806');
    c.ring(8, 8, 4, shade(pal.base, -0.2));
    // 盆内残留熔液
    c.disc(8, 9, 2, shade(pal.accent, -0.35));
    c.disc(8, 9, 1, pal.accent);
    c.set(8, 9, pal.glow);
    return c;
}

/** 铸造盆侧面：盆壁 + 出液口。 */
function castingBasinSide(pal, seed = 111) {
    const c = new Canvas(16, 16);
    c.fill(0, 0, 16, 16, pal.base);
    c.grain(0, 0, 16, 16, seed, 0.10);
    c.bevel(0, 0, 16, 16, pal.light, pal.dark);
    c.frame(0, 0, 16, 16, pal.frame);
    c.fill(1, 1, 14, 2, pal.light);
    c.bevel(1, 1, 14, 2, shade(pal.light, 0.15), shade(pal.light, -0.35));
    c.fill(1, 4, 14, 1, pal.frame);
    for (const y of [7, 11]) {
        c.fill(2, y, 12, 1, shade(pal.base, -0.35));
        c.fill(2, y + 1, 12, 1, pal.hi);
    }
    // 出液口
    c.fill(11, 5, 3, 3, shade(pal.base, -0.45));
    c.frame(10, 4, 5, 5, pal.frame);
    c.set(12, 6, pal.accent);
    for (const [bx, by] of [[2, 2], [13, 2], [2, 13], [13, 13]]) c.set(bx, by, pal.hi);
    return c;
}

// ---------------------------------------------------------------- 物品 pattern

/** 通用锭形：平顶、斜肩、直身，顶面高光带与底面暗带。 */
function ingot(pal) {
    return Canvas.fromMap([
        '................',
        '................',
        '................',
        '................',
        '................',
        '....########....',
        '...#hhhhhhhh#...',
        '..#hhhhhhhhhh#..',
        '.#hhhhhhhhhhhh#.',
        '.#hhhhhhhhhhhh#.',
        '.#bbbbbbbbbbbb#.',
        '.#ssssssssssss#.',
        '.##############.',
        '................',
        '................',
        '................',
    ], pal, 'ingot');
}

/** 通用桶形：提梁 + 桶身，可注入液体色。 */
function bucket(pal, fluid) {
    const c = Canvas.fromMap([
        '................',
        '................',
        '.....######.....',
        '....#......#....',
        '...#........#...',
        '...#........#...',
        '..#..........#..',
        '..#..........#..',
        '..#RRRRRRRRRR#..',
        '..#RBBBBBBBBR#..',
        '..#RBBBBBBBBR#..',
        '..#RBBBBBBBBR#..',
        '..#RBBBBBBBBR#..',
        '..#RRRRRRRRRR#..',
        '...##########...',
        '................',
    ], pal, 'bucket');
    if (fluid) {
        // 桶内液体：从桶口往下填充，顶部留出液面高光
        for (let y = 9; y <= 12; y++) for (let x = 4; x <= 11; x++) {
            if (c.px[(y * 16 + x) * 4 + 3] === 0) continue;
            if (x === 3 || x === 12) continue;
            c.set(x, y, y === 9 ? shade(fluid, 0.25) : shade(fluid, -0.1 - (y - 9) * 0.12));
        }
        c.set(5, 9, shade(fluid, 0.55));
        c.set(6, 9, shade(fluid, 0.4));
    }
    return c;
}

/** 通用罐形：立式燃料罐。空罐画暗色内壁，装液罐画液位。 */
function tankItem(pal, fluid) {
    const c = Canvas.fromMap([
        '................',
        '......####......',
        '......#..#......',
        '...##########...',
        '...#MMMMMMMM#...',
        '...#MMMMMMMM#...',
        '...#G......G#...',
        '...#G......G#...',
        '...#G......G#...',
        '...#G......G#...',
        '...#G......G#...',
        '...#MMMMMMMM#...',
        '...#MMMMMMMM#...',
        '...##########...',
        '................',
        '................',
    ], { ...pal, G: '#000000' }, 'tankItem');
    if (fluid) {
        for (let y = 6; y <= 10; y++) for (let x = 5; x <= 10; x++) {
            c.set(x, y, y === 6 ? shade(fluid, 0.25) : shade(fluid, -0.05 - (y - 6) * 0.11));
        }
        c.set(5, 6, shade(fluid, 0.55));
        c.set(6, 6, shade(fluid, 0.4));
    } else {
        // 空罐：内壁左亮右暗，别让观察窗读成一个黑洞
        for (let y = 6; y <= 10; y++) for (let x = 5; x <= 10; x++) {
            c.set(x, y, shade(pal.M, -0.45 - (x - 5) * 0.05));
        }
        c.set(5, 6, shade(pal.M, -0.1));
    }
    return c;
}

/** 机械装置：外壳 + 中央发光核心 + 底部格栅。 */
function generator(pal, glow, core, seed) {
    const c = Canvas.fromMap([
        '................',
        '................',
        '..############..',
        '..#MMMMMMMMMM#..',
        '..#M########M#..',
        '..#M#gggggg#M#..',
        '..#M#gggggg#M#..',
        '..#M#gggggg#M#..',
        '..#M#gggggg#M#..',
        '..#M########M#..',
        '..#MMMMMMMMMM#..',
        '..#MVVVVVVVVM#..',
        '..#MMMMMMMMMM#..',
        '..############..',
        '................',
        '................',
    ], { ...pal, g: '#000000' }, 'generator');
    // 核心辉光：中心最亮，向外衰减
    for (let y = 5; y <= 8; y++) for (let x = 5; x <= 10; x++) {
        const d = Math.hypot(x - 7.5, y - 6.5) / 3.2;
        c.set(x, y, mix(core, glow, Math.max(0, 1 - d)));
    }
    c.set(7, 6, shade(glow, 0.6));
    c.set(8, 6, shade(glow, 0.4));
    // 格栅缝
    for (let x = 5; x <= 10; x++) {
        if (x % 2 === 0) c.set(x, 11, shade(pal.M, -0.55));
    }
    c.grain(0, 0, 16, 16, seed, 0.06);
    return c;
}

// ---------------------------------------------------------------- 物品定义

/**
 * 熔炼手套 / 隔热手套：连指手套轮廓。
 * 16x16 下张开的五指会被 1px 描边切碎成锯齿，连指形才能一眼读出"手套"。
 */
const GLOVE_MAP = [
    '................',
    '................',
    '.....####.......',
    '....#LLLL#......',
    '...#LLLLLL#.....',
    '...#LLLLLL#.....',
    '...#LLLLLL#.....',
    '...#LLLLLL#.....',
    '...#LLLLLL#.....',
    '..#LLLLLLLL#....',
    '..#LLLLLLLL#....',
    '..#CCCCCCCC#....',
    '..#CCCCCCCC#....',
    '..#CCCCCCCC#....',
    '..##########....',
    '................',
];

/** 隔热手套：与普通手套同形，上半手背整体换成橙色耐热装甲，下半留深色皮革。 */
const THERMAL_GLOVE_MAP = [
    '................',
    '................',
    '.....####.......',
    '....#AAAA#......',
    '...#AAAAAA#.....',
    '...#AAAAAA#.....',
    '...#AAAAAA#.....',
    '...#AAAAAA#.....',
    '...#AAAAAA#.....',
    '..#AAAAAAAA#....',
    '..#AAAAAAAA#....',
    '..#LLLLLLLL#....',
    '..#LLLLLLLL#....',
    '..#LLLLLLLL#....',
    '..##########....',
    '................',
];

/** 磁力线圈：铁芯上绕三匝线圈，绕组之间露出深色铁芯。 */
const COIL_MAP = [
    '................',
    '.......##.......',
    '.......##.......',
    '....########....',
    '...#CCCCCCCC#...',
    '...#dddddddd#...',
    '...#DDDDDDDD#...',
    '...#dddddddd#...',
    '...#CCCCCCCC#...',
    '...#dddddddd#...',
    '...#DDDDDDDD#...',
    '...#dddddddd#...',
    '....########....',
    '................',
    '................',
    '................',
];
/** 下界之心：心形，左上高光、内部暗色血管。 */
const HEART_MAP = [
    '................',
    '................',
    '................',
    '...###...###....',
    '..#HHH#.#HHH#...',
    '.#HHHHH#HHHHH#..',
    '.#HHHHHHHHHHH#..',
    '.#HHHHHHHHHHH#..',
    '.#HHHHHHHHHHH#..',
    '..#HHHHHHHHH#...',
    '...#HHHHHHH#....',
    '....#HHHHH#.....',
    '.....#HHH#......',
    '......#.#.......',
    '................',
    '................',
];

/** 锭模：陶土块上一道横向锭形凹槽。 */
const CAST_INGOT_MAP = [
    '................',
    '................',
    '..############..',
    '.#TTTTTTTTTTTT#.',
    '.#TDDDDDDDDDDT#.',
    '.#TD########DT#.',
    '.#TD########DT#.',
    '.#TDDDDDDDDDDT#.',
    '.#TTTTTTTTTTTT#.',
    '..############..',
    '................',
    '................',
    '................',
    '................',
    '................',
    '................',
];
/** 宝石模：陶土块上一道菱形凹槽。 */
const CAST_GEM_MAP = [
    '................',
    '................',
    '................',
    '..############..',
    '.#TTTTTTTTTTTT#.',
    '.#TTDDDDDDDDTT#.',
    '.#TTD######DTT#.',
    '.#TTD######DTT#.',
    '.#TTDDDDDDDDTT#.',
    '.#TTTTTTTTTTTT#.',
    '..############..',
    '................',
    '................',
    '................',
    '................',
    '................',
];

function buildItems(fluids) {
    const items = {};

    items['glove'] = Canvas.fromMap(GLOVE_MAP, {
        '#': '#171310', L: '#8a6b3c', C: '#6b4f2a',
    }, 'glove');
    // 皮革高光与缝线
    for (let y = 3; y <= 8; y++) items['glove'].set(2, y, '#a8844f');
    for (let x = 3; x <= 12; x++) items['glove'].set(x, 6, '#9c7845');
    items['glove'].grain(0, 0, 16, 16, 201, 0.10);

    items['blunt_thermal_glove'] = Canvas.fromMap(THERMAL_GLOVE_MAP, {
        '#': '#0d0b0a', L: '#4a4038', A: '#b4661c',
    }, 'blunt_thermal_glove');
    // 手背装甲片的受光边与分界线
    for (let y = 3; y <= 10; y++) items['blunt_thermal_glove'].over(3, y, '#d4821f');
    items['blunt_thermal_glove'].over(4, 3, '#d4821f');
    items['blunt_thermal_glove'].over(5, 3, '#d4821f');
    for (let x = 4; x <= 11; x++) items['blunt_thermal_glove'].over(x, 10, '#8a4a12');
    for (let y = 11; y <= 13; y++) items['blunt_thermal_glove'].over(3, y, '#5c5148');
    items['blunt_thermal_glove'].grain(0, 0, 16, 16, 211, 0.10);

    items['trinity_alloy_ingot'] = ingot({ '#': '#1a0d26', h: '#9d57d0', b: '#6a2f9c', s: '#4a1f70' });
    items['trinity_alloy_ingot'].grain(0, 0, 16, 16, 221, 0.08);

    items['magnetic_coil'] = Canvas.fromMap(COIL_MAP, {
        '#': '#241a10', C: '#e08a3c', D: '#a85a1c', d: '#4a4038',
    }, 'magnetic_coil');
    // 每匝左缘受光、右缘背光，看起来才是绕出来的
    for (const y of [4, 8]) {
        items['magnetic_coil'].set(4, y, '#f4b070');
        items['magnetic_coil'].set(11, y, '#7a3c0c');
    }
    for (const y of [6, 10]) {
        items['magnetic_coil'].set(4, y, '#c4762c');
        items['magnetic_coil'].set(11, y, '#63300a');
    }

    items['nether_heart'] = Canvas.fromMap(HEART_MAP, {
        '#': '#2a0406', H: '#a81420',
    }, 'nether_heart');
    for (let x = 3; x <= 7; x++) items['nether_heart'].over(x, 5, '#d8283a');
    for (let x = 3; x <= 6; x++) items['nether_heart'].over(x, 6, '#c01c2c');
    items['nether_heart'].over(4, 4, '#f05a6a');
    items['nether_heart'].over(5, 4, '#e04456');
    for (const [vx, vy] of [[7, 7], [8, 7], [7, 8], [9, 8], [8, 9], [6, 8]]) {
        items['nether_heart'].over(vx, vy, '#6b0c14');
    }
    items['nether_heart'].grain(0, 0, 16, 16, 231, 0.08);

    // 铸模：陶土色凹槽。锭模是横长条凹槽，宝石模是菱形凹槽。
    items['ingot_cast'] = Canvas.fromMap(CAST_INGOT_MAP, {
        '#': '#3a2a1c', T: '#a8785a', D: '#7a5238',
    }, 'ingot_cast');
    items['gem_cast'] = Canvas.fromMap(CAST_GEM_MAP, {
        '#': '#3a2a1c', T: '#a8785a', D: '#7a5238',
    }, 'gem_cast');
    for (const k of ['ingot_cast', 'gem_cast']) {
        items[k].grain(0, 0, 16, 16, 281, 0.10);
    }

    items['vacuum_generator'] = generator({ '#': '#14141a', M: '#5a5a66', V: '#3a3a44' }, '#a8d8ff', '#2f7fd0', 241);
    items['magvac_generator'] = generator({ '#': '#0b1226', M: '#2a4a8a', V: '#1c3566' }, '#c8f2ff', '#2f9fd8', 251);

    // 三种空桶：金属不同
    items['netherite_bucket'] = bucket({ '#': '#141016', R: '#635a6b', B: '#3b3541' }, null);
    items['trinity_alloy_bucket'] = bucket({ '#': '#1a0d26', R: '#9d57d0', B: '#5b2b88' }, null);
    items['magvac_bucket'] = bucket({ '#': '#0e1830', R: '#4d82d4', B: '#2a4a8a' }, null);
    for (const k of ['netherite_bucket', 'trinity_alloy_bucket', 'magvac_bucket']) {
        items[k].grain(0, 0, 16, 16, 261, 0.08);
    }

    // 三种空罐
    items['netherite_fuel_tank'] = tankItem({ '#': '#141016', M: '#4d4653' }, null);
    items['trinity_alloy_fuel_tank'] = tankItem({ '#': '#1a0d26', M: '#7539aa' }, null);
    items['magvac_fuel_tank'] = tankItem({ '#': '#0e1830', M: '#3662ae' }, null);
    for (const k of ['netherite_fuel_tank', 'trinity_alloy_fuel_tank', 'magvac_fuel_tank']) {
        items[k].grain(0, 0, 16, 16, 271, 0.08);
    }

    // 18 个装液桶：下界合金桶 + 对应流体色。此前它们全部共用 item/netherite_bucket，
    // 游戏里 18 种桶长得一模一样，只有名字不同。
    for (const f of fluids) {
        items[`${f.name}_bucket`] = bucket({ '#': '#141016', R: '#635a6b', B: '#3b3541' }, f.hex);
        items[`${f.name}_bucket`].grain(0, 0, 16, 16, 281, 0.07);
    }

    return items;
}

// ---------------------------------------------------------------- GUI

/**
 * 界面贴图直接取自匠魂3（Tinkers' Construct 3, MIT）的 heating_structure.png 与 generic.png。
 *
 * 原因很直接：控制器的布局常量（熔融罐 8,16,106,106；燃料罐 152,32,16,90；火焰 153,15；
 * 按钮 124,69；桶槽 125,46 与 125,104；物品栏 138/196）与匠魂3 逐一对齐，主面板同为
 * 176x220，因此原版贴图可以直接用——手绘近似只会得到一个"像但不是"的界面。
 *
 * 贴图里除主面板外的元素都是透明底，叠在罐体上不会盖住流体：
 *   刻度尺 176,0 / 火焰 176,136 / 热进度条 176,150 / 传输方向图标 176,186 /
 *   按钮高亮 176,202 / 侧栏槽位 0,238 与 22,238
 *
 * 版权与许可见 tools/gui_ref/NOTICE.md。
 */
const GUI_REF = path.join(ROOT, 'tools/gui_ref');

/** 读取参考 PNG，返回 { w, h, px }；仅支持 8 位 RGBA 非隔行。 */
function readPng(file) {
    const buf = fs.readFileSync(file);
    if (buf.readUInt32BE(0) !== 0x89504E47) throw new Error(`${file}: 不是 PNG`);
    let off = 8, w = 0, h = 0, bitDepth = 0, colorType = 0;
    const idat = [];
    while (off < buf.length) {
        const len = buf.readUInt32BE(off);
        const type = buf.toString('latin1', off + 4, off + 8);
        const data = buf.subarray(off + 8, off + 8 + len);
        if (type === 'IHDR') {
            w = data.readUInt32BE(0);
            h = data.readUInt32BE(4);
            bitDepth = data[8];
            colorType = data[9];
            if (bitDepth !== 8 || colorType !== 6 || data[12] !== 0) {
                throw new Error(`${file}: 只支持 8 位 RGBA 非隔行 PNG`);
            }
        } else if (type === 'IDAT') {
            idat.push(data);
        } else if (type === 'IEND') {
            break;
        }
        off += 12 + len;
    }
    const raw = zlib.inflateSync(Buffer.concat(idat));
    const px = new Uint8Array(w * h * 4);
    const stride = w * 4;
    let prev = Buffer.alloc(stride);
    for (let y = 0; y < h; y++) {
        const filter = raw[y * (stride + 1)];
        const line = Buffer.from(raw.subarray(y * (stride + 1) + 1, (y + 1) * (stride + 1)));
        // 反 PNG 行滤波，逐字节还原
        for (let i = 0; i < stride; i++) {
            const a = i >= 4 ? line[i - 4] : 0;
            const b = prev[i];
            const c = i >= 4 ? prev[i - 4] : 0;
            let v = line[i];
            switch (filter) {
                case 0: break;
                case 1: v = (v + a) & 255; break;
                case 2: v = (v + b) & 255; break;
                case 3: v = (v + ((a + b) >> 1)) & 255; break;
                case 4: {
                    const p = a + b - c;
                    const pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
                    v = (v + (pa <= pb && pa <= pc ? a : pb <= pc ? b : c)) & 255;
                    break;
                }
                default: throw new Error(`${file}: 未知的行滤波 ${filter}`);
            }
            line[i] = v;
        }
        px.set(line, y * stride);
        prev = line;
    }
    return { w, h, px };
}

/**
 * 把参考贴图整体搬进生成流程：直接输出原图字节，保证与上游逐像素一致。
 * 生成器仍是资源的唯一来源，同时"抄得像不像"这件事不再靠肉眼判断。
 */
function copyGuiRef(name, rel) {
    const src = path.join(GUI_REF, name);
    const bytes = fs.readFileSync(src);
    const dest = path.join(TEX, rel);
    fs.mkdirSync(path.dirname(dest), { recursive: true });
    fs.writeFileSync(dest, bytes);
    written.push({ rel: `textures/${rel}`, bytes: bytes.length });
    return bytes.length;
}

// ---------------------------------------------------------------- 流体

/** 从 Java 注册表解析流体名与色调，避免两处各写一份颜色。 */
function readFluids() {
    const src = fs.readFileSync(FLUID_SRC, 'utf8');
    const re = /register(?:Molten|BlazingBlood)\("([a-z_]+)",\s*\d+,\s*0x([0-9A-Fa-f]{8}),\s*\d+\)/g;
    const out = [];
    let m;
    while ((m = re.exec(src))) {
        const argb = parseInt(m[2], 16);
        out.push({
            name: m[1],
            tint: argb,
            hex: `#${(argb & 0xFFFFFF).toString(16).padStart(6, '0')}`,
        });
    }
    if (out.length !== 18) throw new Error(`解析到 ${out.length} 种流体，预期 18 种；SoulForgeFluids.java 格式可能变了`);
    return out;
}

/**
 * 流体材质：静流 16x16、流动 32x32，各 8 帧竖排。
 * 图案是分层 fbm 阈值化的熔融纹理，帧间平移噪声场形成流动感。
 */
function fluidFrames(hex, size, frames, seed) {
    const c = new Canvas(size, size * frames);
    for (let f = 0; f < frames; f++) {
        const t = f * 0.7;
        for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
            const n = fbm(x / 6 + t * 0.25, y / 6 + t * 0.5, seed, 4, 0);
            let col;
            if (n < 0.40) col = shade(hex, -0.62);
            else if (n < 0.50) col = shade(hex, -0.38);
            else if (n < 0.60) col = shade(hex, -0.14);
            else if (n < 0.70) col = shade(hex, 0.12);
            else if (n < 0.78) col = shade(hex, 0.34);
            else col = shade(hex, 0.62);
            c.set(x, y + f * size, col);
        }
        // 亮裂缝：等值线附近的像素提亮，熔融金属的典型观感
        for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
            const n = fbm(x / 6 + t * 0.25, y / 6 + t * 0.5, seed, 4, 0);
            if (Math.abs(n - 0.62) < 0.018) c.set(x, y + f * size, shade(hex, 0.85));
        }
    }
    return c;
}

/** 流动纹理：竖直拉长的条纹，向下滚动。 */
function fluidFlowFrames(hex, frames, seed) {
    const size = 32;
    const c = new Canvas(size, size * frames);
    for (let f = 0; f < frames; f++) {
        const t = f * 1.6;
        for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
            const n = fbm(x / 9, (y + t) / 14, seed + 7, 4, 0);
            const streak = fbm(x / 2.5, (y + t) / 22, seed + 31, 2, 0);
            const v = n * 0.65 + streak * 0.35;
            let col;
            if (v < 0.42) col = shade(hex, -0.55);
            else if (v < 0.52) col = shade(hex, -0.28);
            else if (v < 0.62) col = shade(hex, 0.0);
            else if (v < 0.72) col = shade(hex, 0.3);
            else col = shade(hex, 0.58);
            c.set(x, y + f * size, col);
        }
    }
    return c;
}

// ---------------------------------------------------------------- 模型与方块状态

/** 物品模型：单层贴图。 */
const itemModel = name => ({ parent: 'item/generated', textures: { layer0: `soulforge:item/${name}` } });

/** 六面方块模型。传入 up/down/north/south/east/west 的贴图名（不含命名空间）。 */
function cubeModel(t) {
    const tex = {};
    for (const k of ['north', 'south', 'east', 'west', 'up', 'down']) tex[k] = `soulforge:block/${t[k]}`;
    tex.particle = `soulforge:block/${t.particle ?? t.north}`;
    return { parent: 'block/cube', textures: tex };
}

function writeModels(fluids) {
    // --- 五种控制器：正面/侧面/顶面三张不同贴图，用 block/cube 区分六面 ---
    // 方块物品只写 items/<id>.json 指向方块模型。vanilla 26.3 里 832 个方块物品
    // 没有一个存在同名的 models/item/ 文件，多写一份就是没人引用的死文件。
    for (const name of Object.keys(CONTROLLERS)) {
        saveJson(`models/block/${name}.json`, cubeModel({
            north: `${name}_front`, south: `${name}_front`,
            east: `${name}_side`, west: `${name}_side`,
            up: `${name}_top`, down: `${name}_side`,
        }));
        saveJson(`items/${name}.json`, { model: { type: 'minecraft:model', model: `soulforge:block/${name}` } });
    }

    // --- 铸造台 / 铸造盆 ---
    for (const [name, top, side, bottom] of [
        ['casting_table', 'casting_table_top', 'casting_table_side', 'casting_table_bottom'],
        ['casting_basin', 'casting_basin_top', 'casting_basin_side', 'casting_basin_bottom'],
    ]) {
        saveJson(`models/block/${name}.json`, cubeModel({ north: side, south: side, east: side, west: side, up: top, down: bottom }));
        saveJson(`items/${name}.json`, { model: { type: 'minecraft:model', model: `soulforge:block/${name}` } });
    }

    // --- 砖类与金属块：六面同贴图 ---
    const CUBE_ALL = [
        'smeltery_bricks', 'netherite_bricks', 'trinity_alloy_bricks', 'singularity_bricks',
        'trinity_alloy_block', 'super_resistant_metal_block', 'magvac_alloy_block',
        'netherite_fuel_tank', 'trinity_alloy_fuel_tank', 'magvac_fuel_tank',
    ];
    for (const name of CUBE_ALL) {
        saveJson(`models/block/${name}.json`, { parent: 'block/cube_all', textures: { all: `soulforge:block/${name}` } });
        // 燃料储罐的物品与方块同名，方块物品的注册名带 _block 后缀
        const itemId = name.endsWith('_fuel_tank') ? `${name}_block` : name;
        saveJson(`items/${itemId}.json`, { model: { type: 'minecraft:model', model: `soulforge:block/${name}` } });
    }

    // --- 18 种流体方块：贴图是自绘的流体材质，不再复用 block/water ---
    for (const f of fluids) {
        saveJson(`models/block/${f.name}.json`, {
            parent: 'block/water',
            textures: { particle: `soulforge:block/fluid/${f.name}_still` },
        });
        saveJson(`blockstates/${f.name}.json`, { variants: { '': { model: `soulforge:block/${f.name}` } } });
    }

    // --- 其余方块状态：单变体 ---
    const SIMPLE_STATES = [...CUBE_ALL, 'casting_table', 'casting_basin'];
    for (const name of SIMPLE_STATES) {
        saveJson(`blockstates/${name}.json`, { variants: { '': { model: `soulforge:block/${name}` } } });
    }

    // --- 控制器方块状态：FACING × IN_STRUCTURE × ACTIVE 共 16 个变体 ---
    // active=true 用带发光核心的模型，active=false 用熄灭版本，否则结构状态在画面上没有区别。
    for (const name of Object.keys(CONTROLLERS)) {
        const variants = {};
        for (const [facing, y] of [['north', 0], ['east', 90], ['south', 180], ['west', 270]]) {
            for (const inStructure of [false, true]) {
                for (const active of [false, true]) {
                    variants[`facing=${facing},in_structure=${inStructure},active=${active}`] = {
                        model: active ? `soulforge:block/${name}_active` : `soulforge:block/${name}`,
                        y,
                    };
                }
            }
        }
        saveJson(`blockstates/${name}.json`, { variants });
        // active 模型：正面贴图换成发光版本
        saveJson(`models/block/${name}_active.json`, cubeModel({
            north: `${name}_front_active`, south: `${name}_front_active`,
            east: `${name}_side`, west: `${name}_side`,
            up: `${name}_top`, down: `${name}_side`,
        }));
    }

    // --- 纯物品（无方块）：只有物品模型 ---
    const PLAIN_ITEMS = [
        'glove', 'blunt_thermal_glove', 'magnetic_coil', 'vacuum_generator', 'magvac_generator',
        'nether_heart', 'trinity_alloy_ingot', 'ingot_cast', 'gem_cast',
        'netherite_bucket', 'trinity_alloy_bucket', 'magvac_bucket',
        'netherite_fuel_tank', 'trinity_alloy_fuel_tank', 'magvac_fuel_tank',
    ];
    for (const name of PLAIN_ITEMS) {
        saveJson(`models/item/${name}.json`, itemModel(name));
        saveJson(`items/${name}.json`, { model: { type: 'minecraft:model', model: `soulforge:item/${name}` } });
    }

    // --- 18 种装液桶：每种用自己的贴图，此前 19 个桶全部指向 item/netherite_bucket ---
    for (const f of fluids) {
        saveJson(`models/item/${f.name}_bucket.json`, itemModel(`${f.name}_bucket`));
        saveJson(`items/${f.name}_bucket.json`, { model: { type: 'minecraft:model', model: `soulforge:item/${f.name}_bucket` } });
    }
}

// ---------------------------------------------------------------- 输出

const written = [];

function save(canvas, relPath) {
    const abs = path.join(TEX, relPath);
    fs.mkdirSync(path.dirname(abs), { recursive: true });
    const buf = encodePNG(canvas.w, canvas.h, canvas.px);
    fs.writeFileSync(abs, buf);
    written.push({ rel: relPath, bytes: buf.length, w: canvas.w, h: canvas.h });
}

function saveMeta(relPath, obj) {
    const abs = path.join(TEX, relPath);
    fs.mkdirSync(path.dirname(abs), { recursive: true });
    fs.writeFileSync(abs, JSON.stringify(obj, null, 2) + '\n', 'utf8');
    written.push({ rel: relPath, bytes: Buffer.byteLength(JSON.stringify(obj)), w: 0, h: 0 });
}

/**
 * 写资源 JSON。
 * models/ 与 blockstates/ 在仓库里是 CRLF，items/ 是 LF；按各自目录的既有约定写，
 * 否则每次生成都会在 git 里产生整文件的 EOL 差异。
 */
const EOL = { 'models/': '\r\n', blockstates: '\r\n', items: '\n' };

function saveJson(relPath, obj) {
    const eol = relPath.startsWith('models/') ? EOL['models/'] : relPath.startsWith('blockstates') ? EOL.blockstates : EOL.items;
    const abs = path.join(ASSETS, relPath);
    fs.mkdirSync(path.dirname(abs), { recursive: true });
    const text = JSON.stringify(obj, null, 2).split('\n').join(eol) + eol;
    fs.writeFileSync(abs, text, 'utf8');
    written.push({ rel: relPath, bytes: Buffer.byteLength(text), w: 0, h: 0 });
}

function main() {
    const fluids = readFluids();

    // 方块：砖类
    save(bricks(P.smeltery), 'block/smeltery_bricks.png');
    save(bricks(P.netherite, 12), 'block/netherite_bricks.png');
    save(bricks(P.trinity, 13), 'block/trinity_alloy_bricks.png');
    save(bricks(P.singularity, 14), 'block/singularity_bricks.png');

    // 方块：金属块
    save(plate(P.superResistant), 'block/super_resistant_metal_block.png');
    save(plate(P.trinity, 22), 'block/trinity_alloy_block.png');
    save(plate(P.magvac, 23), 'block/magvac_alloy_block.png');

    // 方块：燃料罐（观察窗用对应合金色）
    save(tank(P.netherite, '#9a5cd8', 31), 'block/netherite_fuel_tank.png');
    save(tank(P.trinity, '#e0a0ff', 32), 'block/trinity_alloy_fuel_tank.png');
    save(tank(P.magvac, '#66d9ff', 33), 'block/magvac_fuel_tank.png');

    // 方块：五种控制器，各三面 + 工作状态正面
    for (const [name, cfg] of Object.entries(CONTROLLERS)) {
        save(controllerFront(cfg.p, cfg.glow, cfg.core), `block/${name}_front.png`);
        save(controllerFrontActive(cfg.p, cfg.glow, cfg.core), `block/${name}_front_active.png`);
        save(controllerSide(cfg.p), `block/${name}_side.png`);
        save(controllerTop(cfg.p, cfg.glow), `block/${name}_top.png`);
    }

    // 方块：铸造台 / 铸造盆
    save(castingTableTop(P.castingTable), 'block/casting_table_top.png');
    save(castingTableSide(P.castingTable), 'block/casting_table_side.png');
    save(castingBottom(P.castingTable), 'block/casting_table_bottom.png');
    save(castingBasinTop(P.castingBasin), 'block/casting_basin_top.png');
    save(castingBasinSide(P.castingBasin), 'block/casting_basin_side.png');
    save(castingBottom(P.castingBasin, 92), 'block/casting_basin_bottom.png');

    // GUI：主面板 + 侧栏槽位元素，取自匠魂3 原版贴图（见 GUI_REF 说明）
    copyGuiRef('heating_structure.png', 'gui/smeltery_controller.png');
    copyGuiRef('generic.png', 'gui/generic.png');

    // 物品
    for (const [name, canvas] of Object.entries(buildItems(fluids))) {
        save(canvas, `item/${name}.png`);
    }

    // 流体：静流 + 流动，各 8 帧竖排并附 .mcmeta
    fluids.forEach((f, i) => {
        save(fluidFrames(f.hex, 16, 8, 401 + i), `block/fluid/${f.name}_still.png`);
        saveMeta(`block/fluid/${f.name}_still.png.mcmeta`, { animation: { frametime: 3 } });
        save(fluidFlowFrames(f.hex, 8, 501 + i), `block/fluid/${f.name}_flowing.png`);
        saveMeta(`block/fluid/${f.name}_flowing.png.mcmeta`, { animation: { frametime: 3 } });
    });

    writeModels(fluids);

    const png = written.filter(w => w.rel.endsWith('.png'));
    const total = written.reduce((s, w) => s + w.bytes, 0);
    console.log(`写入 ${written.length} 个文件（${png.length} 个 PNG），共 ${total} 字节`);
    console.log(`  方块材质 ${written.filter(w => w.rel.startsWith('block/') && !w.rel.startsWith('block/fluid/')).length}`);
    console.log(`  流体材质 ${written.filter(w => w.rel.startsWith('block/fluid/')).length}（含 .mcmeta）`);
    console.log(`  物品材质 ${written.filter(w => w.rel.startsWith('item/')).length}`);
    console.log(`  GUI     ${written.filter(w => w.rel.startsWith('textures/gui/')).length}`);
}

main();
