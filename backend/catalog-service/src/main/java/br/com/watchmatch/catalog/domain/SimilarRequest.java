package br.com.watchmatch.catalog.domain;

import java.util.List;

// title: o nome citado pelo usuário ("Interestelar"); mediaType null = o tipo da referência
public record SimilarRequest(String title, TitleType mediaType, Criteria criteria, List<Integer> providers,
        Integer page) {
}
