package com.zeropick.stockservice.controller;

import com.zeropick.stockservice.domain.Stock;
import com.zeropick.stockservice.service.StockService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/stock-service/stocks")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping("/{productId}")
    public StockResponse get(@PathVariable Long productId) {
        return StockResponse.from(stockService.get(productId));
    }

    @GetMapping
    public List<StockResponse> getAll(@RequestParam List<Long> ids) {
        return stockService.getAll(ids).stream().map(StockResponse::from).toList();
    }

    @PutMapping("/{productId}/deduct")
    public StockResponse deduct(@PathVariable Long productId, @Valid @RequestBody QtyRequest request) {
        return StockResponse.from(stockService.deduct(productId, request.qty()));
    }

    @PutMapping("/{productId}/restore")
    public StockResponse restore(@PathVariable Long productId, @Valid @RequestBody QtyRequest request) {
        return StockResponse.from(stockService.restore(productId, request.qty()));
    }

    @PutMapping("/{productId}/pos")
    public StockResponse applyPos(@PathVariable Long productId, @Valid @RequestBody PosRequest request) {
        return StockResponse.from(stockService.applyPos(productId, request.posStock(), request.storeCode()));
    }

    @ExceptionHandler(StockService.OutOfStockException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleOutOfStock(StockService.OutOfStockException e) {
        return e.getMessage();
    }

    @ExceptionHandler(StockService.StockNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(StockService.StockNotFoundException e) {
        return e.getMessage();
    }

    public record QtyRequest(@NotNull @Min(1) Integer qty) {
    }

    public record PosRequest(@NotNull @Min(0) Integer posStock, String storeCode) {
    }

    public record StockResponse(Long productId, Integer onlineStock, Integer posStock,
                                Integer totalStock, String storeCode, Long version) {
        static StockResponse from(Stock s) {
            return new StockResponse(s.getProductId(), s.getOnlineStock(), s.getPosStock(),
                    s.getOnlineStock() + s.getPosStock(), s.getStoreCode(), s.getVersion());
        }
    }
}
