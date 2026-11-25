package br.com.watchmatch.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * O site tem uma lista só de gêneros; o TMDB tem uma de filme e outra de série.
 */
class GenreMapTest {

    @Test
    void generoIgualNosDoisTiposNaoMuda() {
        assertThat(GenreMap.toType(35, TitleType.MOVIE)).containsExactly(35);
        assertThat(GenreMap.toType(35, TitleType.TV)).containsExactly(35);
    }

    @Test
    void ficcaoCientificaViraSciFiAndFantasyEmSerie() {
        assertThat(GenreMap.toType(878, TitleType.TV)).containsExactly(10765);
        assertThat(GenreMap.toType(14, TitleType.TV)).containsExactly(10765);
        // A IA pode mandar o id de série; em filme ele vira os dois
        assertThat(GenreMap.toType(10765, TitleType.MOVIE)).containsExactly(878, 14);
    }

    @Test
    void generoQueOTipoNaoTemFicaDeFora() {
        assertThat(GenreMap.toType(27, TitleType.TV)).isEmpty();
        assertThat(GenreMap.toType(10764, TitleType.MOVIE)).isEmpty();
    }

    @Test
    void qualquerUmFicaComOGeneroQueOTipoTem() {
        // O TMDB não junta gênero e palavra-chave num "ou": vale Comédia, e Terror fica de fora
        var terrorOuComedia = Criteria.empty().withGenres(List.of(27, 35));

        var serie = GenreMap.forType(terrorOuComedia, TitleType.TV).orElseThrow();

        assertThat(serie.criteria().genres()).containsExactly(35);
        assertThat(serie.keywords()).isEmpty();
    }

    @Test
    void terrorEmSerieViraPalavraChave() {
        // Sem isso, "série de terror" não achava nenhuma série
        var serie = GenreMap.forType(Criteria.empty().withGenres(List.of(27)), TitleType.TV).orElseThrow();

        assertThat(serie.criteria().genres()).isEmpty();
        assertThat(serie.keywords()).containsExactly("horror");
        // Em filme, Terror é gênero de verdade
        assertThat(GenreMap.forType(Criteria.empty().withGenres(List.of(27)), TitleType.MOVIE).orElseThrow()
                .keywords()).isEmpty();
    }

    @Test
    void generoSemPalavraChaveEhImpossivel() {
        // Cinema TV não existe em série nem como palavra-chave: buscar sem ele traria qualquer série
        assertThat(GenreMap.forType(Criteria.empty().withGenres(List.of(10770)), TitleType.TV)).isEmpty();
    }

    @Test
    void todosOsGenerosJuntaGeneroEPalavraChave() {
        var terrorEComedia = Criteria.empty().withGenres(List.of(27, 35)).withGenreMatch("all");

        var serie = GenreMap.forType(terrorEComedia, TitleType.TV).orElseThrow();
        assertThat(serie.criteria().genres()).containsExactly(35);
        assertThat(serie.keywords()).containsExactly("horror");
        assertThat(serie.allKeywords()).isTrue();

        assertThat(GenreMap.forType(terrorEComedia, TitleType.MOVIE).orElseThrow().criteria().genres())
                .containsExactly(27, 35);
    }

    @Test
    void terrorProibidoEmSerieViraPalavraChaveProibida() {
        var semTerror = Criteria.empty().withExcludeGenres(List.of(27));

        assertThat(GenreMap.forType(semTerror, TitleType.TV).orElseThrow().excludeKeywords()).containsExactly("horror");
    }

    @Test
    void faroesteEFiccaoEmSerieComTodos() {
        var criteria = Criteria.empty().withGenres(List.of(37, 878)).withGenreMatch("all");

        assertThat(GenreMap.forType(criteria, TitleType.TV).orElseThrow().criteria().genres())
                .containsExactly(37, 10765);
    }

    @Test
    void proibidoQueOTipoNaoTemSaiEPedidoVenceProibido() {
        // Pediu Ação, proibiu Aventura e Terror. Em série: Ação e Aventura são o mesmo gênero (vale o pedido),
        // e Terror não é gênero (vira palavra-chave proibida, conferida no teste acima)
        var criteria = Criteria.empty().withGenres(List.of(28)).withExcludeGenres(List.of(12, 27));

        var serie = GenreMap.forType(criteria, TitleType.TV).orElseThrow();

        assertThat(serie.criteria().genres()).containsExactly(10759);
        assertThat(serie.criteria().excludeGenres()).isEmpty();
    }

    @Test
    void semGeneroNaoMudaNada() {
        var serie = GenreMap.forType(Criteria.empty(), TitleType.TV).orElseThrow();
        assertThat(serie.criteria()).isEqualTo(Criteria.empty());
        assertThat(serie.keywords()).isEmpty();
    }
}
