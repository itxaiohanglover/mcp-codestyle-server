package top.codestyle.mcp.model.entity;

import cn.hutool.db.meta.Column;
import cn.hutool.db.meta.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.*;

/**
 * 表数据
 */
@Data
@NoArgsConstructor
public class TableEntity implements Serializable, Cloneable{
    //数据库类型
    private String DbType;
    //表的名称
    private String tableName;
    //表的备注
    private String comment;
    //表的主键
    private Set<String> pkNames = new LinkedHashSet<>();
    //表的列名(不包含主键)
    private final Map<String, Column> columns = new LinkedHashMap<>();

    //类名(第一个字母大写)，如：sys_user => SysUser
    private String className;
    //类名(第一个字母小写)，如：sys_user => sysUser
    private String classname;

    public Table clone() throws CloneNotSupportedException {
        return (Table)super.clone();
    }


}
	

