package com.saga.pedidos.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
@Data 
@AllArgsConstructor
public class PagoRequestDTO {
    private Long idPedido;
    private Double monto;
}
