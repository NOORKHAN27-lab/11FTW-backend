package com.elevenftw.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Turns the free-text address the poster types (e.g. "Model Town Park,
 * Lahore") into a lat/lng pair via Google's Geocoding API, so distance
 * search works without ever asking the poster to touch a map.
 *
 * If geocoding fails (bad address, API quota, network issue) this returns
 * null coordinates rather than throwing — the match still gets created,
 * it just won't show up in distance-filtered searches until re-geocoded.
 * That's a deliberate trade-off: a typo in an address shouldn't block
 * someone from posting a match.
 */
@Service
public class GeocodingService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.google.maps-api-key}")
    private String apiKey;

    public record LatLng(Double lat, Double lng) {
        static final LatLng EMPTY = new LatLng(null, null);
    }

    @SuppressWarnings("unchecked")
    public LatLng geocode(String addressText) {
        try {
            String encoded = URLEncoder.encode(addressText, StandardCharsets.UTF_8);
            String url = "https://maps.googleapis.com/maps/api/geocode/json?address="
                    + encoded + "&region=pk&key=" + apiKey;

            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response == null || !"OK".equals(response.get("status"))) {
                return LatLng.EMPTY;
            }

            List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
            Map<String, Object> geometry = (Map<String, Object>) results.get(0).get("geometry");
            Map<String, Object> location = (Map<String, Object>) geometry.get("location");

            double lat = ((Number) location.get("lat")).doubleValue();
            double lng = ((Number) location.get("lng")).doubleValue();
            return new LatLng(lat, lng);
        } catch (Exception e) {
            return LatLng.EMPTY;
        }
    }
}
