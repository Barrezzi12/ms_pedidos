package com.saga.pedidos.dto;

import lombok.Data;
@Data

public class StockResponseDTO {
    private boolean exito;
    private String mensaje;
    private Integer stockActual;
}
