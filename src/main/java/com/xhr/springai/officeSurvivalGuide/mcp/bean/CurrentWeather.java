package com.xhr.springai.officeSurvivalGuide.mcp.bean;

public record CurrentWeather(
        String locationId,
        String cityName,
        String observationTime,
        String weather,
        String temperature,
        String feelsLike,
        String humidity,
        String windDirection,
        String windScale,
        String windSpeed
) {
}
