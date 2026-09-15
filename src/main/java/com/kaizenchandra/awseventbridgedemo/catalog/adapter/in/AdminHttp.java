package com.kaizenchandra.awseventbridgedemo.catalog.adapter.in;

import com.kaizenchandra.awseventbridgedemo.catalog.application.CatalogPort;
import com.kaizenchandra.awseventbridgedemo.shared.adapter.in.BlockingBoundary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class AdminHttp {
    private final com.kaizenchandra.awseventbridgedemo.scheduling.application.SchedulingPort scheduling;
    private final CatalogPort catalog;
    private final BlockingBoundary boundary;

    public AdminHttp(CatalogPort catalog, BlockingBoundary boundary, com.kaizenchandra.awseventbridgedemo.scheduling.application.SchedulingPort scheduling) {
        this.scheduling = scheduling;
        this.catalog = catalog;
        this.boundary = boundary;
    }

    @PostMapping("/movies")
    public Mono<?> movie(@Valid @RequestBody Movie m) {
        return boundary.call(() -> Map.of("id", catalog.movie(m.title(), m.durationMinutes())));
    }

    @PutMapping("/movies/{id}")
    public Mono<?> movie(@PathVariable UUID id, @Valid @RequestBody Movie m) {
        return boundary.call(() -> {
            catalog.updateMovie(id, m.title(), m.durationMinutes());
            return Map.of("id", id);
        });
    }

    @PostMapping("/cinemas")
    public Mono<?> cinema(@Valid @RequestBody Cinema c) {
        return boundary.call(() -> Map.of("id", catalog.cinema(c.name(), c.city())));
    }

    @PutMapping("/cinemas/{id}")
    public Mono<?> cinema(@PathVariable UUID id, @Valid @RequestBody Cinema c) {
        return boundary.call(() -> {
            catalog.updateCinema(id, c.name(), c.city());
            return Map.of("id", id);
        });
    }

    @PostMapping("/screens")
    public Mono<?> screen(@Valid @RequestBody Screen s) {
        return boundary.call(() -> Map.of("id", catalog.screen(s.cinemaId(), s.name(), s.seats())));
    }

    @PutMapping("/screens/{id}")
    public Mono<?> screen(@PathVariable UUID id, @Valid @RequestBody Screen s) {
        return boundary.call(() -> {
            catalog.updateScreen(id, s.name(), s.seats());
            return Map.of("id", id);
        });
    }

    @PostMapping("/shows")
    public Mono<?> show(@Valid @RequestBody Show s) {
        return boundary.call(() -> Map.of("id", scheduling.show(s.movieId(), s.screenId(), s.startsAt(), s.endsAt(), s.priceMinor(), s.currency())));
    }

    @DeleteMapping("/shows/{id}")
    public Mono<?> delete(@PathVariable UUID id) {
        return boundary.call(() -> {
            scheduling.deleteShow(id);
            return Map.of("deleted", id);
        });
    }

    public record Movie(@NotBlank @Size(max = 200) String title, @Min(1) @Max(600) int durationMinutes) {
    }

    public record Cinema(@NotBlank @Size(max = 200) String name, @NotBlank @Size(max = 120) String city) {
    }

    public record Screen(@NotNull UUID cinemaId, @NotBlank @Size(max = 100) String name,
                         @NotEmpty @Size(max = 500) List<@NotNull @Pattern(regexp = "[A-Za-z0-9-]{1,12}") String> seats) {
    }

    public record Show(@NotNull UUID movieId, @NotNull UUID screenId, @NotNull @Future Instant startsAt,
                       @NotNull Instant endsAt, @Min(0) @Max(100000000) long priceMinor,
                       @NotNull @Pattern(regexp = "[A-Z]{3}") String currency) {
    }
}
