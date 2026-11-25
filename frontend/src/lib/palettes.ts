// Paletas de cor trocadas ao vivo pelo seletor de paletas (botão no canto inferior direito, para todos).
// Todas as cores do site passam pelos tokens --color-* do index.css; aqui só trocamos os valores.
// Só cores: as fontes são sempre as do index.css, para a troca não mudar o tamanho dos textos.

export const TOKENS = [
  'page',
  'surface',
  'surface-2',
  'line',
  'ink',
  'muted',
  'faint',
  'brand',
  'brand-strong',
  'brand-soft',
  'star',
  'danger',
] as const

export type Token = (typeof TOKENS)[number]

export interface Palette {
  name: string
  scheme: 'dark' | 'light'
  colors: Record<Token, string>
}

function dark(name: string, c: Omit<Palette['colors'], 'star' | 'danger'> & Partial<Palette['colors']>): Palette {
  return { name, scheme: 'dark', colors: { star: '#f5c451', danger: '#f0857a', ...c } }
}

function light(name: string, c: Omit<Palette['colors'], 'star' | 'danger'> & Partial<Palette['colors']>): Palette {
  return { name, scheme: 'light', colors: { star: '#c98a12', danger: '#c2412d', ...c } }
}

export const PALETTES: Palette[] = [
  // A primeira é a padrão do site (a mesma do @theme do index.css)
  dark('Noite magenta (padrão)', {
    page: 'hsl(235 22% 6%)', surface: 'hsl(235 20% 9%)', 'surface-2': 'hsl(235 18% 12%)', line: 'hsl(235 16% 19%)',
    ink: 'hsl(235 25% 93%)', muted: 'hsl(235 14% 66%)', faint: 'hsl(235 10% 47%)',
    brand: 'hsl(335 80% 64%)', 'brand-strong': 'hsl(335 70% 54%)', 'brand-soft': 'hsl(335 85% 77%)',
  }),
  dark('Azul noite', {
    page: '#0e141b', surface: '#151d26', 'surface-2': '#1b2531', line: '#243241',
    ink: '#e6edf3', muted: '#8fa1b3', faint: '#62758a',
    brand: '#4fb3a5', 'brand-strong': '#3b998c', 'brand-soft': '#7fd0c4',
  }),
  dark('Sala de cinema', {
    page: '#100d0b', surface: '#17130f', 'surface-2': '#1f1a15', line: '#33291f',
    ink: '#f3ece2', muted: '#b3a594', faint: '#7d6e60',
    brand: '#f2a93b', 'brand-strong': '#d98f22', 'brand-soft': '#f7c77a', danger: '#e0675a',
  }),
  dark('Neon noturno', {
    page: '#0c0a1a', surface: '#131029', 'surface-2': '#1a1636', line: '#2c2656',
    ink: '#ece9ff', muted: '#a39dd0', faint: '#6f68a3',
    brand: '#a78bfa', 'brand-strong': '#8b6cf0', 'brand-soft': '#c9b8ff', danger: '#f472b6',
  }),
  light('Editorial claro', {
    page: '#f6f2ea', surface: '#fffdf9', 'surface-2': '#efe8dc', line: '#ddd3c3',
    ink: '#1d1a16', muted: '#6e665b', faint: '#9a9083',
    brand: '#d94f27', 'brand-strong': '#b83f1c', 'brand-soft': '#e4572e',
  }),
  dark('Pipoca', {
    page: '#120b0b', surface: '#1b1111', 'surface-2': '#251616', line: '#3d2222',
    ink: '#fbeee6', muted: '#c4a69b', faint: '#8a6a60',
    brand: '#ffcc33', 'brand-strong': '#f0b400', 'brand-soft': '#ffe08a', danger: '#ff6b5b',
  }),
  dark('Floresta', {
    page: '#0b110d', surface: '#111a14', 'surface-2': '#17231b', line: '#253629',
    ink: '#e8efe6', muted: '#9fb09f', faint: '#6a7d6b',
    brand: '#d9a441', 'brand-strong': '#bf8a2a', 'brand-soft': '#ebc47b',
  }),
  dark('Oceano', {
    page: '#08131a', surface: '#0d1c25', 'surface-2': '#122631', line: '#1e3a48',
    ink: '#e4f1f6', muted: '#90adbb', faint: '#5e7d8c',
    brand: '#ff7a59', 'brand-strong': '#ee5f3c', 'brand-soft': '#ffa58e',
  }),
  dark('Grafite e lima', {
    page: '#0f0f10', surface: '#161618', 'surface-2': '#1d1d20', line: '#2c2c31',
    ink: '#f0f0f2', muted: '#a1a1aa', faint: '#6b6b74',
    brand: '#c6f432', 'brand-strong': '#a8d61a', 'brand-soft': '#dcfa7d',
  }),
  dark('Vinho', {
    page: '#140a0e', surface: '#1d0f15', 'surface-2': '#27141c', line: '#40202d',
    ink: '#f7e9ee', muted: '#c49dab', faint: '#8b6574',
    brand: '#f28cb1', 'brand-strong': '#e26b97', 'brand-soft': '#f8b6ce', danger: '#ff8a7a',
  }),
  light('Areia', {
    page: '#f4efe6', surface: '#fbf8f2', 'surface-2': '#ebe3d5', line: '#d8ccb8',
    ink: '#2a241c', muted: '#74695a', faint: '#a29683',
    brand: '#2f6f5e', 'brand-strong': '#245849', 'brand-soft': '#3d8a75',
  }),
  dark('Aurora', {
    page: '#070d14', surface: '#0c1520', 'surface-2': '#111d2b', line: '#1c2d42',
    ink: '#e7f4fb', muted: '#8fb0c4', faint: '#5a7a8f',
    brand: '#3ee6d1', 'brand-strong': '#1fcab5', 'brand-soft': '#8af2e5', danger: '#c58bff',
  }),
  dark('Preto e vermelho', {
    page: '#0a0a0a', surface: '#121212', 'surface-2': '#1a1a1a', line: '#2a2a2a',
    ink: '#f5f5f5', muted: '#a3a3a3', faint: '#6e6e6e',
    brand: '#e50914', 'brand-strong': '#c10710', 'brand-soft': '#ff4d55', danger: '#ff8a65',
  }),
]

