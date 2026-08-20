using System;
using System.Collections.Generic;
using System.IO;
using System.Text;
using System.Web.Script.Serialization;
using E6Sync.Models;

namespace E6Sync.Services
{
    public sealed class ConfigService
    {
        private readonly object syncRoot = new object();
        private readonly JavaScriptSerializer serializer = new JavaScriptSerializer();
        public string BaseDirectory { get; }
        public string ConfigPath { get; }

        public ConfigService()
        {
            BaseDirectory = AppDomain.CurrentDomain.BaseDirectory;
            ConfigPath = Path.Combine(BaseDirectory, "config.json");
        }

        public AppConfig LoadOrCreate(out bool created)
        {
            lock (syncRoot)
            {
                created = false;
                if (!File.Exists(ConfigPath))
                {
                    var template = CreateDefault();
                    SaveInternal(template);
                    created = true;
                    return template;
                }

                var json = File.ReadAllText(ConfigPath, Encoding.UTF8);
                var config = serializer.Deserialize<AppConfig>(json) ?? CreateDefault();
                Normalize(config);
                return config;
            }
        }

        public void Save(AppConfig config)
        {
            lock (syncRoot)
            {
                Normalize(config);
                SaveInternal(BuildUpdatedJson(config));
            }
        }

        public List<string> Validate(AppConfig config)
        {
            var errors = new List<string>();
            if (config == null) { errors.Add("配置为空"); return errors; }
            if (config.E6 == null) errors.Add("缺少 e6 配置");
            else
            {
                if (string.IsNullOrWhiteSpace(config.E6.Server)) errors.Add("E6 server 未配置");
                if (string.IsNullOrWhiteSpace(config.E6.Database)) errors.Add("E6 database 未配置");
                if (string.IsNullOrWhiteSpace(config.E6.Username) && string.IsNullOrWhiteSpace(config.E6.Password) == false)
                    errors.Add("E6 username 为空时应使用 Windows 身份验证，并清空 password");
            }
            if (config.Api == null) errors.Add("缺少 api 配置");
            else
            {
                Uri baseUri;
                if (!Uri.TryCreate(config.Api.BaseUrl, UriKind.Absolute, out baseUri) ||
                    (baseUri.Scheme != Uri.UriSchemeHttp && baseUri.Scheme != Uri.UriSchemeHttps))
                    errors.Add("API baseUrl 必须是 http/https 地址");
                if (string.IsNullOrWhiteSpace(config.Api.ApiKey) || config.Api.ApiKey.Contains("xxxxxxxx")) errors.Add("API apiKey 未配置");
                if (string.IsNullOrWhiteSpace(config.Api.StoreCode)) errors.Add("API storeCode 未配置");
            }
            if (config.Sync == null) errors.Add("缺少 sync 配置");
            else if (config.Sync.IntervalSeconds < 1) errors.Add("sync intervalSeconds 必须大于 0");
            return errors;
        }

        private void SaveInternal(AppConfig config)
        {
            SaveInternal(SerializeDefaultConfig(config));
        }

        private void SaveInternal(string json)
        {
            var tempPath = ConfigPath + ".tmp";
            File.WriteAllText(tempPath, FormatJson(json), new UTF8Encoding(false));
            if (File.Exists(ConfigPath))
            {
                var backupPath = ConfigPath + ".bak";
                try
                {
                    File.Replace(tempPath, ConfigPath, backupPath, true);
                    if (File.Exists(backupPath)) File.Delete(backupPath);
                }
                catch
                {
                    File.Copy(tempPath, ConfigPath, true);
                    File.Delete(tempPath);
                }
            }
            else File.Move(tempPath, ConfigPath);
        }

