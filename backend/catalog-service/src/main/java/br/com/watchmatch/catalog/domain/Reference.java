package br.com.watchmatch.catalog.domain;

// O título de "parecido com X", do jeito que o TMDB o encontrou
public record Reference(Integer id, TitleType mediaType, String title) {
}
