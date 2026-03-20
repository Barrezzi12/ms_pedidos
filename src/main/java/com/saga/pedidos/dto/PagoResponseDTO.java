package com.saga.pedidos.dto;

import lombok.Data;
@Data
public class PagoResponseDTO {
    private boolean exito;
    private String estado;
    private String idempotencyKey;
    private boolean fueIdempotente;
    private String mensaje;
}
