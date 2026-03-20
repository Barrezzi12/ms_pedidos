package com.saga.pedidos.dto;

import lombok.Data;
@Data

public class PedidoRequestDTO {
    private String producto;   // Ej: 'PROD-001'
    private Integer cantidad;  // Ej: 2
    private Double monto;      // Ej: 99.99

}
