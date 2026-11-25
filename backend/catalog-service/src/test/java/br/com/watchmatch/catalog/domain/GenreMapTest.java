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
    void qualquerUmTiraSoOQueOTipoNaoTem() {
        var terrorOuComedia = Criteria.empty().withGenres(List.of(27, 35));

        assertThat(GenreMap.forType(terrorOuComedia, TitleType.TV)).get()
                .extracting(Criteria::genres).isEqualTo(List.of(35));
    }

    @Test
    void soGeneroQueOTipoNaoTemEhImpossivel() {
        // Buscar séries sem o filtro de gênero traria qualquer série
        assertThat(GenreMap.forType(Criteria.empty().withGenres(List.of(27)), TitleType.TV)).isEmpty();
    }

    @Test
    void todosOsGenerosComUmQueOTipoNaoTemEhImpossivel() {
        var terrorEComedia = Criteria.empty().withGenres(List.of(27, 35)).withGenreMatch("all");

        assertThat(GenreMap.forType(terrorEComedia, TitleType.TV)).isEmpty();
        assertThat(GenreMap.forType(terrorEComedia, TitleType.MOVIE)).get()
                .extracting(Criteria::genres).isEqualTo(List.of(27, 35));
    }

    @Test
    void faroesteEFiccaoEmSerieComTodos() {
        var criteria = Criteria.empty().withGenres(List.of(37, 878)).withGenreMatch("all");

        assertThat(GenreMap.forType(criteria, TitleType.TV)).get()
                .extracting(Criteria::genres).isEqualTo(List.of(37, 10765));
    }

    @Test
    void proibidoQueOTipoNaoTemSaiEPedidoVenceProibido() {
        // Pediu Ação, proibiu Aventura e Terror. Em série: Ação e Aventura são o mesmo gênero (vale o pedido),
        // e Terror não existe
        var criteria = Criteria.empty().withGenres(List.of(28)).withExcludeGenres(List.of(12, 27));

        var serie = GenreMap.forType(criteria, TitleType.TV).orElseThrow();

        assertThat(serie.genres()).containsExactly(10759);
        assertThat(serie.excludeGenres()).isEmpty();
    }

    @Test
    void semGeneroNaoMudaNada() {
        assertThat(GenreMap.forType(Criteria.empty(), TitleType.TV)).contains(Criteria.empty());
    }
}
