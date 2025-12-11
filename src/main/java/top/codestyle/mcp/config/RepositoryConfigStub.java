package top.codestyle.mcp.config;


public class RepositoryConfigStub extends RepositoryConfig {

    private final boolean remoteEnabled;
    private final String  localRepoDir;
    private final String  remoteBaseUrl;

    public RepositoryConfigStub(boolean remoteEnabled,
                                String localRepoDir,
                                String remoteBaseUrl) {
        this.remoteEnabled = remoteEnabled;
        this.localRepoDir  = localRepoDir;
        this.remoteBaseUrl = remoteBaseUrl;
    }

    @Override
    public boolean isRemoteSearchEnabled() { return remoteEnabled; }

    @Override
    public String getRepositoryDir() { return localRepoDir; }

    @Override
    public String getRemotePath()    { return remoteBaseUrl; }
}