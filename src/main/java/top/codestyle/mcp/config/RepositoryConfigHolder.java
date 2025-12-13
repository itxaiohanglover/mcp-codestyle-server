package top.codestyle.mcp.config;


import java.nio.file.Path;
import java.nio.file.Paths;

public final class RepositoryConfigHolder {

    /* ========== 静态字段 ========== */
    private static String localPath;
    private static String remotePath;
    private static String repositoryDir;
    private static boolean remoteSearchEnabled;
    private static Boolean pluginOverrideRemote;
    /* Spring 启动时注入 */
    public static void inject(RepositoryConfig cfg) {
        localPath = cfg.getLocalPath();
        remotePath = cfg.getRemotePath();
        repositoryDir = cfg.getRepositoryDir();
        remoteSearchEnabled = cfg.isRemoteSearchEnabled();
    }
    /* ---------- 2. Maven 插件手动注入 ---------- */
    public static void manualSet(String local, String remote, String dir, Boolean remoteEnabled) {
        localPath = local;
        remotePath = remote;
        repositoryDir = dir;
        pluginOverrideRemote = remoteEnabled;
    }
    /* ---------- 下面全部是 static 方法 ---------- */
    public static String getLocalPath() {
        return localPath;
    }

    public static String getRemotePath() {
        return remotePath;
    }

    public static String getRepositoryDir() {
        return repositoryDir == null || repositoryDir.isBlank()
                ? getLocalPath() + java.io.File.separator + "codestyle-cache"
                : repositoryDir;
    }

    public static boolean isRemoteSearchEnabled() {
        return pluginOverrideRemote != null ? pluginOverrideRemote : remoteSearchEnabled;
    }
    public static Path buildRepositoryPath() {
        String dir = getRepositoryDir();
        return Paths.get(dir);
    }

}
