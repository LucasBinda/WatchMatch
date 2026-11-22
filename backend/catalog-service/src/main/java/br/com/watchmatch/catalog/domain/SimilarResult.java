package br.com.watchmatch.catalog.domain;

import java.util.List;

// reference null = não achei o título citado (items vem vazio)
public record SimilarResult(Reference reference, List<TitleSummary> items, Boolean hasMore) {
}
