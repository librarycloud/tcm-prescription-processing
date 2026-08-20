namespace E6Sync.Models
{
    public sealed class AppConfig
    {
        public E6Config E6 { get; set; } = new E6Config();
        public ApiConfig Api { get; set; } = new ApiConfig();
        public SyncConfig Sync { get; set; } = new SyncConfig();
    }

    public sealed class E6Config
    {
        public string Server { get; set; } = "127.0.0.1";
        public string Database { get; set; } = "E6观前街中医诊所";
        public string Username { get; set; } = "sa";
        public string Password { get; set; } = "";
    }

    public sealed class ApiConfig
    {
        public string BaseUrl { get; set; } = "";
        public string ApiKey { get; set; } = "";
        public string StoreCode { get; set; } = "";
    }

    public sealed class SyncConfig
    {
        public bool AutoSyncEnabled { get; set; } = true;
        public int IntervalSeconds { get; set; } = 60;
        public string LastSyncTime { get; set; } = "";
    }
}
