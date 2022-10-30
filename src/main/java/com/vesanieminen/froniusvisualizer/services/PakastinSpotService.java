package com.vesanieminen.froniusvisualizer.services;

import com.fatboyindustrial.gsonjavatime.Converters;
import com.google.gson.GsonBuilder;
import com.vesanieminen.froniusvisualizer.services.model.PakastinResponse;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class PakastinSpotService {

    private static PakastinResponse pakastinResponse;
    private static final String url = "https://pakastin.fi/hinnat/prices";

    public static void updateData() {
        final HttpRequest request;
        final HttpResponse<String> response;
        try {
            request = HttpRequest.newBuilder().uri(new URI(url)).GET().build();
            response = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build().send(request, HttpResponse.BodyHandlers.ofString());
        } catch (URISyntaxException | IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        final var gson = Converters.registerAll(new GsonBuilder()).create();
        var newPakastinResponse = gson.fromJson(response.body(), PakastinResponse.class);
        if (newPakastinResponse.isValid()) {
            pakastinResponse = newPakastinResponse;
        }
    }

    public static PakastinResponse getLatest() {
        return pakastinResponse;
    }

}
