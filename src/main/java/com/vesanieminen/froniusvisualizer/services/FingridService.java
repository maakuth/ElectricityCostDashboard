package com.vesanieminen.froniusvisualizer.services;

import com.fatboyindustrial.gsonjavatime.Converters;
import com.google.gson.GsonBuilder;
import com.vesanieminen.froniusvisualizer.services.model.FingridResponse;
import com.vesanieminen.froniusvisualizer.services.model.FingridWindEstimateResponse;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

public class FingridService {

    private static final ZoneId fiZoneID = ZoneId.of("Europe/Helsinki");
    private static FingridResponse finGridResponse;
    private static List<FingridWindEstimateResponse> windEstimateResponses;
    private static LocalDateTime nextUpdate = LocalDateTime.now(fiZoneID);
    private static LocalDateTime nextWindEstimateUpdate = LocalDateTime.now(fiZoneID);
    private static final String url = "https://www.fingrid.fi/api/graph/power-system-production?start=2022-10-03&end=2022-10-10";
    private static final String url2 = "https://api.fingrid.fi/v1/variable/245/events/json?start_time=2022-10-09T00%3A00%3A00%2B0300&end_time=2022-10-12T00%3A00%3A00%2B0300";

    public static FingridResponse getLatest7Days() throws URISyntaxException, IOException, InterruptedException {
        if (nextUpdate.isBefore(LocalDateTime.now(fiZoneID))) {
            final var nowWithoutMinutes = LocalDateTime.now(fiZoneID).withMinute(0);
            nextUpdate = nowWithoutMinutes.plusHours(1);
            final var request = HttpRequest.newBuilder().uri(new URI(url)).GET().build();
            final var response = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build().send(request, HttpResponse.BodyHandlers.ofString());
            final var gson = Converters.registerAll(new GsonBuilder()).create();
            finGridResponse = gson.fromJson(response.body(), FingridResponse.class);
            finGridResponse.HydroPower = keepEveryNthItem(finGridResponse.HydroPower, 20);
            finGridResponse.NuclearPower = keepEveryNthItem(finGridResponse.NuclearPower, 20);
            finGridResponse.WindPower = keepEveryNthItem(finGridResponse.WindPower, 20);
            finGridResponse.SolarPower = keepEveryNthItem(finGridResponse.SolarPower, 20);
            finGridResponse.Consumption = keepEveryNthItem(finGridResponse.Consumption, 20);
            finGridResponse.NetImportExport = keepEveryNthItem(finGridResponse.NetImportExport, 20);
        }
        return finGridResponse;
    }

    public static List<FingridResponse.Data> keepEveryNthItem(List<FingridResponse.Data> input, int n) {
        return IntStream.range(0, input.size()).filter(item -> item % n == 0).mapToObj(input::get).toList();
    }

    public static List<FingridWindEstimateResponse> getWindEstimate(Environment env) throws URISyntaxException, IOException, InterruptedException {
        if (nextWindEstimateUpdate.isBefore(LocalDateTime.now(fiZoneID))) {
            final var nowWithoutMinutes = LocalDateTime.now(fiZoneID).withMinute(0);
            nextWindEstimateUpdate = nowWithoutMinutes.plusHours(1);
            final var apiKey = env.getProperty("fingrid.api.key");
            final var request = HttpRequest.newBuilder().uri(new URI(url2)).GET().header("x-api-key", apiKey).build();
            var response = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build().send(request, HttpResponse.BodyHandlers.ofString());
            final var gson = Converters.registerAll(new GsonBuilder()).create();
            windEstimateResponses = Arrays.stream(gson.fromJson(response.body(), FingridWindEstimateResponse[].class)).toList();
        }
        return windEstimateResponses;
    }

}
