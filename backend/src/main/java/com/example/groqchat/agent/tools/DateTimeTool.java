package com.example.groqchat.agent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@Component
public class DateTimeTool {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm:ss a z");

    private static final Map<String, String> COUNTRY_TIMEZONE_MAP = Map.ofEntries(
            Map.entry("bangladesh", "Asia/Dhaka"),
            Map.entry("bd", "Asia/Dhaka"),
            Map.entry("dhaka", "Asia/Dhaka"),
            Map.entry("india", "Asia/Kolkata"),
            Map.entry("in", "Asia/Kolkata"),
            Map.entry("pakistan", "Asia/Karachi"),
            Map.entry("pk", "Asia/Karachi"),
            Map.entry("sri lanka", "Asia/Colombo"),
            Map.entry("nepal", "Asia/Kathmandu"),
            Map.entry("japan", "Asia/Tokyo"),
            Map.entry("jp", "Asia/Tokyo"),
            Map.entry("tokyo", "Asia/Tokyo"),
            Map.entry("china", "Asia/Shanghai"),
            Map.entry("cn", "Asia/Shanghai"),
            Map.entry("south korea", "Asia/Seoul"),
            Map.entry("korea", "Asia/Seoul"),
            Map.entry("singapore", "Asia/Singapore"),
            Map.entry("thailand", "Asia/Bangkok"),
            Map.entry("malaysia", "Asia/Kuala_Lumpur"),
            Map.entry("indonesia", "Asia/Jakarta"),
            Map.entry("uae", "Asia/Dubai"),
            Map.entry("dubai", "Asia/Dubai"),
            Map.entry("saudi arabia", "Asia/Riyadh"),
            Map.entry("qatar", "Asia/Qatar"),
            Map.entry("turkey", "Europe/Istanbul"),
            Map.entry("iran", "Asia/Tehran"),
            Map.entry("uk", "Europe/London"),
            Map.entry("united kingdom", "Europe/London"),
            Map.entry("england", "Europe/London"),
            Map.entry("london", "Europe/London"),
            Map.entry("france", "Europe/Paris"),
            Map.entry("paris", "Europe/Paris"),
            Map.entry("germany", "Europe/Berlin"),
            Map.entry("italy", "Europe/Rome"),
            Map.entry("spain", "Europe/Madrid"),
            Map.entry("netherlands", "Europe/Amsterdam"),
            Map.entry("russia", "Europe/Moscow"),
            Map.entry("usa", "America/New_York"),
            Map.entry("us", "America/New_York"),
            Map.entry("united states", "America/New_York"),
            Map.entry("new york", "America/New_York"),
            Map.entry("los angeles", "America/Los_Angeles"),
            Map.entry("chicago", "America/Chicago"),
            Map.entry("canada", "America/Toronto"),
            Map.entry("brazil", "America/Sao_Paulo"),
            Map.entry("mexico", "America/Mexico_City"),
            Map.entry("argentina", "America/Argentina/Buenos_Aires"),
            Map.entry("australia", "Australia/Sydney"),
            Map.entry("new zealand", "Pacific/Auckland")
    );

    public String getDateTime(String location) {
        log.info("DateTime Tool invoked for location: {}", location);
        try {
            ZoneId zoneId = resolveTimezone(location);
            ZonedDateTime now = ZonedDateTime.now(zoneId);
            return "Current date and time in " + location + " (" + zoneId.getId() + "): " + now.format(FORMATTER);
        } catch (Exception e) {
            log.warn("Could not resolve timezone for: {}", location);
            return "Unable to determine timezone for '" + location + "'.";
        }
    }

    private ZoneId resolveTimezone(String location) {
        String normalized = location.trim().toLowerCase();

        if (COUNTRY_TIMEZONE_MAP.containsKey(normalized)) {
            return ZoneId.of(COUNTRY_TIMEZONE_MAP.get(normalized));
        }

        try {
            return ZoneId.of(location.trim());
        } catch (Exception ignored) {
        }

        return ZoneId.getAvailableZoneIds().stream()
                .filter(id -> id.toLowerCase().contains(normalized))
                .findFirst()
                .map(ZoneId::of)
                .orElseThrow(() -> new RuntimeException("Unknown timezone: " + location));
    }
}
