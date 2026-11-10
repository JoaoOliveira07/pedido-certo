# Erros da API

Todos os erros seguem a [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) (`application/problem+json`). O campo `type` aponta para uma das seções abaixo.

## pedido-nao-encontrado
`404`. O id ou o txid não existe, ou o pedido tem mais de 24 horas e foi apagado pela limpeza da demo.

## idempotency-key-reutilizada
`422`. A mesma `Idempotency-Key` chegou com um corpo diferente do original. Cada pedido novo precisa de uma chave nova; a mesma chave só serve para repetir **a mesma** requisição.

## idempotency-key-invalida
`400`. A chave precisa ter de 8 a 100 caracteres: letras, números, `-`, `_`, `:` ou `.`. Um UUID serve.

## valor-divergente
`422`. O valor pago no PIX é diferente do total do pedido. O pedido continua aguardando pagamento e a tentativa fica registrada na linha do tempo.

## transicao-invalida
`409`. A ação não vale para o status atual. Exemplo: reprocessar um pedido que não está em `FALHA_INTEGRACAO`.

## alteracao-concorrente
`409`. Duas requisições alteraram o mesmo pedido ao mesmo tempo e esta perdeu (controle otimista pela coluna `version`). Repetir a requisição resolve.

## assinatura-invalida
`401`. O header `X-Pix-Signature` não confere com o HMAC-SHA256 do corpo.

## requisicao-invalida
`400`. Algum campo viola uma regra do domínio.
