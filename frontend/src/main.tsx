import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { applyPalette, loadPalette } from './lib/palettes'

// Paleta escolhida no seletor (fica salva neste navegador): aplica antes do primeiro desenho
const saved = loadPalette()
if (saved) applyPalette(saved)

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
