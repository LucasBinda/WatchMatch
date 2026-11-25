package br.com.watchmatch.catalog.tmdb;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.web.client.RestClient;

import br.com.watchmatch.catalog.domain.Genre;
import br.com.watchmatch.catalog.domain.TitleType;

public class TmdbClient {

    private static final String BASE_URL = "https://api.themoviedb.org/3";

    private final RestClient restClient;

    // A chave é o token v4 do TMDB, que vai no cabeçalho e não na URL
    public TmdbClient(RestClient.Builder builder, String apiKey) {
        this.restClient = builder
                .baseUrl(BASE_URL)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    public TmdbPage discover(TitleType type, Map<String, String> params) {
        return restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/discover/" + type.name().toLowerCase());
                    params.forEach(uriBuilder::queryParam);
                    return uriBuilder.build();
                })
                .retrieve()
                .body(TmdbPage.class);
    }

    // id → nome em português (ex.: 35 → "Comédia")
    public Map<Integer, String> genres(TitleType type) {
        TmdbGenreList list = restClient.get()
                .uri("/genre/{type}/list?language=pt-BR", type.name().toLowerCase())
                .retrieve()
                .body(TmdbGenreList.class);

        return list.genres().stream().collect(Collectors.toMap(Genre::id, Genre::name));
    }

    // Uma chamada só traz detalhes + onde assistir + elenco + classificação.
    // Série também traz a 1ª temporada: a duração dos episódios sai dela (episode_run_time vem vazio)
    public TmdbDetail details(TitleType type, int id) {
        // Texto fixo na URL: por variável, a vírgula e a barra de "season/1" sairiam codificadas
        String uri = type == TitleType.MOVIE
                ? "/movie/{id}?language=pt-BR&append_to_response=watch/providers,credits,release_dates"
                : "/tv/{id}?language=pt-BR&append_to_response=watch/providers,credits,content_ratings,season/1";
        return restClient.get()
                .uri(uri, id)
                .retrieve()
                .body(TmdbDetail.class);
    }

    // Ids das palavras-chave do TMDB que batem com o texto (em inglês: "heist")
    public TmdbKeywordPage searchKeyword(String query) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/search/keyword").queryParam("query", query).build())
                .retrieve()
                .body(TmdbKeywordPage.class);
    }

    // Busca por nome em filmes e séries ao mesmo tempo ("Interestelar" acha pelo título em português)
    public TmdbMultiPage searchMulti(String query) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/search/multi")
                        .queryParam("query", query)
                        .queryParam("language", "pt-BR")
                        .queryParam("include_adult", "false")
                        .build())
                .retrieve()
                .body(TmdbMultiPage.class);
    }

    // Nome, gêneros e palavras-chave do título de referência
    public TmdbReference reference(TitleType type, int id) {
        return restClient.get()
                .uri("/{type}/{id}?language=pt-BR&append_to_response=keywords", type.name().toLowerCase(), id)
                .retrieve()
                .body(TmdbReference.class);
    }

    // "Quem gostou de X também gostou de": 20 por página, sem filtro de plataforma
    public TmdbPage recommendations(TitleType type, int id, int page) {
        return restClient.get()
                .uri("/{type}/{id}/recommendations?language=pt-BR&page={page}", type.name().toLowerCase(), id, page)
                .retrieve()
                .body(TmdbPage.class);
    }

    // Plataformas com catálogo no Brasil (a lista de filmes cobre as de séries também)
    public List<TmdbProvider> providers() {
        TmdbProviderList list = restClient.get()
                .uri("/watch/providers/movie?watch_region=BR&language=pt-BR")
                .retrieve()
                .body(TmdbProviderList.class);

        return list.results();
    }
}
