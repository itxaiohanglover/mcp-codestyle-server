package top.codestyle.mcp.dto;

import lombok.Data;

import java.util.List;

/**
 * 分页请求
 *
 * @author 文艺倾年
 */
@Data
public class PageRequest {

    /**
     * 当前页号
     */
    private long current = 1;

    /**
     * 页面大小
     */
    private long pageSize = 10;

    /**
     * 升序排序字段
     */
    private List<String> ascSortField;

    /**
     * 降序排序字段
     */
    private List<String> descSortField;

    /**
     * 排序规则
     */
    private List<Sorter> sorterList;

    @Data
    public static class Sorter {
        /**
         * 排序属性
         */
        private String field;

        /**
         * 排序规则，是否升序
         */
        private boolean asc;
    }

}
