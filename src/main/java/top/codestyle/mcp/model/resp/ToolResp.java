package top.codestyle.mcp.model.resp;

import lombok.Data;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.Serial;
import java.io.Serializable;

/**
 * 工具响应
 */
@Data
public class ToolResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ToolParam(description = "工具参数信息") // 暴露工具参数信息
    private String ToolParamInfo;
}