const hsl = (h: number, s: number, l: number) => `hsl(${Math.round(h) % 360} ${s}% ${l}%)`

// Paleta nova a partir de dois matizes sorteados: fundo levemente tingido e um destaque saturado
export function generatePalette(): Palette {
  const base = Math.random() * 360
  const accent = (base + 90 + Math.random() * 180) % 360
  // Sempre escura: um fundo claro sorteado de surpresa ofusca quem está no escuro. A claridade do fundo
  // varia entre 4% e 12% (quase preto a cinza-escuro tingido); superfícies e borda sobem a partir dela
  const page = 4 + Math.random() * 8
  const name = `Gerada ${Math.round(base)}°/${Math.round(accent)}°`
  return dark(name, {
    page: hsl(base, 22, page), surface: hsl(base, 20, page + 3), 'surface-2': hsl(base, 18, page + 6),
    line: hsl(base, 16, page + 13),
    ink: hsl(base, 25, 93), muted: hsl(base, 14, 66), faint: hsl(base, 10, 47),
    brand: hsl(accent, 80, 64), 'brand-strong': hsl(accent, 70, 54), 'brand-soft': hsl(accent, 85, 77),
  })
}

const STORAGE_KEY = 'watchmatch.palette'

// O favicon é o mesmo símbolo do cabeçalho (o "play" da lucide), e muda de cor junto com a paleta.
// public/favicon.svg é a versão fixa, com as cores da paleta padrão
const PLAY_PATH = 'M5 5a2 2 0 0 1 3.008-1.728l11.997 6.998a2 2 0 0 1 .003 3.458l-12 7A2 2 0 0 1 5 19z'

function setFavicon(page: string, brand: string) {
  const svg =
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32"><rect width="32" height="32" rx="8" fill="${page}"/>` +
    `<path transform="translate(4 4)" d="${PLAY_PATH}" fill="${brand}" stroke="${brand}" stroke-width="2" ` +
    `stroke-linejoin="round"/></svg>`
  const link = document.querySelector<HTMLLinkElement>('link[rel="icon"]')
  if (link) link.href = `data:image/svg+xml,${encodeURIComponent(svg)}`
}

export function applyPalette(palette: Palette) {
  const root = document.documentElement
  for (const token of TOKENS) root.style.setProperty(`--color-${token}`, palette.colors[token])
  setFavicon(palette.colors.page, palette.colors.brand)
  root.style.colorScheme = palette.scheme
}

export function savePalette(palette: Palette) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(palette))
  } catch {
    // Navegação privada ou armazenamento cheio: a troca vale só nesta aba
  }
}

export function loadPalette(): Palette | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as Palette) : null
  } catch {
    return null
  }
}

export function clearPalette() {
  try {
    localStorage.removeItem(STORAGE_KEY)
  } catch {
    // nada a limpar
  }
  const root = document.documentElement
  for (const token of TOKENS) root.style.removeProperty(`--color-${token}`)
  root.style.removeProperty('color-scheme')
  // Só remover as variáveis não basta: o Chrome recalcula o estilo, mas não repinta na hora alguns
  // elementos (botões e bolinhas ficavam com a cor antiga até recarregar). Lê as cores do index.css,
  // agora que nada as sobrepõe, e define de volta: o mesmo caminho do "Próxima", que repinta na hora.
  // Na próxima visita nada é aplicado, porque o navegador não guarda mais paleta
  const css = getComputedStyle(root)
  for (const token of TOKENS) {
    const value = css.getPropertyValue(`--color-${token}`).trim()
    if (value) root.style.setProperty(`--color-${token}`, value)
  }
  setFavicon(css.getPropertyValue('--color-page').trim(), css.getPropertyValue('--color-brand').trim())
}

// O bloco @theme para colar no index.css quando gostar de uma paleta
export function paletteToCss(palette: Palette): string {
  const lines = TOKENS.map((t) => `  --color-${t}: ${palette.colors[t]};`)
  return [`/* ${palette.name} */`, ...lines].join('\n')
}
