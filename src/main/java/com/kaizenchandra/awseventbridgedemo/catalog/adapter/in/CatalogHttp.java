package com.kaizenchandra.awseventbridgedemo.catalog.adapter.in;

import org.springframework.web.bind.annotation.*;
import jakarta.validation.constraints.*;

import java.time.*;
import java.util.*;

import reactor.core.publisher.Mono;
import com.kaizenchandra.awseventbridgedemo.catalog.application.CatalogPort;
import com.kaizenchandra.awseventbridgedemo.shared.adapter.in.BlockingBoundary;

@RestController
@RequestMapping("/api")
public class CatalogHttp {
    private final CatalogPort catalog;
    private final BlockingBoundary boundary;
    private final Clock clock;
    private final com.kaizenchandra.awseventbridgedemo.scheduling.application.SchedulingPort scheduling;
    private final com.kaizenchandra.awseventbridgedemo.inventory.application.InventoryPort inventory;

    public CatalogHttp(CatalogPort catalog, BlockingBoundary boundary, Clock clock, com.kaizenchandra.awseventbridgedemo.scheduling.application.SchedulingPort scheduling, com.kaizenchandra.awseventbridgedemo.inventory.application.InventoryPort inventory) {
        this.scheduling = scheduling;
        this.inventory = inventory;
        this.catalog = catalog;
        this.boundary = boundary;
        this.clock = clock;
    }

    @GetMapping("/movies")
    public Mono<?> movies(@RequestParam(defaultValue = "") @Size(max = 100) String title, @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return boundary.call(() -> catalog.movies(title, page, size));
    }

    @GetMapping("/cinemas")
    public Mono<?> cinemas() {
        return boundary.call(catalog::cinemas);
    }

    @GetMapping("/shows")
    public Mono<?> shows(@RequestParam(required = false) UUID movieId, @RequestParam(defaultValue = "") @Size(max = 120) String city, @RequestParam(required = false) Instant from, @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return boundary.call(() -> scheduling.shows(movieId, city, from == null ? clock.instant() : from, page, size));
    }

    @GetMapping("/shows/{id}/seats")
    public Mono<?> seats(@PathVariable UUID id) {
        return boundary.call(() -> inventory.seats(id));
    }
}
