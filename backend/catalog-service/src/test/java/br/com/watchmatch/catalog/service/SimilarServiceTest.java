package br.com.watchmatch.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import br.com.watchmatch.catalog.domain.Criteria;
import br.com.watchmatch.catalog.domain.DiscoverRequest;
import br.com.watchmatch.catalog.domain.Genre;
import br.com.watchmatch.catalog.domain.SimilarRequest;
import br.com.watchmatch.catalog.domain.SimilarResult;
import br.com.watchmatch.catalog.domain.TitleDetail;
import br.com.watchmatch.catalog.domain.TitleList;
import br.com.watchmatch.catalog.domain.TitleSummary;
import br.com.watchmatch.catalog.domain.TitleType;
import br.com.watchmatch.catalog.domain.WatchInfo;
import br.com.watchmatch.catalog.domain.WatchProvider;
import br.com.watchmatch.catalog.tmdb.TmdbClient;
import br.com.watchmatch.catalog.tmdb.TmdbKeywordPage.TmdbKeyword;
import br.com.watchmatch.catalog.tmdb.TmdbPage;
import br.com.watchmatch.catalog.tmdb.TmdbReference;
import br.com.watchmatch.catalog.tmdb.TmdbResult;

/**
 * "Parecido com X" no modelo híbrido: recomendações do TMDB que estão nas plataformas, depois a busca
 * pelos gêneros e palavras-chave de X. Busca, discover e detalhes são falsos (Mockito).
 */
class SimilarServiceTest {

    private static final int NETFLIX = 8;
    private static final int INTERESTELAR = 157336;

    private final TmdbClient client = mock(TmdbClient.class);
    private final SearchService search = mock(SearchService.class);
    private final DiscoverService discover = mock(DiscoverService.class);
    private final TitleService titles = mock(TitleService.class);
    private final SimilarService service = new SimilarService(client, search, discover, titles);

    @BeforeEach
    void interestelarExiste() {
        when(search.search(eq("Interestelar"), eq(null), eq(1))).thenReturn(List.of(resumo(INTERESTELAR, "Interestelar")));
        when(client.reference(TitleType.MOVIE, INTERESTELAR)).thenReturn(new TmdbReference(INTERESTELAR,
                "Interestelar", null, List.of(new Genre(12, "Aventura"), new Genre(878, "Ficção científica")),
                new TmdbReference.Keywords(List.of(new TmdbKeyword(9882, "space travel"),
                        new TmdbKeyword(4565, "black hole")), null)));
        when(client.recommendations(TitleType.MOVIE, INTERESTELAR, 1)).thenReturn(new TmdbPage(1, 3, List.of(
                resultado(1), resultado(2), resultado(3))));
        when(discover.discover(any(), anyInt(), anyList()))
                .thenReturn(new TitleList(List.of(resumo(2, "Dois"), resumo(10, "Dez"), resumo(INTERESTELAR, "X")),
                        false));
    }

    @Test
    void recomendacoesNasPlataformasVemPrimeiroDepoisABuscaPelosGenerosEPalavras() {
        // O 2 não está em nenhuma plataforma do usuário; 1 e 3 estão na Netflix
        when(titles.lookup(anyList())).thenReturn(List.of(
                detalhe(1, NETFLIX, "flatrate", 100), detalhe(2, 337, "flatrate", 100), detalhe(3, NETFLIX, "ads", 100)));

        SimilarResult resultado = service.similar(pedido(Criteria.empty()), 40);

        assertThat(resultado.reference().title()).isEqualTo("Interestelar");
        // 1 e 3 das recomendações; 2 e 10 da busca; o próprio Interestelar sai
        assertThat(resultado.items()).extracting(TitleSummary::id).containsExactly(1, 3, 2, 10);
        assertThat(resultado.hasMore()).isTrue();

        var discoverPedido = ArgumentCaptor.forClass(DiscoverRequest.class);
        verify(discover).discover(discoverPedido.capture(), anyInt(), eq(List.of(9882, 4565)));
        assertThat(discoverPedido.getValue().criteria().genres()).containsExactly(12, 878);
        assertThat(discoverPedido.getValue().mediaType()).isEqualTo(TitleType.MOVIE);
    }

    @Test
    void aluguelECompraNaoContamComoDisponivel() {
        when(titles.lookup(anyList())).thenReturn(List.of(detalhe(1, NETFLIX, "rent", 100)));

        SimilarResult resultado = service.similar(pedido(Criteria.empty()), 40);

        assertThat(resultado.items()).extracting(TitleSummary::id).doesNotContain(1);
    }

    @Test
    void recomendacoesRespeitamOsCriteriosDoPedido() {
        when(titles.lookup(anyList())).thenReturn(List.of(
                detalhe(1, NETFLIX, "flatrate", 169), detalhe(3, NETFLIX, "flatrate", 95)));

        SimilarResult resultado = service.similar(pedido(Criteria.empty().withRuntimeMax(120)), 40);

        assertThat(resultado.items()).extracting(TitleSummary::id).containsExactly(3, 2, 10);
    }

