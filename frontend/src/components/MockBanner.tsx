import { isMockMode } from '../api/client'

// Aviso impossível de ignorar: com o backend falso, nada na tela vem da IA nem do catálogo ao vivo
export function MockBanner() {
  if (!isMockMode) return null
  return (
    <div role="alert" className="bg-red-600 px-4 py-3 text-center text-white">
      <p className="text-4xl font-black tracking-widest sm:text-6xl">MOCK MODE</p>
      <p className="mt-1 text-sm font-medium sm:text-base">
        Dados de exemplo, sem IA. Para o backend real use <code className="font-mono">npm run dev:backend</code>.
      </p>
    </div>
  )
}
