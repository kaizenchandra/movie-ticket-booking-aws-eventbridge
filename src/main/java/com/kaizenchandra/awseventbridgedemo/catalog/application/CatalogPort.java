package com.kaizenchandra.awseventbridgedemo.catalog.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface CatalogPort {
    List<Map<String, Object>> movies(String title, int page, int size);

    List<Map<String, Object>> cinemas();


    UUID movie(String title, int duration);

    UUID cinema(String name, String city);

    UUID screen(UUID cinema, String name, List<String> seats);

    void updateMovie(UUID id, String title, int duration);

    void updateCinema(UUID id, String name, String city);

    void updateScreen(UUID id, String name, List<String> seats);

}
