package br.com.jopes.pedidocerto.adapter.in.web;

import br.com.jopes.pedidocerto.application.port.in.ConfirmPaymentUseCase.PaymentOutcome;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook que o PSP chama quando um PIX é pago. Recebe o corpo cru porque a assinatura é
 * calculada sobre os bytes exatos que chegaram, não sobre um JSON reformatado.
 *
 * <p>Repetições respondem 200: o PSP reenvia enquanto não recebe sucesso, e uma repetição já
 * tratada não é erro.
 */
@RestController
@RequestMapping("/api/webhooks/pix")
class PixWebhookController {

    static final String SIGNATURE_HEADER = "X-Pix-Signature";

    private final PixWebhookHandler handler;

    PixWebhookController(PixWebhookHandler handler) {
        this.handler = handler;
    }

    @PostMapping(consumes = "application/json")
    WebhookAck receive(@RequestHeader(SIGNATURE_HEADER) String signature, @RequestBody String rawBody) {
        return new WebhookAck(handler.handle(rawBody, signature));
    }

    public record WebhookAck(PaymentOutcome outcome) {
    }
}
