// Preferências do usuário salvas só no navegador (sem cadastro).
// try/catch: o localStorage pode estar bloqueado (modo anônimo).
import { PAGE_SIZES } from './searchForm'
import type { AiMode, PageSize } from '../types'

const PROVIDERS_KEY = 'watchmatch.providers'
const PAGE_SIZE_KEY = 'watchmatch.pageSize'
const AI_MODE_KEY = 'watchmatch.aiMode'

const DEFAULT_PROVIDERS = [8, 119] // Netflix e Prime Video
const DEFAULT_PAGE_SIZE: PageSize = 24
const DEFAULT_AI_MODE: AiMode = 'pick'

function load<T>(key: string, valid: (value: unknown) => value is T, fallback: T): T {
  try {
    const saved: unknown = JSON.parse(localStorage.getItem(key) ?? 'null')
    return valid(saved) ? saved : fallback
  } catch {
    return fallback
  }
}

function save(key: string, value: unknown) {
  try {
    localStorage.setItem(key, JSON.stringify(value))
  } catch {
    // Sem localStorage a preferência só vale até fechar a aba
  }
}

const isProviders = (v: unknown): v is number[] => Array.isArray(v) && v.every((id) => typeof id === 'number')
const isPageSize = (v: unknown): v is PageSize => PAGE_SIZES.includes(v as PageSize)
const isAiMode = (v: unknown): v is AiMode => v === 'pick' || v === 'filters'

export const loadProviders = () => load(PROVIDERS_KEY, isProviders, DEFAULT_PROVIDERS)
export const saveProviders = (providers: number[]) => save(PROVIDERS_KEY, providers)

export const loadPageSize = () => load(PAGE_SIZE_KEY, isPageSize, DEFAULT_PAGE_SIZE)
export const savePageSize = (size: PageSize) => save(PAGE_SIZE_KEY, size)

export const loadAiMode = () => load(AI_MODE_KEY, isAiMode, DEFAULT_AI_MODE)
export const saveAiMode = (mode: AiMode) => save(AI_MODE_KEY, mode)
