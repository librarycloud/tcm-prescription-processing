using System;
using System.Collections.Generic;
using System.Threading;
using System.Threading.Tasks;
using E6Sync.Models;

namespace E6Sync.Services
{
    public sealed class SyncStats
    {
        public int QueryCount { get; set; }
        public int SuccessCount { get; set; }
        public int DuplicateCount { get; set; }
        public int FailureCount { get; set; }
    }

    public sealed class SyncProgress
    {
        public E6Order Order { get; set; }
        public int Current { get; set; }
        public int Total { get; set; }
        public string Result { get; set; }
        public SyncStats Stats { get; set; }
    }

    public sealed class E6SyncService
    {
        private readonly AppConfig config;
        private readonly ConfigService configService;
        private readonly E6DatabaseService database;
        private readonly ApiService api;
        private readonly LogService log;
        private readonly SemaphoreSlim gate = new SemaphoreSlim(1, 1);

        public event Action<SyncProgress> ProgressChanged;

        public E6SyncService(AppConfig config, ConfigService configService, E6DatabaseService database, ApiService api, LogService log)
        {
            this.config = config;
            this.configService = configService;
            this.database = database;
            this.api = api;
            this.log = log;
        }

        public bool IsBusy { get; private set; }

        public async Task<SyncStats> RunAutomaticAsync(CancellationToken cancellationToken)
        {
            DateTimeOffset last;
            if (string.IsNullOrWhiteSpace(config.Sync.LastSyncTime) || !DateTimeOffset.TryParse(config.Sync.LastSyncTime, out last))
            {
                log.Warn("lastSyncTime 为空或无效，请先在 GUI 执行一次手动同步");
                return new SyncStats();
            }
            var start = last.LocalDateTime.AddMinutes(-2);
            var end = DateTime.Now;
            log.Info(string.Format("开始自动同步：{0:yyyy-MM-dd HH:mm:ss} 至 {1:yyyy-MM-dd HH:mm:ss}", start, end));
            var stats = await RunAsync(start, end, true, cancellationToken).ConfigureAwait(false);
            if (stats.FailureCount == 0 && !cancellationToken.IsCancellationRequested)
            {
                config.Sync.LastSyncTime = new DateTimeOffset(end).ToString("o");
                configService.Save(config);
                log.Info("本轮自动同步全部处理完成，已更新 lastSyncTime");
            }
            else log.Warn("本轮自动同步存在失败，未推进 lastSyncTime");
            return stats;
        }

        public async Task<SyncStats> RunManualAsync(DateTime startDate, DateTime endDate, CancellationToken cancellationToken)
        {
            var endExclusive = endDate.Date.AddDays(1);
            log.Info(string.Format("开始手动同步：{0:yyyy-MM-dd} 至 {1:yyyy-MM-dd}", startDate.Date, endDate.Date));
            var stats = await RunAsync(startDate.Date, endExclusive, false, cancellationToken).ConfigureAwait(false);
            if (stats.FailureCount == 0 && !cancellationToken.IsCancellationRequested)
            {
                config.Sync.LastSyncTime = new DateTimeOffset(DateTime.Now).ToString("o");
                configService.Save(config);
                log.Info("本轮手动同步全部处理完成，已更新 lastSyncTime");
            }
            else log.Warn("本轮手动同步存在失败，未推进 lastSyncTime");
            return stats;
        }

        private async Task<SyncStats> RunAsync(DateTime start, DateTime end, bool automatic, CancellationToken cancellationToken)
        {
            if (!await gate.WaitAsync(0, cancellationToken).ConfigureAwait(false))
            {
                log.Warn("已有同步任务正在执行，本次请求跳过");
                return new SyncStats();
            }
            IsBusy = true;
            try
            {
                var orders = await Task.Run(() => database.QueryOrders(start, end), cancellationToken).ConfigureAwait(false);
                var stats = new SyncStats { QueryCount = orders.Count };
                log.Info("查询到 " + orders.Count + " 条");
                var current = 0;
                foreach (var order in orders)
                {
                    cancellationToken.ThrowIfCancellationRequested();
                    current++;
                    var resultText = await ProcessOrderAsync(order, stats, cancellationToken).ConfigureAwait(false);
                    RaiseProgress(new SyncProgress { Order = order, Current = current, Total = orders.Count, Result = resultText, Stats = stats });
                }
                log.Info(string.Format("{0}同步完成：查询 {1}，成功 {2}，重复 {3}，失败 {4}", automatic ? "自动" : "手动", stats.QueryCount, stats.SuccessCount, stats.DuplicateCount, stats.FailureCount));
                return stats;
            }
            finally
            {
                IsBusy = false;
                gate.Release();
            }
        }

        private async Task<string> ProcessOrderAsync(E6Order order, SyncStats stats, CancellationToken cancellationToken)
        {
            if (!string.IsNullOrWhiteSpace(order.ValidationError))
            {
                stats.FailureCount++;
                log.Error("单据 " + order.ExternalOrderNo + " 同步失败：" + order.ValidationError);
                return "失败：" + order.ValidationError;
            }
            // 优先传递 E6 原始医师值；空值时以配置的 E6 编码作为后端映射键。
            var doctorCode = string.IsNullOrWhiteSpace(order.DoctorName)
                ? (config.E6.DefaultDoctorCode ?? "").Trim()
                : order.DoctorName.Trim();
            var result = await api.SendAsync(order, doctorCode, cancellationToken).ConfigureAwait(false);
            if (!result.Success)
            {
                stats.FailureCount++;
                if (result.HttpStatus == 401) log.Error("单据 " + order.ExternalOrderNo + " 失败：HTTP 401，请检查 API Key/storeCode 配置");
                else log.Error("单据 " + order.ExternalOrderNo + " 同步失败：" + result.Message);
                return "失败：" + result.Message;
            }

            if (result.Duplicate)
            {
                stats.DuplicateCount++;
                log.Info("单据 " + order.ExternalOrderNo + " 已存在（重复）");
            }
            else
            {
                stats.SuccessCount++;
                log.Info("单据 " + order.ExternalOrderNo + " 同步成功");
            }
            if (result.BusinessStatus.HasValue)
                log.Info("单据 " + order.ExternalOrderNo + " API 状态：" + DescribeStatus(result.BusinessStatus.Value));
            return result.Duplicate ? "重复" : "成功";
        }

        private void RaiseProgress(SyncProgress progress)
        {
            var handler = ProgressChanged;
            if (handler != null) handler(progress);
        }

        private static string DescribeStatus(int status)
        {
            switch (status)
            {
                case 0: return "0 待确认";
                case 1: return "1 待映射（正常业务状态）";
                case 2: return "2 导入异常";
                case 3: return "3 已生成处方";
                case 4: return "4 已驳回";
                case 5: return "5 已取消";
                case 6: return "6 数据冲突，需人工处理";
                case 7: return "7 处理中";
                default: return status + " 未知状态";
            }
        }
    }
}
