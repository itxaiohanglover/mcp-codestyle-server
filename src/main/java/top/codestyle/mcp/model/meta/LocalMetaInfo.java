package top.codestyle.mcp.model.meta;

import lombok.Data;
import top.codestyle.mcp.model.sdk.MetaInfo;

/**
 * 本地缓存的 Meta 信息结构
 *
 * @author 小航love666, Kanttha, movclantian
 * @since 2025-09-29
 */
@Data
public class LocalMetaInfo extends MetaInfo {

    /**
     * 模板文件内容
     */
    private String templateContent;

}