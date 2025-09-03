package top.codestyle.mcp.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;
import top.codestyle.mcp.model.req.ToolReq;
import top.codestyle.mcp.model.resp.ToolResp;

/**
 * @author 文艺倾年
 * @Description
 */
@Slf4j
@Service
public class GeneratorService {

    @Tool(name = "get-weather", description = "Get weather information by city name.")
    public String getWeather(ToolReq cityName) {
        ToolResp result = null;
        try {
            result = new ToolResp();
            result.setToolParamInfo(cityName.getToolParamInfo());
            log.info("mcp server run getWeather, result = {}", result);
        } catch (Exception e) {
            log.error("call mcp server failed, e:\n", e);
            return String.format("调用服务失败，异常[%s]", e.getMessage());
        }
        return result.toString();
    }
}
