package com.saga.pedidos.service;

import com.saga.pedidos.dto.*;
import com.saga.pedidos.entity.Pedido;
import com.saga.pedidos.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
 
import java.util.UUID;
 
@Slf4j
@Service
public class PedidoService {
 
    private final PedidoRepository pedidoRepository;
    private final WebClient inventarioClient;
    private final WebClient pagosClient;
    private final WebClient notificacionesClient;
 
    // Constructor manual (por tener múltiples WebClient)
    public PedidoService(
            PedidoRepository pedidoRepository,
            @Qualifier("inventarioClient") WebClient inventarioClient,
            @Qualifier("pagosClient") WebClient pagosClient,
            @Qualifier("notificacionesClient") WebClient notificacionesClient) {
        this.pedidoRepository = pedidoRepository;
        this.inventarioClient = inventarioClient;
        this.pagosClient = pagosClient;
        this.notificacionesClient = notificacionesClient;
    }
// =================================================================
    // MÉTODO PRINCIPAL: ORQUESTACIÓN DEL SAGA
    // =================================================================
    @Transactional
    public PedidoResponseDTO crearPedido(PedidoRequestDTO request) {
 
        log.info("===== [SAGA] INICIO =============================");
        log.info("[SAGA] Paso 1: Creando pedido para producto={}", request.getProducto());
 
        // ----------------------------------------------------------
        // PASO 1: Crear pedido en estado PENDIENTE
        // ----------------------------------------------------------
        Pedido pedido = new Pedido();
        pedido.setProducto(request.getProducto());
        pedido.setEstado("PENDIENTE");
        pedido = pedidoRepository.save(pedido);
        final Long pedidoId = pedido.getId();
 
        log.info("[SAGA] Pedido creado con id={}", pedidoId);
 
        // ----------------------------------------------------------
        // PASO 2: Llamar a MS Inventario → Reservar stock
        // ----------------------------------------------------------
        log.info("[SAGA] Paso 2: Llamando a MS Inventario (reservar)");
        StockResponseDTO stockResp;
        try {
            stockResp = inventarioClient.post()
                .uri("/api/inventario/reservar")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new StockRequestDTO(request.getProducto(), request.getCantidad()))
                .retrieve()
                .bodyToMono(StockResponseDTO.class)
                .block();  // block() convierte reactivo a síncrono
        } catch (Exception e) {
            log.error("[SAGA] Error al llamar Inventario: {}", e.getMessage());
            return cancelarPedido(pedido, "Error al contactar servicio de inventario", null);
        }
 
        if (stockResp == null || !stockResp.isExito()) {
            String msg = stockResp != null ? stockResp.getMensaje() : "Sin respuesta";
            log.warn("[SAGA] Inventario falló: {}", msg);
            return cancelarPedido(pedido, msg, null);
        }
        log.info("[SAGA] Inventario OK. Stock actual: {}", stockResp.getStockActual());
 
        // ----------------------------------------------------------
        // PASO 3: Generar Idempotency-Key y llamar a MS Pagos
        // ----------------------------------------------------------
        String idempotencyKey = UUID.randomUUID().toString();
        log.info("[SAGA] Paso 3: Llamando a MS Pagos con Key={}", idempotencyKey);
 
        PagoResponseDTO pagoResp;
        try {
            pagoResp = pagosClient.post()
                .uri("/api/pagos/procesar")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", idempotencyKey)
                .bodyValue(new PagoRequestDTO(pedidoId, request.getMonto()))
                .retrieve()
                .bodyToMono(PagoResponseDTO.class)
                .block();
        } catch (Exception e) {
            log.error("[SAGA] Error al llamar Pagos: {}", e.getMessage());
            // COMPENSACIÓN: liberar el stock reservado
            liberarStockCompensacion(request.getProducto(), request.getCantidad());
            return cancelarPedido(pedido, "Error al contactar servicio de pagos", null);
        }
// ----------------------------------------------------------
        // DECISIÓN DEL SAGA: ¿Pago exitoso o fallido?
        // ----------------------------------------------------------
        if (pagoResp != null && pagoResp.isExito()) {
 
            log.info("[SAGA] PAGO EXITOSO. Paso 4: Llamando a MS Notificaciones");
 
            // CAMINO FELIZ: Pago OK → Notificar → COMPLETAR
            try {
                notificacionesClient.post()
                    .uri("/api/notificaciones/enviar")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new NotificacionRequestDTO(
                        pedidoId,
                        "Tu pedido #" + pedidoId + " fue procesado exitosamente!"
                    ))
                    .retrieve()
                    .bodyToMono(Object.class)
                    .block();
                log.info("[SAGA] Notificacion enviada correctamente");
            } catch (Exception e) {
                // Las notificaciones no cancelan el pedido
                // El pago ya fue aprobado, no tiene sentido compensar
                log.warn("[SAGA] Notificacion fallo pero pago fue OK: {}", e.getMessage());
            }
 
            // Actualizar pedido a COMPLETADO
            pedido.setEstado("COMPLETADO");
            pedidoRepository.save(pedido);
 
            log.info("[SAGA] Pedido #{} COMPLETADO exitosamente", pedidoId);
            log.info("===== [SAGA] FIN (EXITO) ========================");
 
            return new PedidoResponseDTO(
                pedidoId, "COMPLETADO",
                "Pedido procesado exitosamente",
                idempotencyKey
            );
 
        } else {
 
            log.warn("[SAGA] PAGO FALLIDO. Ejecutando compensacion");
 
            // COMPENSACIÓN: liberar el stock que se reservó en el paso 2
            liberarStockCompensacion(request.getProducto(), request.getCantidad());
 
            String motivo = pagoResp != null ? pagoResp.getMensaje() : "Sin respuesta de pagos";
            return cancelarPedido(pedido, "Pago rechazado: " + motivo, idempotencyKey);
        }
    }
// =================================================================
    // MÉTODOS AUXILIARES
    // =================================================================
 
    // Cancela el pedido (cambia estado a CANCELADO)
    private PedidoResponseDTO cancelarPedido(Pedido pedido, String motivo, String idempotencyKey) {
        pedido.setEstado("CANCELADO");
        pedidoRepository.save(pedido);
        log.info("[SAGA] Pedido #{} CANCELADO. Motivo: {}", pedido.getId(), motivo);
        log.info("===== [SAGA] FIN (CANCELADO) =====================");
        return new PedidoResponseDTO(pedido.getId(), "CANCELADO", motivo, idempotencyKey);
    }
 
    // Llama a Inventario para liberar (compensación)
    private void liberarStockCompensacion(String idProducto, Integer cantidad) {
        log.info("[SAGA] [COMPENSACION] Liberando stock: {} unidades de {}",
                 cantidad, idProducto);
        try {
            inventarioClient.post()
                .uri("/api/inventario/liberar")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new StockRequestDTO(idProducto, cantidad))
                .retrieve()
                .bodyToMono(Object.class)
                .block();
            log.info("[SAGA] [COMPENSACION] Stock liberado correctamente");
        } catch (Exception e) {
            log.error("[SAGA] [COMPENSACION] Error al liberar stock: {}", e.getMessage());
            // En un sistema real, aquí se guardaría en una tabla de compensaciones pendientes
        }
    }
}
