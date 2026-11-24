package it.mlf.fotobatch.model;

import java.util.List;

public record SearchFacets(
        List<SearchFacetValue> monthYears,
        List<SearchFacetValue> localities
) {
}
