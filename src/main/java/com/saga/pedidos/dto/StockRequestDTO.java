package com.saga.pedidos.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data 
@AllArgsConstructor
public class StockRequestDTO {
    private String idProducto;
    private Integer cantidad;
}
