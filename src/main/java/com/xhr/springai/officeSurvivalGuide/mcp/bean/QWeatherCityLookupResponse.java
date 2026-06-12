package com.xhr.springai.officeSurvivalGuide.mcp.bean;

import java.util.List;

public record QWeatherCityLookupResponse(
        String code,
        List<Location> location
) {

    public record Location(
            String name,
            String id,
            String lat,
            String lon,
            String adm2,
            String adm1,
            String country,
            String tz,
            String utcOffset,
            String isDst,
            String type,
            String rank,
            String fxLink
    ) {
    }
}
