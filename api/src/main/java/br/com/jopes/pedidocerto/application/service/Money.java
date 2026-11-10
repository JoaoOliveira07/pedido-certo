package br.com.jopes.pedidocerto.application.service;

import java.text.NumberFormat;
import java.util.Locale;

final class Money {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private Money() {
    }

    static String brl(long cents) {
        NumberFormat format = NumberFormat.getCurrencyInstance(PT_BR);
        return format.format(cents / 100.0);
    }
}
