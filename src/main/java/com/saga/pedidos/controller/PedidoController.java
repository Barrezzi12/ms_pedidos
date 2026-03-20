package com.saga.pedidos.controller;

import com.saga.pedidos.dto.PedidoRequestDTO;
import com.saga.pedidos.dto.PedidoResponseDTO;
import com.saga.pedidos.service.PedidoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
 
@Slf4j
@RestController
@RequestMapping("/api/pedidos")
@RequiredArgsConstructor
public class PedidoController {
 
    private final PedidoService pedidoService;
 
    // POST /api/pedidos
    // Este es el único endpoint que llama el cliente externo
    @PostMapping
    public ResponseEntity<PedidoResponseDTO> crearPedido(
            @RequestBody PedidoRequestDTO request) {
 
        log.info("[PEDIDOS] Nuevo pedido recibido: producto={}", request.getProducto());
        PedidoResponseDTO response = pedidoService.crearPedido(request);
 
        HttpStatus status = "COMPLETADO".equals(response.getEstadoFinal())
            ? HttpStatus.CREATED
            : HttpStatus.OK;  // 200 aunque esté cancelado (la saga completó)
 
        return ResponseEntity.status(status).body(response);
    }
}

