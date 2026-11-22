package br.com.watchmatch.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import br.com.watchmatch.catalog.domain.Reference;
import br.com.watchmatch.catalog.domain.SimilarRequest;
import br.com.watchmatch.catalog.domain.SimilarResult;
import br.com.watchmatch.catalog.domain.TitleType;
import br.com.watchmatch.catalog.service.SearchService;
import br.com.watchmatch.catalog.service.SimilarService;

/**
 * POST /similar e GET /search: o JSON entra no formato do contrato e os erros de entrada viram 400.
 */
@WebMvcTest(SimilarController.class)
class SimilarControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private SimilarService similar;

    @MockitoBean
    private SearchService search;

    @Test
    void similarRecebeOTituloEOsCriterios() {
        when(similar.similar(any(), anyInt())).thenReturn(
                new SimilarResult(new Reference(157336, TitleType.MOVIE, "Interestelar"), List.of(), false));

        var resposta = mvc.post().uri("/similar").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title": "Interestelar", "providers": [8], "runtimeMin": 90, "originalLanguage": "en",
                     "keywords": ["space travel"], "limit": 20}
                    """)
                .exchange();

        assertThat(resposta).hasStatusOk().bodyJson().extractingPath("$.reference.mediaType").isEqualTo("movie");
        var pedido = ArgumentCaptor.forClass(SimilarRequest.class);
        verify(similar).similar(pedido.capture(), eq(20));
        assertThat(pedido.getValue().title()).isEqualTo("Interestelar");
        assertThat(pedido.getValue().criteria().runtimeMin()).isEqualTo(90);
        assertThat(pedido.getValue().criteria().originalLanguage()).isEqualTo("en");
        assertThat(pedido.getValue().criteria().keywords()).containsExactly("space travel");
    }

    @Test
    void similarSemTituloOuComIdiomaInvalidoE400() {
        assertThat(mvc.post().uri("/similar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"providers\": [8]}")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mvc.post().uri("/similar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"X\", \"providers\": [8], \"originalLanguage\": \"coreano\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void searchConverteOTipoDaUrl() {
        when(search.search("Interestelar", TitleType.MOVIE, 5)).thenReturn(List.of());

        assertThat(mvc.get().uri("/search?query=Interestelar&mediaType=movie&limit=5")).hasStatusOk();
        verify(search).search("Interestelar", TitleType.MOVIE, 5);
        assertThat(mvc.get().uri("/search?query=X&mediaType=filme")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mvc.get().uri("/search?query=")).hasStatus(HttpStatus.BAD_REQUEST);
    }
}
