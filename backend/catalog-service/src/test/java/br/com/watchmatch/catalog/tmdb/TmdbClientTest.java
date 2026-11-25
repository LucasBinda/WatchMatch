package br.com.watchmatch.catalog.tmdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import br.com.watchmatch.catalog.domain.TitleType;

/**
 * Faz as chamadas HTTP para a API do TMDB.
 * Nos testes, um TMDB falso (MockRestServiceServer) responde no lugar do real.
 */
class TmdbClientTest {

    // A ordem importa: o TMDB falso se liga no builder ANTES do TmdbClient usar o builder
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer tmdbFalso = MockRestServiceServer.bindTo(builder).build();
    private final TmdbClient client = new TmdbClient(builder, "chave-de-teste");

    @Test
    void discoverMandaOsParametrosEAChave() {
        tmdbFalso.expect(requestTo("https://api.themoviedb.org/3/discover/movie?watch_region=BR"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer chave-de-teste"))
                .andRespond(withSuccess("{\"page\":1,\"results\":[]}", MediaType.APPLICATION_JSON));

        client.discover(TitleType.MOVIE, Map.of("watch_region", "BR"));

        tmdbFalso.verify();
    }

    @Test
    void discoverTransformaARespostaEmTmdbPage() {
        tmdbFalso.expect(requestTo("https://api.themoviedb.org/3/discover/movie?watch_region=BR"))
                .andRespond(withSuccess("""
                    {"page": 1, "total_pages": 40, "results": [{
                        "id": 157336,
                        "title": "Interestelar",
                        "original_title": "Interstellar",
                        "overview": "Uma equipe de exploradores...",
                        "poster_path": "/abc.jpg",
                        "release_date": "2014-11-05",
                        "vote_average": 8.4,
                        "genre_ids": [12, 18, 878],
                        "original_language": "en",
                        "popularity": 99.9
                    }]}
                    """, MediaType.APPLICATION_JSON));

        TmdbPage page = client.discover(TitleType.MOVIE, Map.of("watch_region", "BR"));

        assertThat(page.totalPages()).isEqualTo(40);
        TmdbResult interestelar = page.results().get(0);
        assertThat(interestelar.id()).isEqualTo(157336);
        assertThat(interestelar.title()).isEqualTo("Interestelar");
        assertThat(interestelar.posterPath()).isEqualTo("/abc.jpg");
        assertThat(interestelar.releaseDate()).isEqualTo("2014-11-05");
        assertThat(interestelar.genreIds()).containsExactly(12, 18, 878);
    }

    @Test
    void genresDevolveOsNomesEmPortuguesPorId() {
        tmdbFalso.expect(requestTo("https://api.themoviedb.org/3/genre/tv/list?language=pt-BR"))
                .andRespond(withSuccess("""
                    {"genres": [{"id": 18, "name": "Drama"}, {"id": 35, "name": "Comédia"}]}
                    """, MediaType.APPLICATION_JSON));

        Map<Integer, String> generos = client.genres(TitleType.TV);

        assertThat(generos).containsExactlyInAnyOrderEntriesOf(Map.of(18, "Drama", 35, "Comédia"));
    }

    @Test
    void providersDevolveAsPlataformasDoBrasil() {
        tmdbFalso.expect(requestTo("https://api.themoviedb.org/3/watch/providers/movie?watch_region=BR&language=pt-BR"))
                .andRespond(withSuccess("""
                    {"results": [{"provider_id": 8, "provider_name": "Netflix", "logo_path": "/n.png",
                                  "display_priority": 3}]}
                    """, MediaType.APPLICATION_JSON));

        List<TmdbProvider> plataformas = client.providers();

        assertThat(plataformas).containsExactly(new TmdbProvider(8, "Netflix", "/n.png"));
    }

    @Test
    void detailsPedeOndeAssistirElencoEClassificacaoNumaChamadaSo() {
        tmdbFalso.expect(requestTo("https://api.themoviedb.org/3/movie/157336"
                        + "?language=pt-BR&append_to_response=watch/providers,credits,release_dates"))
                .andRespond(withSuccess("""
                    {"id": 157336, "title": "Interestelar", "runtime": 169,
                     "watch/providers": {"results": {"BR": {"link": "https://tmdb/watch",
                        "flatrate": [{"provider_id": 8, "provider_name": "Netflix", "logo_path": "/n.png"}]}}},
                     "release_dates": {"results": [{"iso_3166_1": "BR", "release_dates": [{"certification": "10"}]}]}}
                    """, MediaType.APPLICATION_JSON));

        TmdbDetail detalhe = client.details(TitleType.MOVIE, 157336);

        assertThat(detalhe.runtime()).isEqualTo(169);
        assertThat(detalhe.watchProviders().results().get("BR").flatrate())
                .containsExactly(new TmdbProvider(8, "Netflix", "/n.png"));
        assertThat(detalhe.releaseDates().results().get(0).releaseDates().get(0).certification()).isEqualTo("10");
    }

    @Test
    void detailsDeSeriePedeAClassificacaoDaTv() {
        tmdbFalso.expect(requestTo("https://api.themoviedb.org/3/tv/1396"
                        + "?language=pt-BR&append_to_response=watch/providers,credits,content_ratings,season/1"))
                .andRespond(withSuccess("{\"id\": 1396, \"name\": \"Breaking Bad\"}", MediaType.APPLICATION_JSON));

        assertThat(client.details(TitleType.TV, 1396).name()).isEqualTo("Breaking Bad");
    }

    @Test
    void buscaPalavraChavePeloTexto() {
        tmdbFalso.expect(requestTo("https://api.themoviedb.org/3/search/keyword?query=time%20travel"))
                .andRespond(withSuccess("{\"results\":[{\"id\":4379,\"name\":\"time travel\"}]}",
                        MediaType.APPLICATION_JSON));

        TmdbKeywordPage pagina = client.searchKeyword("time travel");

        assertThat(pagina.results()).extracting(TmdbKeywordPage.TmdbKeyword::id).containsExactly(4379);
        tmdbFalso.verify();
    }

    @Test
    void buscaPorNomeTrazOTipoDeCadaResultado() {
        tmdbFalso.expect(requestTo(
                "https://api.themoviedb.org/3/search/multi?query=Interestelar&language=pt-BR&include_adult=false"))
                .andRespond(withSuccess("""
                    {"results": [
                        {"id": 157336, "media_type": "movie", "title": "Interestelar", "genre_ids": [12, 878]},
                        {"id": 1, "media_type": "person", "name": "Alguém"}
                    ]}
                    """, MediaType.APPLICATION_JSON));

        TmdbMultiPage pagina = client.searchMulti("Interestelar");

        assertThat(pagina.results()).extracting(TmdbMultiPage.TmdbMultiResult::mediaType)
                .containsExactly("movie", "person");
        assertThat(pagina.results().get(0).toResult().genreIds()).containsExactly(12, 878);
    }

    @Test
    void palavrasChaveDaReferenciaDeFilmeEDeSerie() {
        tmdbFalso.expect(requestTo(
                "https://api.themoviedb.org/3/movie/157336?language=pt-BR&append_to_response=keywords"))
                .andRespond(withSuccess("""
                    {"id": 157336, "title": "Interestelar", "genres": [{"id": 878, "name": "Ficção científica"}],
                     "keywords": {"keywords": [{"id": 9882, "name": "space travel"}]}}
                    """, MediaType.APPLICATION_JSON));
        tmdbFalso.expect(requestTo("https://api.themoviedb.org/3/tv/1399?language=pt-BR&append_to_response=keywords"))
                .andRespond(withSuccess("""
                    {"id": 1399, "name": "Game of Thrones", "keywords": {"results": [{"id": 818, "name": "dragon"}]}}
                    """, MediaType.APPLICATION_JSON));

        TmdbReference filme = client.reference(TitleType.MOVIE, 157336);
        TmdbReference serie = client.reference(TitleType.TV, 1399);

        assertThat(filme.displayTitle()).isEqualTo("Interestelar");
        assertThat(filme.genreIds()).containsExactly(878);
        assertThat(filme.keywordIds()).containsExactly(9882);
        assertThat(serie.displayTitle()).isEqualTo("Game of Thrones");
        assertThat(serie.keywordIds()).containsExactly(818);
    }

    @Test
    void recomendacoesDeUmTitulo() {
        tmdbFalso.expect(requestTo("https://api.themoviedb.org/3/movie/157336/recommendations?language=pt-BR&page=2"))
                .andRespond(withSuccess("{\"page\":2,\"total_pages\":3,\"results\":[{\"id\":27205}]}",
                        MediaType.APPLICATION_JSON));

        TmdbPage pagina = client.recommendations(TitleType.MOVIE, 157336, 2);

        assertThat(pagina.results()).extracting(TmdbResult::id).containsExactly(27205);
        assertThat(pagina.totalPages()).isEqualTo(3);
    }
}
