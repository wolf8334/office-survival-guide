package com.xhr.springai.officeSurvivalGuide.mcp.bean;

import java.util.List;

public record QWeatherResponse(
        String code,
        String updateTime,
        String fxLink,
        Now now,
        Refer refer
) {

    public record Now(
            String obsTime,
            String temp,
            String feelsLike,
            String icon,
            String text,
            String wind360,
            String windDir,
            String windScale,
            String windSpeed,
            String humidity,
            String precip,
            String pressure,
            String vis,
            String cloud,
            String dew
    ) {
    }

    public record Refer(
            List<String> sources,
            List<String> license
    ) {
    }
}