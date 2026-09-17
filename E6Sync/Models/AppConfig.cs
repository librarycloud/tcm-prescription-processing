namespace E6Sync.Models
{
    public sealed class AppConfig
    {
        public E6Config E6 { get; set; } = new E6Config();
        public E6PharmacyConfig PharmacyE6 { get; set; } = new E6PharmacyConfig();
        public ApiConfig Api { get; set; } = new ApiConfig();
        public SyncConfig Sync { get; set; } = new SyncConfig();
    }

    public sealed class E6Config
    {
        public string Server { get; set; } = "127.0.0.1";
        public string Database { get; set; } = "E6观前街中医诊所";
        public bool WindowsAuthentication { get; set; } = true;
        public string Username { get; set; } = "";
        public string Password { get; set; } = "";
        public string DefaultDoctorCode { get; set; } = "";
    }

    public sealed class E6PharmacyConfig
    {
        public string Server { get; set; } = "127.0.0.1";
        public string Database { get; set; } = "E6苏州药店";
        public bool WindowsAuthentication { get; set; } = true;
        public string Username { get; set; } = "";
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
        public bool PharmacySyncEnabled { get; set; } = false;
        public int IntervalSeconds { get; set; } = 60;
        public int PharmacyIntervalSeconds { get; set; } = 60;
        public string LastSyncTime { get; set; } = "";
        public string LastPharmacySyncTime { get; set; } = "";
        public string LastPharmacyProductModifiedAt { get; set; } = "";
        public string LastPharmacyProductCursor { get; set; } = "";
        public string LastPharmacyLocationCursor { get; set; } = "";
        public string LastPharmacyInventoryCursor { get; set; } = "";
        public string LastPharmacyStockCursor { get; set; } = "";
    }
}
