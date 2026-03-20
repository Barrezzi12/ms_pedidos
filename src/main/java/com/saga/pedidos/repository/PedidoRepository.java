package com.saga.pedidos.repository;

import com.saga.pedidos.entity.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
 
@Repository

public interface PedidoRepository extends JpaRepository<Pedido, Long> {
}

