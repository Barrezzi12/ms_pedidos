package com.saga.pedidos.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
@Data 
@AllArgsConstructor
public class NotificacionRequestDTO {
    private Long idPedido;
    private String mensaje;
}