    @Test
    void comTodosOsGenerosARecomendacaoPrecisaTerCadaUm() {
        // O 1 é só ficção científica; o 3 é ficção científica e aventura
        when(titles.lookup(anyList())).thenReturn(List.of(
                detalhe(1, NETFLIX, "flatrate", 100),
                detalheComGeneros(3, List.of(new Genre(878, "Ficção científica"), new Genre(12, "Aventura")))));

        var criterios = Criteria.empty().withGenres(List.of(878, 12)).withGenreMatch("all");
        SimilarResult resultado = service.similar(pedido(criterios), 40);

        assertThat(resultado.items()).extracting(TitleSummary::id).startsWith(3).doesNotContain(1);
    }

    @Test
    void generoEscolhidoPeloUsuarioVenceOsDaReferencia() {
        when(titles.lookup(anyList())).thenReturn(List.of());

        service.similar(pedido(Criteria.empty().withGenres(List.of(35))), 40);

        var discoverPedido = ArgumentCaptor.forClass(DiscoverRequest.class);
        verify(discover).discover(discoverPedido.capture(), anyInt(), anyList());
        assertThat(discoverPedido.getValue().criteria().genres()).containsExactly(35);
    }

    @Test
    void seriePedidaParecidaComUmFilmeUsaSoAsPalavrasChave() {
        var pedido = new SimilarRequest("Interestelar", TitleType.TV, Criteria.empty(), List.of(NETFLIX), 1);

        SimilarResult resultado = service.similar(pedido, 40);

        // A referência é o filme; as recomendações dele (filmes) não servem para quem pediu série
        assertThat(resultado.reference().mediaType()).isEqualTo(TitleType.MOVIE);
        verify(client, never()).recommendations(any(), anyInt(), anyInt());
        var discoverPedido = ArgumentCaptor.forClass(DiscoverRequest.class);
        verify(discover).discover(discoverPedido.capture(), anyInt(), eq(List.of(9882, 4565)));
        assertThat(discoverPedido.getValue().mediaType()).isEqualTo(TitleType.TV);
        assertThat(discoverPedido.getValue().criteria().genres()).isEmpty();
    }

    @Test
    void tituloNaoEncontradoVoltaSemReferencia() {
        when(search.search(eq("Filme Que Não Existe"), any(), eq(1))).thenReturn(List.of());
        var pedido = new SimilarRequest("Filme Que Não Existe", null, Criteria.empty(), List.of(NETFLIX), 1);

        SimilarResult resultado = service.similar(pedido, 40);

        assertThat(resultado.reference()).isNull();
        assertThat(resultado.items()).isEmpty();
        verify(discover, never()).discover(any(), anyInt(), anyList());
    }

    @Test
    void aReferenciaEBuscadaUmaVezSoEntreAsPaginas() {
        when(titles.lookup(anyList())).thenReturn(List.of());
        when(client.recommendations(TitleType.MOVIE, INTERESTELAR, 2)).thenReturn(new TmdbPage(2, 3, List.of()));

        service.similar(pedido(Criteria.empty()), 40);
        service.similar(new SimilarRequest("interestelar ", null, Criteria.empty(), List.of(NETFLIX), 2), 40);

        verify(search, times(1)).search(eq("Interestelar"), eq(null), eq(1));
        verify(client, times(1)).reference(TitleType.MOVIE, INTERESTELAR);
    }

    private static SimilarRequest pedido(Criteria criteria) {
        return new SimilarRequest("Interestelar", null, criteria, List.of(NETFLIX), 1);
    }

    private static TitleSummary resumo(int id, String titulo) {
        return new TitleSummary(id, TitleType.MOVIE, titulo, titulo, 2014, "", null, List.of(), 8.0, "en");
    }

    private static TmdbResult resultado(int id) {
        return new TmdbResult(id, "Filme " + id, null, null, null, "", null, "2015-01-01", null, 7.0, List.of(), "en");
    }

    private static TitleDetail detalheComGeneros(int id, List<Genre> generos) {
        return new TitleDetail(id, TitleType.MOVIE, "Filme " + id, "Filme " + id, 2015, "", null, generos, 7.0, "en",
                100, null, "12", null, List.of(), new WatchInfo(null, List.of(new WatchProvider(NETFLIX, "P", null,
                        "flatrate", null))));
    }

    private static TitleDetail detalhe(int id, int plataforma, String oferta, int duracao) {
        return new TitleDetail(id, TitleType.MOVIE, "Filme " + id, "Filme " + id, 2015, "", null,
                List.of(new Genre(878, "Ficção científica")), 7.0, "en", duracao, null, "12", null, List.of(),
                new WatchInfo(null, List.of(new WatchProvider(plataforma, "P", null, oferta, null))));
    }
}