        private static string FormatJson(string json)
        {
            var output = new StringBuilder();
            var indent = 0;
            var inString = false;
            var escaping = false;
            foreach (var character in json)
            {
                if (inString)
                {
                    output.Append(character);
                    if (escaping) escaping = false;
                    else if (character == '\\') escaping = true;
                    else if (character == '"') inString = false;
                    continue;
                }

                switch (character)
                {
                    case '"':
                        inString = true;
                        output.Append(character);
                        break;
                    case '{':
                    case '[':
                        output.Append(character).AppendLine();
                        indent++;
                        AppendIndent(output, indent);
                        break;
                    case '}':
                    case ']':
                        output.AppendLine();
                        indent--;
                        AppendIndent(output, indent);
                        output.Append(character);
                        break;
                    case ',':
                        output.Append(character).AppendLine();
                        AppendIndent(output, indent);
                        break;
                    case ':':
                        output.Append(": ");
                        break;
                    default:
                        if (!char.IsWhiteSpace(character)) output.Append(character);
                        break;
                }
            }
            return output.ToString() + Environment.NewLine;
        }

        private static void AppendIndent(StringBuilder output, int indent)
        {
            output.Append(' ', indent * 2);
        }

        private string BuildUpdatedJson(AppConfig config)
        {
            if (!File.Exists(ConfigPath)) return SerializeDefaultConfig(config);
            try
            {
                var root = serializer.DeserializeObject(File.ReadAllText(ConfigPath, Encoding.UTF8)) as Dictionary<string, object>;
                if (root == null) return SerializeDefaultConfig(config);
                var syncKey = FindKey(root, "sync") ?? "sync";
                var sync = root.ContainsKey(syncKey) ? root[syncKey] as Dictionary<string, object> : null;
                if (sync == null)
                {
                    sync = new Dictionary<string, object>();
                    root[syncKey] = sync;
                }
                var lastSyncKey = FindKey(sync, "lastSyncTime") ?? "lastSyncTime";
                sync[lastSyncKey] = config.Sync.LastSyncTime ?? "";
                var autoSyncEnabledKey = FindKey(sync, "autoSyncEnabled") ?? "autoSyncEnabled";
                sync[autoSyncEnabledKey] = config.Sync.AutoSyncEnabled;
                return serializer.Serialize(root);
            }
            catch
            {
                return SerializeDefaultConfig(config);
            }
        }

        private string SerializeDefaultConfig(AppConfig config)
        {
            var root = new Dictionary<string, object>
            {
                ["e6"] = new Dictionary<string, object>
                {
                    ["server"] = config.E6.Server,
                    ["database"] = config.E6.Database,
                    ["username"] = config.E6.Username,
                    ["password"] = config.E6.Password
                },
                ["api"] = new Dictionary<string, object>
                {
                    ["baseUrl"] = config.Api.BaseUrl,
                    ["apiKey"] = config.Api.ApiKey,
                    ["storeCode"] = config.Api.StoreCode
                },
                ["sync"] = new Dictionary<string, object>
                {
                    ["autoSyncEnabled"] = config.Sync.AutoSyncEnabled,
                    ["intervalSeconds"] = config.Sync.IntervalSeconds,
                    ["lastSyncTime"] = config.Sync.LastSyncTime ?? ""
                }
            };
            return serializer.Serialize(root);
        }

        private static string FindKey(Dictionary<string, object> values, string expected)
        {
            foreach (var key in values.Keys)
            {
                if (string.Equals(key, expected, StringComparison.OrdinalIgnoreCase)) return key;
            }
            return null;
        }

        private static AppConfig CreateDefault()
        {
            return new AppConfig
            {
                E6 = new E6Config(),
                Api = new ApiConfig(),
                Sync = new SyncConfig()
            };
        }

        private static void Normalize(AppConfig config)
        {
            if (config.E6 == null) config.E6 = new E6Config();
            if (config.Api == null) config.Api = new ApiConfig();
            if (config.Sync == null) config.Sync = new SyncConfig();
            if (config.Sync.IntervalSeconds < 1) config.Sync.IntervalSeconds = 60;
        }
    }
}
