package com.zeropick.possyncservice.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/pos-sync-service/pos-terminal")
public class PosTerminalController {

    private final JdbcTemplate posDb;

    public PosTerminalController(JdbcTemplate posDb) {
        this.posDb = posDb;
    }

    @PutMapping("/{productId}")
    public Map<String, Object> setStock(@PathVariable Long productId, @Valid @RequestBody TerminalRequest request) {
        int updated = posDb.update(
                "UPDATE pos_stock SET stock = ? WHERE product_id = ?", request.stock(), productId);
        if (updated == 0) {
            posDb.update(
                    "INSERT INTO pos_stock (product_id, store_code, stock) VALUES (?, 'GANGNAM01', ?)",
                    productId, request.stock());
        }
        return Map.of("productId", productId, "stock", request.stock(),
                "applied", updated > 0 ? "updated" : "inserted");
    }

    public record TerminalRequest(@NotNull @Min(0) Integer stock) {
    }
}
