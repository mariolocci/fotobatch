package it.mlf.fotobatch.controller;

import java.util.List;

public record ApiErrorResponse(
        String message,
        List<String> details
) {
}
