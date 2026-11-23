package br.com.watchmatch.catalog.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

// Cache em memória com tamanho máximo: quando enche, sai o que foi usado há mais tempo.
// As chaves vêm do texto dos usuários, então sem limite a memória cresceria para sempre.
// Guarda também o "não achei" (Optional.empty) para não perguntar de novo ao TMDB.
// Com validade (ttl), uma entrada mais velha que isso é buscada de novo: serve para dados que mudam,
// como as plataformas de um título.
class LruCache<K, V> {

    private record Stored<V>(Optional<V> value, Instant storedAt) {
    }

    private final Map<K, Stored<V>> entries;
    private final Duration ttl;
    private final Clock clock;

    // Sem validade: a entrada só sai quando o cache enche
    LruCache(int maxSize) {
        this(maxSize, null, Clock.systemUTC());
    }

    LruCache(int maxSize, Duration ttl, Clock clock) {
        this.ttl = ttl;
        this.clock = clock;
        this.entries = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, Stored<V>> eldest) {
                return size() > maxSize;
            }
        };
    }

    // O cálculo (chamada ao TMDB) roda fora do lock: dois pedidos iguais ao mesmo tempo podem
    // calcular duas vezes, mas um TMDB lento não trava os outros pedidos
    Optional<V> get(K key, Function<K, Optional<V>> compute) {
        synchronized (entries) {
            Stored<V> cached = entries.get(key);
            if (cached != null && !expired(cached)) {
                return cached.value();
            }
        }
        Optional<V> value = compute.apply(key);
        synchronized (entries) {
            entries.put(key, new Stored<>(value, clock.instant()));
        }
        return value;
    }

    private boolean expired(Stored<V> entry) {
        return ttl != null && entry.storedAt().plus(ttl).isBefore(clock.instant());
    }
}
