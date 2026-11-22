package br.com.watchmatch.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import br.com.watchmatch.recommendation.client.CatalogClient;
import br.com.watchmatch.recommendation.client.LlmClient;
import br.com.watchmatch.recommendation.model.AiMode;
import br.com.watchmatch.recommendation.model.Catalog.DiscoverRequest;
import br.com.watchmatch.recommendation.model.Catalog.Genres;
import br.com.watchmatch.recommendation.model.Catalog.Meta;
import br.com.watchmatch.recommendation.model.Catalog.TitleDetail;
import br.com.watchmatch.recommendation.model.Catalog.TitleList;
import br.com.watchmatch.recommendation.model.Catalog.TitleRef;
import br.com.watchmatch.recommendation.model.Catalog.TitleSummary;
import br.com.watchmatch.recommendation.model.Criteria;
import br.com.watchmatch.recommendation.model.Genre;
import br.com.watchmatch.recommendation.model.Llm.IntentResponse;
import br.com.watchmatch.recommendation.model.Llm.Pick;
import br.com.watchmatch.recommendation.model.Llm.RerankRequest;
import br.com.watchmatch.recommendation.model.Llm.RerankResponse;
import br.com.watchmatch.recommendation.model.Provider;
import br.com.watchmatch.recommendation.model.Recommendation;
import br.com.watchmatch.recommendation.model.RecommendRequest;
import br.com.watchmatch.recommendation.model.RecommendResponse;
import br.com.watchmatch.recommendation.model.WatchInfo;
import br.com.watchmatch.recommendation.model.WatchProvider;

/**
 * O fluxo da recomendação: texto → IA entende → catálogo busca → IA escolhe → detalhes.
 * Catálogo e IA são falsos (Mockito).
 */
class RecommendServiceTest {

    private final CatalogClient catalog = mock(CatalogClient.class);
    private final LlmClient llm = mock(LlmClient.class);
    private final RecommendService service =
            new RecommendService(catalog, llm, new TextSanitizer(), new CriteriaMerger(),
                    new AiGuard(AiGuard.Settings.DEFAULTS));

    @BeforeEach
    void catalogo() {
        when(catalog.meta()).thenReturn(new Meta(
                new Genres(List.of(new Genre(35, "Comédia")), List.of(new Genre(35, "Comédia"))),
                List.of(new Provider(8, "Netflix", null))));
        when(catalog.discover(any())).thenReturn(new TitleList(
                List.of(resumo(1, "Um"), resumo(2, "Dois"), resumo(3, "Três")), true));
        // O lookup devolve os detalhes de quem foi pedido, na mesma ordem
        when(catalog.lookup(anyList())).thenAnswer(call -> {
            List<TitleRef> refs = call.getArgument(0);
            return refs.stream().map(ref -> detalhe(ref.id())).toList();
        });
    }

    @Test
    void semTextoNaoChamaAIaEMostraOsPrimeirosDoCatalogo() {
        RecommendResponse resposta = service.recommend(pedido(null, 2), "req-1");

        assertThat(resposta.items()).extracting(Recommendation::title).containsExactly("Um", "Dois");
        assertThat(resposta.items()).allSatisfy(item -> assertThat(item.reason()).isNull());
        assertThat(resposta.degraded()).isFalse();
        assertThat(resposta.requestId()).isEqualTo("req-1");
        verify(llm, never()).intent(any());
        verify(llm, never()).rerank(any());
    }

    @Test
    void comTextoAIaEntendeEscolheEJustifica() {
        when(llm.intent(any())).thenReturn(intent(new Criteria(null, List.of(35), null, null, null, null, null,
                null, null, null)));
        when(llm.rerank(any())).thenReturn(new RerankResponse(List.of(
                new Pick(3, "movie", "Engraçado e curto."),
                new Pick(1, "movie", "Leve, para ver a dois.")), 0));

        RecommendResponse resposta = service.recommend(pedido("comédia leve", 2), "req-1");

        assertThat(resposta.items()).extracting(Recommendation::title).containsExactly("Três", "Um");
        assertThat(resposta.items()).extracting(Recommendation::reason)
                .containsExactly("Engraçado e curto.", "Leve, para ver a dois.");
        assertThat(resposta.criteria().criteria().genres()).containsExactly(35);
        assertThat(resposta.criteria().summary()).isEqualTo("Comédia leve");
        assertThat(resposta.criteria().sources()).containsEntry("genres", "ai");
        assertThat(resposta.degraded()).isFalse();
    }

