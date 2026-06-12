package com.xhr.springai.officeSurvivalGuide.mcp;

import com.xhr.springai.officeSurvivalGuide.mcp.bean.CurrentWeather;
import com.xhr.springai.officeSurvivalGuide.mcp.service.QWeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SystemMcpTools {

    private final QWeatherService weatherService;

    @McpTool(name = "ping",description = "MCP Server 状态")
    public String ping(@McpToolParam(description = "客户端发送的文本",required = false) String message) {
        return "MCP Server is running. message=" + message;
    }

    @McpTool(name = "GetWeather",description = "根据和风天气城市编码查询指定城市的实时天气")
    public CurrentWeather getCurrentWeather(
            @McpToolParam(description = "城市名称",required = true) String cityName) {
        return weatherService.getCurrentWeather(cityName);
    }
}
