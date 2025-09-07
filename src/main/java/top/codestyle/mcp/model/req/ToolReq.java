/*
 * Copyright (c) 2025-present IPBD Organization. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package top.codestyle.mcp.model.req;
import lombok.Data;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.Serial;
import java.io.Serializable;

/**
 * 工具请求
 *
 * @author 文艺倾年
 */
@Data
public class ToolReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     *
     */
    @ToolParam(description = "工具参数信息") // 暴露工具参数信息
    private String ToolParamInfo;
}
