package br.com.jopes.pedidocerto.application.service;

import br.com.jopes.pedidocerto.application.port.in.PlaceOrderUseCase.PlaceOrderCommand;
import br.com.jopes.pedidocerto.domain.order.OrderItem;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Hash do conteúdo de uma requisição de pedido. Serve para detectar a mesma Idempotency-Key
 * reutilizada com um pedido diferente, que é erro do cliente e não deve ser aceito em silêncio.
 */
final class RequestFingerprint {

    private RequestFingerprint() {
    }

    static String of(PlaceOrderCommand command) {
        StringBuilder canonical = new StringBuilder(command.customerName().strip());
        for (OrderItem item : command.items()) {
            canonical.append('\n')
                    .append(item.sku()).append('|')
                    .append(item.description()).append('|')
                    .append(item.quantity()).append('|')
                    .append(item.unitPriceCents());
        }
        return sha256(canonical.toString());
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
