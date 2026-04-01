package com.example.groqchat.agent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class WeatherTool {

    private final RestClient restClient = RestClient.create();

    public String getWeather(String location) {
        log.info("Weather Tool invoked for location: {}", location);
        try {
            String encodedLocation = URLEncoder.encode(location, StandardCharsets.UTF_8);
            String url = "https://wttr.in/" + encodedLocation + "?format=%C+%t+Humidity:+%h+Wind:+%w";

            String result = restClient.get()
                    .uri(url)
                    .header("User-Agent", "curl/7.0")
                    .retrieve()
                    .body(String.class);

            return "Weather in " + location + ": " + result;
        } catch (Exception e) {
            log.error("Weather API error for {}: {}", location, e.getMessage());
            return "Unable to fetch weather for " + location + ". Service may be temporarily unavailable.";
        }
    }
}
