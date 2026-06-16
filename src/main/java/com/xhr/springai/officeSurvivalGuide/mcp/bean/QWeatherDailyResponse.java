package com.xhr.springai.officeSurvivalGuide.mcp.bean;

import java.util.List;

public record QWeatherDailyResponse(
        String code,
        String updateTime,
        List<Daily> daily
) {
    public record Daily(
            String fxDate,
            String sunrise,
            String sunset,
            String tempMax,
            String tempMin,
            String textDay,
            String textNight,
            String windDirDay,
            String windScaleDay,
            String windSpeedDay,
            String humidity,
            String precip,
            String pressure,
            String vis,
            String cloud,
            String uvIndex
    ) {
    }
}