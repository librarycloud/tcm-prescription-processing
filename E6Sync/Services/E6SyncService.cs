using System;
using System.Collections.Generic;
using System.Globalization;
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
        private readonly SemaphoreSlim clinicGate = new SemaphoreSlim(1, 1);
        private readonly SemaphoreSlim pharmacyGate = new SemaphoreSlim(1, 1);
        private readonly object configSaveLock = new object();

        public event Action<SyncProgress> ProgressChanged;

        public E6SyncService(AppConfig config, ConfigService configService, E6DatabaseService database, ApiService api, LogService log)
        {
            this.config = config;
            this.configService = configService;
            this.database = database;
            this.api = api;
            this.log = log;
        }

        public bool IsClinicBusy { get; private set; }
        public bool IsPharmacyBusy { get; private set; }
        public bool IsBusy { get { return IsClinicBusy || IsPharmacyBusy; } }

        public async Task<SyncStats> RunAutomaticAsync(CancellationToken cancellationToken)
        {
            DateTimeOffset last;
            if (string.IsNullOrWhiteSpace(config.Sync.LastSyncTime) || !DateTimeOffset.TryParse(config.Sync.LastSyncTime, out last))
            {
                log.Warn("诊所 lastSyncTime 为空或无效，请先执行诊所手动同步");
                return new SyncStats();
            }
            var start = last.LocalDateTime.AddMinutes(-2);
            var end = DateTime.Now;
            log.Info(string.Format("开始诊所自动同步：{0:yyyy-MM-dd HH:mm:ss} 至 {1:yyyy-MM-dd HH:mm:ss}", start, end));
            var stats = await RunClinicAsync(start, end, true, cancellationToken).ConfigureAwait(false);
            if (stats.FailureCount == 0 && !cancellationToken.IsCancellationRequested)
            {
                config.Sync.LastSyncTime = new DateTimeOffset(end).ToString("o");
                SaveConfig();
                log.Info("诊所自动同步完成，已更新 lastSyncTime");
            }
            else log.Warn("诊所自动同步存在失败，未推进 lastSyncTime");
            return stats;
        }

        public async Task<SyncStats> RunManualAsync(DateTime startDate, DateTime endDate, CancellationToken cancellationToken)
        {
            var endExclusive = endDate.Date.AddDays(1);
            log.Info(string.Format("开始诊所手动同步：{0:yyyy-MM-dd} 至 {1:yyyy-MM-dd}", startDate.Date, endDate.Date));
            var stats = await RunClinicAsync(startDate.Date, endExclusive, false, cancellationToken).ConfigureAwait(false);
            if (stats.FailureCount == 0 && !cancellationToken.IsCancellationRequested)
            {
                config.Sync.LastSyncTime = new DateTimeOffset(DateTime.Now).ToString("o");
                SaveConfig();
                log.Info("诊所手动同步完成，已更新 lastSyncTime");
            }
            else log.Warn("诊所手动同步存在失败，未推进 lastSyncTime");
            return stats;
        }

        public async Task<SyncStats> RunPharmacyAutomaticAsync(CancellationToken cancellationToken)
        {
            DateTimeOffset last;
            if (string.IsNullOrWhiteSpace(config.Sync.LastPharmacySyncTime) || !DateTimeOffset.TryParse(config.Sync.LastPharmacySyncTime, out last))
            {
                log.Warn("药店 lastPharmacySyncTime 为空或无效，请先执行药店手动同步");
                return new SyncStats();
            }
            log.Info("开始药店自动同步（按修改日期和 _c_ 增量）");
            var stats = await RunPharmacyAsync(false, cancellationToken).ConfigureAwait(false);
            if (stats.FailureCount == 0 && !cancellationToken.IsCancellationRequested)
            {
                config.Sync.LastPharmacySyncTime = new DateTimeOffset(DateTime.Now).ToString("o");
                SaveConfig();
                log.Info("药店自动同步完成，已更新 lastPharmacySyncTime");
            }
            else log.Warn("药店自动同步存在失败，未推进 lastPharmacySyncTime");
            return stats;
        }

        public async Task<SyncStats> RunPharmacyManualAsync(CancellationToken cancellationToken)
        {
            log.Info("开始药店手动同步：全部货位库存全量");
            var stats = await RunPharmacyAsync(true, cancellationToken).ConfigureAwait(false);
            if (stats.FailureCount == 0 && !cancellationToken.IsCancellationRequested)
            {
                config.Sync.LastPharmacySyncTime = new DateTimeOffset(DateTime.Now).ToString("o");
                SaveConfig();
                log.Info("药店手动同步完成，已更新 lastPharmacySyncTime");
            }
            else log.Warn("药店手动同步存在失败，未推进 lastPharmacySyncTime");
            return stats;
        }

        private async Task<SyncStats> RunClinicAsync(DateTime start, DateTime end, bool automatic, CancellationToken cancellationToken)
        {
            if (!await clinicGate.WaitAsync(0, cancellationToken).ConfigureAwait(false))
            {
                log.Warn("已有诊所同步任务正在执行，本次请求跳过");
                return new SyncStats();
            }
            IsClinicBusy = true;
            try
            {
                var orders = await Task.Run(() => database.QueryOrders(start, end), cancellationToken).ConfigureAwait(false);
                var stats = new SyncStats { QueryCount = orders.Count };
                log.Info("诊所查询到 " + orders.Count + " 条");
                var current = 0;
                foreach (var order in orders)
                {
                    cancellationToken.ThrowIfCancellationRequested();
                    current++;
                    var resultText = await ProcessOrderAsync(order, stats, cancellationToken).ConfigureAwait(false);
                    RaiseProgress(new SyncProgress { Order = order, Current = current, Total = orders.Count, Result = resultText, Stats = stats });
                }
                log.Info(string.Format("诊所{0}同步完成：查询 {1}，成功 {2}，重复 {3}，失败 {4}", automatic ? "自动" : "手动", stats.QueryCount, stats.SuccessCount, stats.DuplicateCount, stats.FailureCount));
                return stats;
            }
            finally
            {
                IsClinicBusy = false;
                clinicGate.Release();
            }
        }

        private async Task<SyncStats> RunPharmacyAsync(bool fullSync, CancellationToken cancellationToken)
        {
            if (!await pharmacyGate.WaitAsync(0, cancellationToken).ConfigureAwait(false))
            {
                log.Warn("已有药店同步任务正在执行，本次请求跳过");
                return new SyncStats();
            }
            IsPharmacyBusy = true;
            try
            {
                return await SyncPharmacyAsync(fullSync, cancellationToken).ConfigureAwait(false);
            }
            catch (Exception ex)
            {
                log.Error("药店商品库存同步失败：" + ex.Message);
                return new SyncStats { FailureCount = 1 };
            }
            finally
            {
                IsPharmacyBusy = false;
                pharmacyGate.Release();
            }
        }

        private async Task<SyncStats> SyncPharmacyAsync(bool fullSync, CancellationToken cancellationToken)
        {
            var stats = new SyncStats();
            var productSnapshot = await Task.Run(() => database.QueryPharmacyProducts(fullSync ? "" : config.Sync.LastPharmacyProductCursor), cancellationToken).ConfigureAwait(false);
            var products = productSnapshot.Products;
            stats.QueryCount += products.Count;
            var productBatchCount = 0;
            foreach (var productBatch in SplitBatches(products, 1000))
            {
                cancellationToken.ThrowIfCancellationRequested();
                var productResult = await api.SendPharmacyProductsAsync(productBatch, cancellationToken).ConfigureAwait(false);
                if (!productResult.Success) throw new InvalidOperationException(productResult.Message);
                stats.SuccessCount++;
                productBatchCount++;
            }
            var snapshot = await Task.Run(() => database.QueryPharmacyInventory(DateTime.Today, fullSync ? "" : config.Sync.LastPharmacyInventoryCursor, fullSync ? "" : config.Sync.LastPharmacyLocationCursor), cancellationToken).ConfigureAwait(false);
            stats.QueryCount += snapshot.Batches.Count;
            if (!fullSync && snapshot.Batches.Count > 0)
            {
                var uploadedCodes = new HashSet<string>(products.ConvertAll(item => item.productCode ?? ""), StringComparer.OrdinalIgnoreCase);
                var inventoryCodes = new List<string>();
                foreach (var batch in snapshot.Batches)
                {
                    var code = (batch.productCode ?? "").Trim();
                    if (!string.IsNullOrWhiteSpace(code) && !uploadedCodes.Contains(code)) inventoryCodes.Add(code);
                }
                if (inventoryCodes.Count > 0)
                {
                    var supplement = await Task.Run(() => database.QueryPharmacyProductsByCodes(inventoryCodes), cancellationToken).ConfigureAwait(false);
                    stats.QueryCount += supplement.Products.Count;
                    foreach (var product in supplement.Products)
                    {
                        if (uploadedCodes.Add(product.productCode ?? "")) products.Add(product);
                    }
                    foreach (var productBatch in SplitBatches(supplement.Products, 1000))
                    {
                        cancellationToken.ThrowIfCancellationRequested();
                        var productResult = await api.SendPharmacyProductsAsync(productBatch, cancellationToken).ConfigureAwait(false);
                        if (!productResult.Success) throw new InvalidOperationException(productResult.Message);
                        stats.SuccessCount++;
                        productBatchCount++;
                    }
                    log.Info(string.Format("药店增量补传库存对应商品：{0} 条", supplement.Products.Count));
                }
            }
            log.Info(string.Format("药店本地查询：商品 {0}，库存批次 {1}，模式 {2}", products.Count, snapshot.Batches.Count, fullSync ? "全量" : "增量"));
            if (fullSync && snapshot.Batches.Count == 0)
                throw new InvalidOperationException("药店货位库存查询为 0，已停止全量上传；请检查药店数据库和库存表");
            var fullSyncStartedAt = fullSync ? DateTimeOffset.UtcNow.ToString("o", CultureInfo.InvariantCulture) : null;
            var inventoryBatchCount = 0;
            var inventoryBatches = SplitBatches(snapshot.Batches, 1000);
            for (var index = 0; index < inventoryBatches.Count; index++)
            {
                cancellationToken.ThrowIfCancellationRequested();
                var lastBatch = index == inventoryBatches.Count - 1;
                var inventoryResult = await api.SendPharmacyInventoryAsync(inventoryBatches[index], fullSync, fullSync && lastBatch, fullSyncStartedAt, cancellationToken).ConfigureAwait(false);
                if (!inventoryResult.Success) throw new InvalidOperationException(inventoryResult.Message);
                stats.SuccessCount++;
                inventoryBatchCount++;
                log.Info(string.Format("药店库存上传批次 {0}/{1}：{2} 条", index + 1, inventoryBatches.Count, inventoryBatches[index].Count));
            }
            if (!string.IsNullOrWhiteSpace(productSnapshot.Cursor)) config.Sync.LastPharmacyProductCursor = productSnapshot.Cursor;
            if (!string.IsNullOrWhiteSpace(snapshot.Cursor)) config.Sync.LastPharmacyInventoryCursor = snapshot.Cursor;
            if (!string.IsNullOrWhiteSpace(snapshot.LocationCursor)) config.Sync.LastPharmacyLocationCursor = snapshot.LocationCursor;
            SaveConfig();
            log.Info(string.Format("药店同步完成：商品 {0}（{1} 批），库存批次 {2}（{3} 批）{4}", products.Count, productBatchCount, snapshot.Batches.Count, inventoryBatchCount, fullSync ? "（全部货位库存全量）" : "（_c_ 增量）"));
            return stats;
        }

        private static List<List<T>> SplitBatches<T>(IList<T> items, int size)
        {
            var result = new List<List<T>>();
            for (var offset = 0; offset < items.Count; offset += size)
            {
                var batch = new List<T>(Math.Min(size, items.Count - offset));
                for (var index = offset; index < Math.Min(offset + size, items.Count); index++) batch.Add(items[index]);
                result.Add(batch);
            }
            return result;
        }

        private async Task<string> ProcessOrderAsync(E6Order order, SyncStats stats, CancellationToken cancellationToken)
        {
            if (!string.IsNullOrWhiteSpace(order.ValidationError))
            {
                stats.FailureCount++;
                log.Error("单据 " + order.ExternalOrderNo + " 同步失败：" + order.ValidationError);
                return "失败：" + order.ValidationError;
            }
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
            if (result.BusinessStatus.HasValue) log.Info("单据 " + order.ExternalOrderNo + " API 状态：" + DescribeStatus(result.BusinessStatus.Value));
            return result.Duplicate ? "重复" : "成功";
        }

        private void SaveConfig()
        {
            lock (configSaveLock) configService.Save(config);
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
