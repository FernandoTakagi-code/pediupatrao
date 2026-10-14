package com.umc.pediupatrao.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    /** Relógio único do sistema (fuso configurável), usado para data/hora de pedidos e auditoria. */
    @Bean
    public Clock clock(@Value("${pediupatrao.timezone:America/Sao_Paulo}") String timezone) {
        return Clock.system(ZoneId.of(timezone));
    }
}