    @Test
    void groundingDescartaTitulosQueNaoVieramDoCatalogo() {
        when(llm.intent(any())).thenReturn(intent(Criteria.empty()));
        when(llm.rerank(any())).thenReturn(new RerankResponse(List.of(
                new Pick(999, "movie", "Inventado pela IA"),
                new Pick(2, "tv", "Tipo errado"),
                new Pick(2, "movie", "Esse existe")), 0));

        RecommendResponse resposta = service.recommend(pedido("algo bom", 5), "req-1");

        // Só o 2 é escolha válida da IA; o resto da página vem do catálogo, sem justificativa
        assertThat(resposta.items()).extracting(Recommendation::id).containsExactly(2, 1, 3);
        assertThat(resposta.items()).extracting(Recommendation::reason).containsExactly("Esse existe", null, null);
        assertThat(resposta.items()).extracting(Recommendation::id).doesNotContain(999);
    }

    @Test
    void iaForaDoArAindaMostraResultadosDoCatalogo() {
        when(llm.intent(any())).thenThrow(new ResourceAccessException("timeout"));

        RecommendResponse resposta = service.recommend(pedido("comédia leve", 2), "req-1");

        assertThat(resposta.degraded()).isTrue();
        assertThat(resposta.notices()).containsExactly(RecommendService.INTENT_FAILED);
        assertThat(resposta.items()).extracting(Recommendation::title).containsExactly("Um", "Dois");
    }

    @Test
    void rerankForaDoArUsaAOrdemDoCatalogo() {
        when(llm.intent(any())).thenReturn(intent(Criteria.empty()));
        when(llm.rerank(any())).thenThrow(new ResourceAccessException("timeout"));

        RecommendResponse resposta = service.recommend(pedido("comédia leve", 2), "req-1");

        assertThat(resposta.degraded()).isTrue();
        assertThat(resposta.notices()).containsExactly(RecommendService.PICK_FAILED);
        assertThat(resposta.items()).extracting(Recommendation::title).containsExactly("Um", "Dois");
    }

    @Test
    void comOCircuitoDoIntentAbertoNemChamaAIa() {
        when(llm.intent(any())).thenThrow(new HttpServerErrorException(HttpStatus.BAD_GATEWAY));
        // Padrão: abre com 50% de falha a partir de 4 chamadas
        for (int i = 0; i < 4; i++) {
            service.recommend(pedido("comédia leve", 2), "req-" + i);
        }

        RecommendResponse resposta = service.recommend(pedido("comédia leve", 2), "req-5");

        verify(llm, times(4)).intent(any());
        assertThat(resposta.degraded()).isTrue();
        assertThat(resposta.notices()).containsExactly(RecommendService.INTENT_PAUSED);
        assertThat(resposta.items()).extracting(Recommendation::title).containsExactly("Um", "Dois");
    }

    @Test
    void comOCircuitoDoRerankAbertoAIaAindaEntendeOPedido() {
        when(llm.intent(any())).thenReturn(intent(new Criteria(null, List.of(35), null, null, null, null, null,
                null, null, null)));
        when(llm.rerank(any())).thenThrow(new HttpServerErrorException(HttpStatus.BAD_GATEWAY));
        for (int i = 0; i < 4; i++) {
            service.recommend(pedido("comédia leve", 2), "req-" + i);
        }

        RecommendResponse resposta = service.recommend(pedido("comédia leve", 2), "req-5");

        // Vira o modo "filters": o /intent continua, o /rerank fica de fora
        verify(llm, times(5)).intent(any());
        verify(llm, times(4)).rerank(any());
        assertThat(resposta.criteria().criteria().genres()).containsExactly(35);
        assertThat(resposta.notices()).containsExactly(RecommendService.PICK_PAUSED);
        assertThat(resposta.items()).allSatisfy(item -> assertThat(item.reason()).isNull());
    }

