package it.mlf.fotobatch.model;

import java.util.List;

public record PhotoSearchPage(
        List<PhotoSearchItem> items,
        int page,
        int size,
        boolean hasNext
) {
}
