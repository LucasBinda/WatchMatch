import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { applyPalette, loadPalette, paletteToolEnabled } from './lib/palettes'

// Paleta escolhida no seletor (só em desenvolvimento ou com ?paletas): aplica antes do primeiro desenho
if (paletteToolEnabled()) {
  const saved = loadPalette()
  if (saved) applyPalette(saved)
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
