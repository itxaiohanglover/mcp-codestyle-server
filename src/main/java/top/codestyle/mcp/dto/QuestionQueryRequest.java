package top.codestyle.mcp.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 查询请求
 *
 * @author 文艺倾年
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class QuestionQueryRequest extends PageRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * id
     */
    private Long notId;
    /**
     * 编号
     */
    private Long questionNum;


    /**
     * 搜索词
     */
    private String searchText;

    /**
     * 标题
     */
    private String title;

    /**
     * 难度：1-简单, 2-中等, 3-困难
     */
    private Integer difficulty;


    /**
     * 标题列表
     */
    private List<String> orTitleList;

    /**
     * 内容
     */
    private String content;

    /**
     * 内容格式
     * 0 普通文本
     * 1 md
     * 2 富文本
     */
    private Integer contentType;

    /**
     * 标签列表
     */
    private List<String> tagList;

    /**
     * 至少有一个标签
     */
    private List<String> orTagList;

    /**
     * 状态：0-待审核, 1-通过, 2-拒绝
     */
    private Integer reviewStatus;

    /**
     * 审核信息
     */
    private String reviewMessage;

    /**
     * 审核人 id
     */
    private Long reviewerId;

    /**
     * 审核时间
     */
    private Date reviewTime;

    /**
     * 优先级
     * 999 精选
     */
    private Integer priority;

    /**
     * 题目来源
     */
    private String source;

    /**
     * 题目来源描述
     */
    private String sourceDescription;

    /**
     * 仅vip可见（1 表示仅会员可见）
     */
    private Integer needVip;


    /**
     * 是否有答案
     */
    private Boolean hasAnswer;

    /**
     * 额外权限（json 对象）
     */
    private String extraAuth;

    /**
     * 创建用户 id
     */
    private Long userId;

    /**
     * 收藏用户 id
     */
    private Long favourUserId;

    /**
     * 需要查询所属题库列表
     */
    private boolean needQueryQuestionBankList;

    /**
     * 需要查询登录用户最新的一条回答
     */
    private boolean needQueryUserQuestionAnswer;

    /**
     * 题库id
     */
    private Long questionBankId;

    /**
     * 批次id
     */
    private Long batchId;

    /**
     * 题目备注
     */
    private String note;

    private boolean hasAlias;

    /**
     * 排序字段别名
     */
    private String sortAlias;

    /**
     * 标记
     */
    private Integer mark;

    /**
     * Id 列表
     */
    private List<Long> idList;

    private static final long serialVersionUID = 1L;
}