package top.codestyle.mcp.util;

import java.util.Map;

public final class AuthUtil {
    private static final Map<String,String> AK_SK = Map.of(
            "user1ak", "user1sk",
            "user2ak", "user2sk");
    public static void verify(String ak, String sk){
        if(!sk.equals(AK_SK.get(ak))){
            throw new IllegalArgumentException("AK/SK 无效");
        }
    }
}