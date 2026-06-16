package com.xhr.springai.officeSurvivalGuide.mcp.tools;

import com.xhr.springai.officeSurvivalGuide.mcp.bean.CurrentWeather;
import com.xhr.springai.officeSurvivalGuide.mcp.bean.QWeatherDailyResponse;
import com.xhr.springai.officeSurvivalGuide.mcp.service.QWeatherService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WeatherTools {

    private static final Logger log = LoggerFactory.getLogger(WeatherTools.class);

    private final QWeatherService weatherService;

    @Tool(description = "获取指定城市的实时天气信息，适用于当前天气情况")
    public CurrentWeather weatherFunction(@ToolParam(description = "城市名称，例如南京、上海、北京、南京") String cityName) {
        CurrentWeather weather = weatherService.getCurrentWeather(cityName);
        log.info("weatherFunction: {}",weather);
        return weather;
    }

    @Tool(description = "获取指定城市未来几天的天气预报。适用于用户询问未来几天、一周内、10天内、15天内、30天内的天气趋势。如果用户问明天或后天，days 应传 3d。如果用户问一周天气，days 应传 7d。")
    public QWeatherDailyResponse getFutureWeather(@ToolParam(description = "城市名称，例如南京、上海、北京、南京") String cityName,@ToolParam(description = "预报天数范围，只能是 3d、7d、10d、15d、30d。明天或后天使用 3d，一周使用 7d") String days) {
        QWeatherDailyResponse weather = weatherService.getFutureWeather(cityName,days);
        log.info("getFutureWeather: {}",weather);
        return weather;
    }
}
