package com.saga.pedidos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
 
// @Configuration indica que esta clase crea beans de Spring
@Configuration
public class WebClientConfig {
 
    // Leer URLs del application.yml
    @Value("${microservicios.inventario.url}")
    private String inventarioUrl;
 
    @Value("${microservicios.pagos.url}")
    private String pagosUrl;
 
    @Value("${microservicios.notificaciones.url}")
    private String notificacionesUrl;
 
    // Bean para llamar a MS Inventario
    @Bean
    public WebClient inventarioClient() {
        return WebClient.builder()
            .baseUrl(inventarioUrl)
            .build();
    }
 
    // Bean para llamar a MS Pagos
    @Bean
    public WebClient pagosClient() {
        return WebClient.builder()
            .baseUrl(pagosUrl)
            .build();
    }
 
    // Bean para llamar a MS Notificaciones
    @Bean
    public WebClient notificacionesClient() {
        return WebClient.builder()
            .baseUrl(notificacionesUrl)
            .build();
    }
}

