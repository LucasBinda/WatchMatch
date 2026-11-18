package br.com.watchmatch.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import br.com.watchmatch.catalog.service.MetaService;

/**
 * GET /health: o Docker Compose usa essa rota para saber se o serviço está de pé.
 */
@WebMvcTest(MetaController.class)
class MetaControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private MetaService service;

    @Test
    void healthRespondeOk() {
        var resposta = mvc.get().uri("/health").exchange();

        assertThat(resposta).hasStatusOk();
        assertThat(resposta).bodyJson().extractingPath("$.status").isEqualTo("ok");
    }
}
