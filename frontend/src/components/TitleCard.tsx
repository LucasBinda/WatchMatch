import { Clapperboard, Star } from 'lucide-react'
import { formatCertification, formatRuntime } from '../lib/format'
import type { Recommendation } from '../types'

interface Props {
  title: Recommendation
  onOpen: () => void
}

// Pôster em destaque: título, ano, duração e nota ficam sobre um degradê na parte de baixo do pôster.
// A justificativa da IA vem embaixo, em texto menor. O pôster inteiro abre os detalhes
export function TitleCard({ title, onOpen }: Props) {
  const genres = (title.genres ?? []).slice(0, 2)
  const length =
    title.mediaType === 'movie' && title.runtime
      ? formatRuntime(title.runtime)
      : title.mediaType === 'tv'
        ? title.seasons
          ? `${title.seasons} temporada${title.seasons > 1 ? 's' : ''}`
          : 'Série'
        : null
  const facts = [title.year, length].filter(Boolean).join(' · ')

  return (
    <article className="group flex flex-col">
      <button
        type="button"
        onClick={onOpen}
        aria-label={`Ver detalhes de ${title.title}`}
        className="relative block aspect-[2/3] overflow-hidden rounded-xl bg-surface-2 text-left ring-1 ring-line transition duration-300 group-hover:-translate-y-1 group-hover:shadow-2xl group-hover:shadow-black/50 group-hover:ring-brand/70 focus-visible:ring-2 focus-visible:ring-brand focus-visible:outline-none"
      >
        {title.posterUrl ? (
          <img
            src={title.posterUrl}
            alt=""
            loading="lazy"
            className="size-full object-cover transition duration-500 group-hover:scale-[1.04]"
          />
        ) : (
          <div className="grid size-full place-items-center text-faint">
            <Clapperboard className="size-12" />
          </div>
        )}

        {title.certification && (
          <span className="absolute top-2.5 left-2.5 rounded-md bg-black/70 px-1.5 py-0.5 text-[11px] font-bold text-white backdrop-blur">
            {formatCertification(title.certification)}
          </span>
        )}

        <div className="absolute top-2.5 right-2.5 flex gap-1">
          {title.watch.providers.map((p) =>
            p.logoUrl ? (
              <img key={p.id} src={p.logoUrl} alt={p.name} title={p.name} className="size-7 rounded-md shadow-lg ring-1 ring-black/40" />
            ) : null,
          )}
        </div>

        {/* Sobre o pôster o texto é sempre claro, em qualquer paleta: o degradê garante o contraste */}
        <div className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/95 via-black/70 to-transparent px-3 pt-14 pb-3 text-white">
          <h3 className="font-display text-base leading-snug font-bold sm:text-lg">{title.title}</h3>
          <p className="mt-1 flex flex-wrap items-center gap-x-2 text-xs text-white/75">
            {facts && <span>{facts}</span>}
            {title.voteAverage ? (
              <span className="flex items-center gap-1 text-white">
                <Star className="size-3.5 fill-star text-star" />
                {title.voteAverage.toFixed(1)}
              </span>
            ) : null}
          </p>
          {genres.length > 0 && <p className="mt-0.5 truncate text-xs text-white/60">{genres.map((g) => g.name).join(' · ')}</p>}
        </div>
      </button>

      {title.reason && <p className="mt-2.5 line-clamp-4 px-0.5 text-sm leading-relaxed text-muted">{title.reason}</p>}
    </article>
  )
}
