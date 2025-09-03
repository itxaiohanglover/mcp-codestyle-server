package top.codestyle.mcp.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 题目视图
 *
 * @author 文艺倾年
 */
@Data
public class QuestionVO implements Serializable {

    /**
     * id
     */
    private Long id;
    /**
     * 编号
     */
    private Long questionNum;

    /**
     * 回答id
     */
    private Long questionAnswerId;
    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;


    /**
     * 难度：简单 = 1，中等 = 3，困难 = 5
     */
    private Integer difficulty;


    /**
     * 内容格式
     * 0 普通文本
     * 1 md
     * 2 富文本
     */
    private Integer contentType;

    /**
     * 浏览数
     */
    private Integer viewNum;

    /**
     * 点赞数
     */
    private Integer thumbNum;

    /**
     * 收藏数
     */
    private Integer favourNum;

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
     * 额外权限（json 对象）
     */
    private String extraAuth;

    /**
     * 批次id
     */
    private Long batchId;

    /**
     * 题目备注
     */
    private String note;

    /**
     * 创建用户 id
     */
    private Long userId;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 标签列表
     */
    private List<String> tagList;

    /**
     * 有会员题目权限
     */
    private boolean hasVipAuth;

    /**
     * 有最佳答案
     */
    private Boolean hasBestAnswer;
    /**
     * 有答案
     */
    private Boolean hasAnswer;

    /**
     * 最佳答案id
     */
    private Long bestAnswerId;

    /**
     * 登录用户最新的答案id
     */
    private Long userNewestAnswerId;

    /**
     * 是否已点赞
     */
    private Boolean hasThumb;

    /**
     * 是否已收藏
     */
    private Boolean hasFavour;

    /**
     * 是否是新题目
     */
    private Boolean isNew;


    /**
     * 所属题库 id 列表
     */
    private List<Long> questionBankIdList;


    /**
     * 上次访问的所属题库id
     */
    private Long questionBankId;


    /**
     * 当前用户查看次数
     */
    private Long userViewNum;

    /**
     * 最新浏览时间
     */
    private Date lastViewTime;
    /**
     * 标记
     */
    private String mark;
    /**
     * 分享码
     */
    private String shareCode;


    /**
     * 文件id
     */
    private String fileId;


    /**
     * 是否有选择题
     */
    private Boolean hasChoiceQuestion;

}
