package br.com.watchmatch.catalog.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

// Cache em memória com tamanho máximo: quando enche, sai o que foi usado há mais tempo.
// As chaves vêm do texto dos usuários, então sem limite a memória cresceria para sempre.
// Guarda também o "não achei" (Optional.empty) para não perguntar de novo ao TMDB.
class LruCache<K, V> {

    private final Map<K, Optional<V>> entries;

    LruCache(int maxSize) {
        this.entries = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, Optional<V>> eldest) {
                return size() > maxSize;
            }
        };
    }

    // O cálculo (chamada ao TMDB) roda fora do lock: dois pedidos iguais ao mesmo tempo podem
    // calcular duas vezes, mas um TMDB lento não trava os outros pedidos
    Optional<V> get(K key, Function<K, Optional<V>> compute) {
        synchronized (entries) {
            Optional<V> cached = entries.get(key);
            if (cached != null) {
                return cached;
            }
        }
        Optional<V> value = compute.apply(key);
        synchronized (entries) {
            entries.put(key, value);
        }
        return value;
    }
}
