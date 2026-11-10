package br.com.jopes.pedidocerto.application.port.in;

import br.com.jopes.pedidocerto.domain.integration.ChaosSettings;

public interface ChaosControlUseCase {

    ChaosSettings current();

    ChaosSettings update(ChaosSettings settings);
}
