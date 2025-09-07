package top.codestyle.mcp.model.resp;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 生成编码
 */
@Data
public class GeneratePreviewResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "生成的文件路径", example = "continew-admin\\continew-admin\\continew-admin-generator\\src\\main\\java\\top\\continew\\admin\\generator\\service")
    private String path;

    /**
     * 文件名
     */
    @Schema(description = "文件名", example = "UserController.java")
    private String fileName;

    /**
     * 内容
     */
    @Schema(description = "内容", example = "public class UserController {...}")
    private String content;

    /**
     * 是否为后端代码
     */
    @Schema(hidden = true)
    @JsonIgnore
    private boolean backend;
}
