package br.com.watchmatch.catalog.service;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import br.com.watchmatch.catalog.domain.TitleDetail;
import br.com.watchmatch.catalog.domain.TitleRef;
import br.com.watchmatch.catalog.domain.TitleType;
import br.com.watchmatch.catalog.tmdb.TitleMapper;
import br.com.watchmatch.catalog.tmdb.TmdbClient;

@Service
public class TitleService {

    // A mesma ficha é pedida de novo no "Carregar mais", na conferência de duração do /discover e nos
    // detalhes do resultado. As plataformas de um título mudam, mas não de hora em hora.
    static final int CACHE_SIZE = 2000;
    static final Duration CACHE_TTL = Duration.ofHours(6);

    private final TmdbClient client;
    private final TitleMapper mapper;
    private final LruCache<TitleRef, TitleDetail> cache;

    @Autowired
    public TitleService(TmdbClient client, TitleMapper mapper) {
        this(client, mapper, Clock.systemUTC());
    }

    // Os testes passam um relógio próprio para ver a validade do cache vencer
    TitleService(TmdbClient client, TitleMapper mapper, Clock clock) {
        this.client = client;
        this.mapper = mapper;
        this.cache = new LruCache<>(CACHE_SIZE, CACHE_TTL, clock);
    }

    // Título inexistente (404) não entra no cache: o erro sobe antes de guardar
    public TitleDetail get(TitleType type, int id) {
        return cache.get(new TitleRef(type, id),
                ref -> Optional.of(mapper.toDetail(client.details(type, id), type))).orElseThrow();
    }

    // Busca todos ao mesmo tempo (uma thread virtual por título): 48 títulos levam o tempo de ~1
    public List<TitleDetail> lookup(List<TitleRef> refs) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<TitleDetail>> futures = refs.stream()
                    .map(ref -> executor.submit(() -> getOrNull(ref)))
                    .toList();
            return futures.stream()
                    .map(this::await)
                    .filter(Objects::nonNull)
                    .toList();
        }
    }

    // Id inexistente some da resposta, como diz o contrato
    private TitleDetail getOrNull(TitleRef ref) {
        try {
            return get(ref.mediaType(), ref.id());
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    private TitleDetail await(Future<TitleDetail> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (java.util.concurrent.ExecutionException e) {
            // Repassa o erro original (ex.: TMDB fora do ar), que vira 502 no TmdbErrorHandler
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(e.getCause());
        }
    }
}
