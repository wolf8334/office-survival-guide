package com.xhr.springai.officeSurvivalGuide.mcp.service;

import com.xhr.springai.officeSurvivalGuide.mcp.bean.CurrentWeather;
import com.xhr.springai.officeSurvivalGuide.mcp.bean.QWeatherCityLookupResponse;
import com.xhr.springai.officeSurvivalGuide.mcp.bean.QWeatherDailyResponse;
import com.xhr.springai.officeSurvivalGuide.mcp.bean.QWeatherResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
public class QWeatherService {

    private final RestClient restClient;
    private final String apiKey;

    public QWeatherService(
            RestClient.Builder restClientBuilder,
            @Value("${custom.weather.base-url}") String baseUrl,
            @Value("${custom.weather.api-key}") String apiKey) {

        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .build();
        this.apiKey = apiKey;
    }

    public CurrentWeather getCurrentWeather(String cityName) {
        QWeatherCityLookupResponse.Location location = lookupCity(cityName);
        String locationId = location.id();

        QWeatherResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v7/weather/now")
                        .queryParam("location", locationId)
                        .build())
                .header("X-QW-Api-Key", apiKey)
                .retrieve()
                .body(QWeatherResponse.class);

        if (response == null) {
            throw new IllegalStateException("和风天气接口未返回数据");
        }

        if (!"200".equals(response.code())) {
            throw new IllegalStateException(
                    "和风天气查询失败，返回代码：" + response.code());
        }

        if (response.now() == null) {
            throw new IllegalStateException("和风天气返回结果中不存在 now 数据");
        }

        QWeatherResponse.Now now = response.now();

        return new CurrentWeather(
                locationId,
                cityName,
                now.obsTime(),
                now.text(),
                now.temp(),
                now.feelsLike(),
                now.humidity(),
                now.windDir(),
                now.windScale(),
                now.windSpeed()
        );
    }

    public QWeatherDailyResponse getFutureWeather(String cityName, String days) {
        QWeatherCityLookupResponse.Location location = lookupCity(cityName);
        String locationId = location.id();

        QWeatherDailyResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v7/weather/{days}")
                        .queryParam("location", locationId)
                        .queryParam("lang", "zh")
                        .queryParam("unit", "m")
                        .build(days))
                .header("X-QW-Api-Key", apiKey)
                .retrieve()
                .body(QWeatherDailyResponse.class);

        if (response == null) {
            throw new IllegalStateException("和风天气返回为空");
        }

        if (!"200".equals(response.code())) {
            throw new IllegalStateException("查询和风天气失败，code=" + response.code());
        }

        return response;
    }

    public QWeatherCityLookupResponse.Location lookupCity(String cityName) {
        if (cityName == null || cityName.isBlank()) {
            throw new IllegalArgumentException("城市名称不能为空");
        }

        QWeatherCityLookupResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/geo/v2/city/lookup")
                        .queryParam("location", cityName)
                        .queryParam("number", 10)
                        .build())
                .header("X-QW-Api-Key", apiKey)
                .retrieve()
                .body(QWeatherCityLookupResponse.class);

        if (response == null) {
            throw new IllegalStateException("和风城市查询接口未返回数据");
        }

        if (!"200".equals(response.code())) {
            throw new IllegalStateException("和风城市查询失败，返回代码：" + response.code());
        }

        if (response.location() == null || response.location().isEmpty()) {
            throw new IllegalArgumentException("未找到城市：" + cityName);
        }

        return response.location().getFirst();
    }

    public List<QWeatherCityLookupResponse.Location> searchCities(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }

        QWeatherCityLookupResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/geo/v2/city/lookup")
                        .queryParam("location", keyword)
                        .queryParam("number", 10)
                        .queryParam("range", "cn")
                        .build())
                .header("X-QW-Api-Key", apiKey)
                .retrieve()
                .body(QWeatherCityLookupResponse.class);

        if (response == null || !"200".equals(response.code()) || response.location() == null) {
            return List.of();
        }

        return response.location();
    }
}