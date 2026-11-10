package br.com.jopes.pedidocerto.adapter.in.web;

import br.com.jopes.pedidocerto.application.port.in.ConfirmPaymentUseCase;
import br.com.jopes.pedidocerto.application.port.in.ConfirmPaymentUseCase.PaymentOutcome;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Valida e interpreta o webhook do PSP antes de entregar para o caso de uso. */
@Component
class PixWebhookHandler {

    private final PixSignature signature;
    private final ObjectMapper json;
    private final ConfirmPaymentUseCase confirmPayment;

    PixWebhookHandler(@Value("${app.pix.webhook-secret}") String secret, ObjectMapper json,
                      ConfirmPaymentUseCase confirmPayment) {
        this.signature = new PixSignature(secret);
        this.json = json;
        this.confirmPayment = confirmPayment;
    }

    PaymentOutcome handle(String rawBody, String signatureHeader) {
        if (!signature.matches(rawBody, signatureHeader)) {
            throw new InvalidSignatureException();
        }
        PixWebhookPayload payload;
        try {
            payload = json.readValue(rawBody, PixWebhookPayload.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("corpo do webhook inválido");
        }
        if (payload.txid() == null || payload.txid().isBlank()) {
            throw new IllegalArgumentException("txid é obrigatório");
        }
        return confirmPayment.confirm(payload.txid(), payload.amountCents());
    }

    /** Usado pelo PSP simulado para assinar como um PSP de verdade faria. */
    String sign(String rawBody) {
        return signature.sign(rawBody);
    }

    public record PixWebhookPayload(String txid, long amountCents) {
    }

    static class InvalidSignatureException extends RuntimeException {
        InvalidSignatureException() {
            super("Assinatura do webhook inválida");
        }
    }
}
