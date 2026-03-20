package com.saga.pedidos.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
@Data
@AllArgsConstructor

public class PedidoResponseDTO {
    private Long idPedido;
    private String estadoFinal;
    private String mensaje;
    private String idempotencyKeyUsada;

}
