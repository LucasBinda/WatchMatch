package br.com.watchmatch.catalog.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import br.com.watchmatch.catalog.domain.SimilarResult;
import br.com.watchmatch.catalog.domain.TitleList;
import br.com.watchmatch.catalog.domain.TitleType;
import br.com.watchmatch.catalog.service.SearchService;
import br.com.watchmatch.catalog.service.SimilarService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Rotas internas: o gateway não as expõe. O Spring valida os @RequestParam sozinho e responde 400
@RestController
public class SimilarController {

    private final SimilarService similar;
    private final SearchService search;

    public SimilarController(SimilarService similar, SearchService search) {
        this.similar = similar;
        this.search = search;
    }

    @PostMapping("/similar")
    public SimilarResult similar(@Valid @RequestBody SimilarBody body) {
        return similar.similar(body.toDomain(), body.limitOrDefault());
    }

    // Busca por nome; não filtra por plataforma. hasMore sempre false: é só a primeira página do TMDB
    @GetMapping("/search")
    public TitleList search(
            @RequestParam @NotBlank @Size(max = 100) String query,
            @RequestParam(required = false) String mediaType,
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int limit) {
        return new TitleList(search.search(query, parseType(mediaType), limit), false);
    }

    // Na URL o tipo vem em minúsculas, como no /titles/{mediaType}/{id}
    private TitleType parseType(String mediaType) {
        if (mediaType == null) {
            return null;
        }
        return switch (mediaType) {
            case "movie" -> TitleType.MOVIE;
            case "tv" -> TitleType.TV;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mediaType deve ser movie ou tv");
        };
    }
}
