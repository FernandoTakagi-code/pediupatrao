package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.StatusPedido;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

@Configuration
public class AppConfig {

    /** Relógio único do sistema (fuso configurável), usado para data/hora de pedidos e auditoria. */
    @Bean
    public Clock clock(@Value("${pediupatrao.timezone:America/Sao_Paulo}") String timezone) {
        return Clock.system(ZoneId.of(timezone));
    }

    /** Lê status antigos gravados no MongoDB (ex.: EM_PREPARO, ENTREGUE) sem quebrar a listagem. */
    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(List.of(new StatusPedidoReadingConverter()));
    }

    @ReadingConverter
    static class StatusPedidoReadingConverter implements Converter<String, StatusPedido> {
        @Override
        public StatusPedido convert(String source) {
            return StatusPedido.deTextoLegado(source);
        }
    }
}