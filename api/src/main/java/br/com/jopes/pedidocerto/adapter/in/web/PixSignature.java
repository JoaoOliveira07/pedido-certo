package br.com.jopes.pedidocerto.adapter.in.web;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Assinatura HMAC-SHA256 do corpo do webhook, no formato {@code sha256=<hex>}. Prova que o
 * webhook veio de quem conhece o segredo (o PSP) e que o corpo não foi alterado no caminho.
 */
final class PixSignature {

    private static final String PREFIX = "sha256=";
    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] secret;

    PixSignature(String secret) {
        if (secret == null || secret.length() < 16) {
            throw new IllegalArgumentException("o segredo do webhook PIX precisa de ao menos 16 caracteres");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    String sign(String body) {
        return PREFIX + HexFormat.of().formatHex(hmac(body));
    }

    /** Comparação em tempo constante, para não vazar a assinatura correta por medição de tempo. */
    boolean matches(String body, String signature) {
        if (signature == null || !signature.startsWith(PREFIX)) {
            return false;
        }
        byte[] received;
        try {
            received = HexFormat.of().parseHex(signature.substring(PREFIX.length()));
        } catch (IllegalArgumentException e) {
            return false;
        }
        return MessageDigest.isEqual(hmac(body), received);
    }

    private byte[] hmac(String body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC indisponível", e);
        }
    }
}