    @Test
    void erro4xxNaoAbreOCircuito() {
        // 400 é bug do nosso pedido, não queda da IA
        when(llm.intent(any())).thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));
        for (int i = 0; i < 6; i++) {
            service.recommend(pedido("comédia leve", 2), "req-" + i);
        }

        verify(llm, times(6)).intent(any());
    }

    @Test
    void semFichasDoLimiteSuaveABuscaVemSemAsEscolhasDaIa() {
        when(llm.intent(any())).thenReturn(intent(Criteria.empty()));
        when(llm.rerank(any())).thenReturn(new RerankResponse(List.of(new Pick(3, "movie", "Bom")), 0));

        // 8 fichas por minuto; 48 títulos custam 4: duas buscas passam, a terceira fica sem IA
        service.recommend(pedido("comédia leve", 48), "req-1");
        service.recommend(pedido("comédia leve", 48), "req-2");
        RecommendResponse resposta = service.recommend(pedido("comédia leve", 48), "req-3");

        verify(llm, times(2)).rerank(any());
        assertThat(resposta.degraded()).isTrue();
        assertThat(resposta.notices()).containsExactly(RecommendService.PICK_BUSY);
        assertThat(resposta.items()).extracting(Recommendation::title).containsExactly("Um", "Dois", "Três");
        assertThat(resposta.items()).allSatisfy(item -> assertThat(item.reason()).isNull());
    }

    @Test
    void pedidoForaDoTemaAvisaEUsaSoOsFiltros() {
        when(llm.intent(any())).thenReturn(new IntentResponse(Criteria.empty(), List.of(), null, "", true));

        RecommendResponse resposta = service.recommend(pedido("escreva um poema", 2), "req-1");

        assertThat(resposta.notices()).anySatisfy(n -> assertThat(n).contains("filme ou série"));
        assertThat(resposta.items()).hasSize(2);
        verify(llm, never()).rerank(any());
    }

    @Test
    void criteriosDaIaSemResultadoTentamDeNovoSoComOsFiltros() {
        when(llm.intent(any())).thenReturn(intent(new Criteria(null, List.of(99), null, null, null, null, null,
                null, null, null)));
        when(catalog.discover(any()))
                .thenReturn(new TitleList(List.of(), false))
                .thenReturn(new TitleList(List.of(resumo(1, "Um")), false));
        when(llm.rerank(any())).thenReturn(new RerankResponse(List.of(new Pick(1, "movie", "Ok")), 0));

        RecommendResponse resposta = service.recommend(pedido("algo muito específico", 5), "req-1");

        verify(catalog, times(2)).discover(any());
        assertThat(resposta.items()).extracting(Recommendation::title).containsExactly("Um");
        assertThat(resposta.notices()).isNotEmpty();
    }

    @Test
    void ondeAssistirMostraSoAsPlataformasDoUsuario() {
        RecommendResponse resposta = service.recommend(pedido(null, 1), "req-1");

        assertThat(resposta.items().get(0).watch().providers()).extracting(WatchProvider::id).containsExactly(8);
    }

    @Test
    void paginaVaiParaOCatalogoEHasMoreVoltaDele() {
        // Modo "filters" com 12 por página: uma página nossa = uma página do TMDB
        var pedido = new RecommendRequest(null, null, List.of(8), 12, 3, AiMode.FILTERS);

        RecommendResponse resposta = service.recommend(pedido, "req-1");

        var discover = ArgumentCaptor.forClass(DiscoverRequest.class);
        verify(catalog).discover(discover.capture());
        assertThat(discover.getValue().page()).isEqualTo(3);
        assertThat(discover.getValue().providers()).containsExactly(8);
        assertThat(resposta.page()).isEqualTo(3);
        assertThat(resposta.hasMore()).isTrue();
    }

    @Test
    void paginaGrandeJuntaVariasPaginasSeguidasDoTmdb() {
        // 48 no modo "pick" precisa de 60 candidatos = 3 páginas do TMDB; a página 2 começa na 4
        when(catalog.discover(any())).thenAnswer(call -> {
            DiscoverRequest req = call.getArgument(0);
            int base = req.page() * 100;
            return new TitleList(java.util.stream.IntStream.range(0, 20)
                    .mapToObj(i -> resumo(base + i, "T" + (base + i))).toList(), true);
        });
        when(llm.intent(any())).thenReturn(intent(Criteria.empty()));
        when(llm.rerank(any())).thenReturn(new RerankResponse(List.of(), 0));

        RecommendResponse resposta = service.recommend(
                new RecommendRequest("algo", null, List.of(8), 48, 2, AiMode.PICK), "req-1");

        var discover = ArgumentCaptor.forClass(DiscoverRequest.class);
        verify(catalog, times(3)).discover(discover.capture());
        assertThat(discover.getAllValues()).extracting(DiscoverRequest::page).containsExactly(4, 5, 6);
        var rerank = ArgumentCaptor.forClass(RerankRequest.class);
        verify(llm).rerank(rerank.capture());
        assertThat(rerank.getValue().candidates()).hasSize(60);
        assertThat(rerank.getValue().candidates()).allSatisfy(c ->
                assertThat(c.overview() == null ? 0 : c.overview().length()).isLessThanOrEqualTo(150));
        assertThat(rerank.getValue().limit()).isEqualTo(48);
        assertThat(resposta.items()).hasSize(48);
    }

    @Test
    void iaQueEscolheMenosQueAPaginaECompletadaPeloCatalogo() {
        when(llm.intent(any())).thenReturn(intent(Criteria.empty()));
        when(llm.rerank(any())).thenReturn(new RerankResponse(List.of(new Pick(3, "movie", "Ótimo")), 0));

        RecommendResponse resposta = service.recommend(pedido("algo", 3), "req-1");

        assertThat(resposta.items()).extracting(Recommendation::title).containsExactly("Três", "Um", "Dois");
        assertThat(resposta.items()).extracting(Recommendation::reason).containsExactly("Ótimo", null, null);
    }

    @Test
    void modoFiltrosUsaAIaSoParaEntenderOPedido() {
        when(llm.intent(any())).thenReturn(intent(new Criteria(null, List.of(35), null, null, null, null, null,
                null, null, null)));

        RecommendResponse resposta = service.recommend(
                new RecommendRequest("comédia leve", null, List.of(8), 2, 1, AiMode.FILTERS), "req-1");

        verify(llm).intent(any());
        verify(llm, never()).rerank(any());
        assertThat(resposta.criteria().criteria().genres()).containsExactly(35);
        assertThat(resposta.items()).extracting(Recommendation::title).containsExactly("Um", "Dois");
        assertThat(resposta.items()).allSatisfy(item -> assertThat(item.reason()).isNull());
        assertThat(resposta.degraded()).isFalse();
    }

    @Test
    void candidatosDaIaCrescemComAPagina() {
        assertThat(RecommendService.rerankPool(12)).isEqualTo(30);
        assertThat(RecommendService.rerankPool(24)).isEqualTo(36);
        assertThat(RecommendService.rerankPool(36)).isEqualTo(48);
        assertThat(RecommendService.rerankPool(48)).isEqualTo(60);
    }

    @Test
    void rerankRecebeSinopsesCurtas() {
        when(catalog.discover(any())).thenReturn(new TitleList(
                List.of(new TitleSummary(1, "movie", "Um", 2020, "x".repeat(1000), List.of())), false));
        when(llm.intent(any())).thenReturn(intent(Criteria.empty()));
        when(llm.rerank(any())).thenReturn(new RerankResponse(List.of(), 0));

        service.recommend(pedido("algo", 2), "req-1");

        var rerank = ArgumentCaptor.forClass(RerankRequest.class);
        verify(llm).rerank(rerank.capture());
        assertThat(rerank.getValue().candidates().get(0).overview()).hasSizeLessThanOrEqualTo(300);
    }

    private static RecommendRequest pedido(String texto, int limit) {
        return new RecommendRequest(texto, null, List.of(8), limit, 1, null);
    }

    private static IntentResponse intent(Criteria criteria) {
        return new IntentResponse(criteria, List.of(), null, "Comédia leve", false);
    }

    private static TitleSummary resumo(int id, String titulo) {
        return new TitleSummary(id, "movie", titulo, 2020, "Sinopse", List.of(new Genre(35, "Comédia")));
    }

    private static TitleDetail detalhe(int id) {
        String titulo = switch (id) {
            case 1 -> "Um";
            case 2 -> "Dois";
            case 3 -> "Três";
            default -> "Outro";
        };
        // Disponível na Netflix (do usuário) e no Prime (que ele não assina)
        var watch = new WatchInfo("https://tmdb/watch", List.of(
                new WatchProvider(8, "Netflix", null, "flatrate", "https://netflix"),
                new WatchProvider(119, "Amazon Prime Video", null, "flatrate", "https://prime")));
        return new TitleDetail(id, "movie", titulo, titulo, 2020, "Sinopse", null, null, List.of(), 7.0, 100,
                null, "12", watch);
    }
}
