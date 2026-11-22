package br.com.watchmatch.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.watchmatch.catalog.tmdb.TmdbClient;
import br.com.watchmatch.catalog.tmdb.TmdbKeywordPage;
import br.com.watchmatch.catalog.tmdb.TmdbKeywordPage.TmdbKeyword;

/**
 * Traduz as palavras-chave da IA ("heist") para os ids que o /discover do TMDB entende.
 */
class KeywordResolverTest {

    private final TmdbClient client = mock(TmdbClient.class);
    private final KeywordResolver resolver = new KeywordResolver(client);

    @Test
    void preferePalavraComONomeExato() {
        when(client.searchKeyword("heist")).thenReturn(new TmdbKeywordPage(List.of(
                new TmdbKeyword(1, "heist movie"), new TmdbKeyword(10051, "heist"))));

        assertThat(resolver.ids(List.of("heist"))).containsExactly(10051);
    }

    @Test
    void semNomeExatoUsaOPrimeiro() {
        when(client.searchKeyword("space")).thenReturn(new TmdbKeywordPage(List.of(
                new TmdbKeyword(9882, "space travel"), new TmdbKeyword(3, "space opera"))));

        assertThat(resolver.ids(List.of("space"))).containsExactly(9882);
    }

    @Test
    void palavraSemCorrespondenciaEIgnorada() {
        when(client.searchKeyword("heist")).thenReturn(new TmdbKeywordPage(List.of(new TmdbKeyword(10051, "heist"))));
        when(client.searchKeyword("xyzzy")).thenReturn(new TmdbKeywordPage(List.of()));

        assertThat(resolver.ids(List.of("heist", "xyzzy"))).containsExactly(10051);
    }

    @Test
    void guardaAsRespostasParaNaoPerguntarDeNovo() {
        when(client.searchKeyword("heist")).thenReturn(new TmdbKeywordPage(List.of(new TmdbKeyword(10051, "heist"))));
        when(client.searchKeyword("xyzzy")).thenReturn(new TmdbKeywordPage(List.of()));

        resolver.ids(List.of("heist", "xyzzy"));
        resolver.ids(List.of(" Heist ", "xyzzy"));

        // Também guarda o "não achei"
        verify(client, times(1)).searchKeyword("heist");
        verify(client, times(1)).searchKeyword("xyzzy");
    }

    @Test
    void semPalavrasNaoChamaOTmdb() {
        assertThat(resolver.ids(List.of())).isEmpty();
        assertThat(resolver.ids(null)).isEmpty();
    }
}
