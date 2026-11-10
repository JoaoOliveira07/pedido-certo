package br.com.jopes.pedidocerto.application.port.out;

import br.com.jopes.pedidocerto.domain.integration.ChaosSettings;

/** Configuração da rede simulada entre a loja e o ERP. */
public interface NetworkChaos {

    ChaosSettings current();

    void update(ChaosSettings settings);
}
