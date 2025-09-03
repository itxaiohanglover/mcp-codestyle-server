package top.codestyle.mcp.service;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import top.codestyle.mcp.dto.BaseResponse;
import top.codestyle.mcp.dto.Page;
import top.codestyle.mcp.dto.QuestionQueryRequest;
import top.codestyle.mcp.dto.QuestionVO;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.StringJoiner;

/**
 * @author gulihua
 * @Description
 * @date 2025-03-26 11:35
 */
@Slf4j
@Service
public class MianshiyaService {

    @Value("${endpoint.mianshiya.searchQuestion}")
    private String baseUrl;

    @Value("${endpoint.mianshiya.resultLink}")
    private String resultLink;


    /**
     * 根据搜索词搜索面试鸭面试题目
     *
     * @param searchText 搜索词
     * @return 面试鸭搜索结果的题目链接
     */
    @Tool(description = "根据搜索词搜索面试鸭面试题目（如果用户提的问题的技术面试题，优先搜索面试鸭的题目列表）")
    public String questionSearch(String searchText) {
        QuestionQueryRequest request = new QuestionQueryRequest();
        request.setSearchText(searchText);

        // 调用编程导航
        String result = null;
        try {
            HttpResponse response = HttpRequest.post(baseUrl).body(JSONUtil.toJsonStr(request)).timeout(2000).execute();
            result = response.body();
            log.info("call mianshiya , result = {}", result);
            if (response.getStatus() != 200) {
                return String.format("面试鸭搜索服务异常，状态码[%s]", response.getStatus());
            }

            BaseResponse<?> resp = JSONUtil.toBean(result, BaseResponse.class);
            int code = resp.getCode();
            if (code == 0) {
                Page<?> page = JSONUtil.toBean(JSONUtil.toJsonStr(resp.getData()), Page.class);
                if (page.getTotal() == 0) {
                    return "无搜索结果";
                } else {
                    int i = 0;
                    StringJoiner joiner = new StringJoiner("\n");
                    for (Object obj : page.getRecords()) {
                        if (i++ >= 5) {
                            break;
                        }
                        QuestionVO questionVO = JSONUtil.toBean(JSONUtil.toJsonStr(obj), QuestionVO.class);
                        String title = questionVO.getTitle();
                        Long id = questionVO.getId();
                        String link = String.format(resultLink, id);
                        joiner.add(String.format("- [%s](%s)", title, link));
                    }
                    log.info("mcp mianshiya server , result = {}", joiner);
                    return joiner.toString();
                }

            } else {
                log.error("call mianshiya failed, code = {}, message = {}", code, resp.getMessage());
                return String.format("面试鸭搜索服务异常，响应码[%s]", code);
            }
        } catch (Exception e) {
            log.error("call mianshiya failed, e:\n", e);
            return String.format("调用面试鸭搜索服务失败，异常[%s]", e.getMessage());
        }
    }
}
