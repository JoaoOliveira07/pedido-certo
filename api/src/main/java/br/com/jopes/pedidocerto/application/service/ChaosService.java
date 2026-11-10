package br.com.jopes.pedidocerto.application.service;

import br.com.jopes.pedidocerto.application.port.in.ChaosControlUseCase;
import br.com.jopes.pedidocerto.application.port.out.NetworkChaos;
import br.com.jopes.pedidocerto.domain.integration.ChaosSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ChaosService implements ChaosControlUseCase {

    private static final Logger log = LoggerFactory.getLogger(ChaosService.class);

    private final NetworkChaos chaos;

    public ChaosService(NetworkChaos chaos) {
        this.chaos = chaos;
    }

    @Override
    public ChaosSettings current() {
        return chaos.current();
    }

    @Override
    public ChaosSettings update(ChaosSettings settings) {
        chaos.update(settings);
        log.info("Caos da rede atualizado: {}", settings);
        return settings;
    }
}
