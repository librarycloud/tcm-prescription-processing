using System;
using System.Collections.Generic;
using System.Data;
using System.Data.SqlClient;
using E6Sync.Models;

namespace E6Sync.Services
{
    public sealed class E6DatabaseService
    {
        private readonly AppConfig config;
        private readonly LogService log;

        public E6DatabaseService(AppConfig config, LogService log)
        {
            this.config = config;
            this.log = log;
        }

        public void TestConnection()
        {
            var e6 = config.E6;
            var builder = new SqlConnectionStringBuilder
            {
                DataSource = e6.Server,
                InitialCatalog = e6.Database,
                ConnectTimeout = 5,
                IntegratedSecurity = e6.WindowsAuthentication
            };
            if (!builder.IntegratedSecurity)
            {
                builder.UserID = e6.Username;
                builder.Password = e6.Password;
            }
            using (var connection = new SqlConnection(builder.ConnectionString))
            {
                connection.Open();
            }
        }

        public void TestPharmacyConnection()
        {
            using (var connection = new SqlConnection(BuildConnectionString(config.PharmacyE6))) connection.Open();
        }

        public List<E6Order> QueryOrders(DateTime start, DateTime end)
        {
            var result = new List<E6Order>();
            var e6 = config.E6;
            var builder = new SqlConnectionStringBuilder
            {
                DataSource = e6.Server,
                InitialCatalog = e6.Database,
                ConnectTimeout = 5,
                IntegratedSecurity = e6.WindowsAuthentication
            };
            if (!builder.IntegratedSecurity)
            {
                builder.UserID = e6.Username;
                builder.Password = e6.Password;
            }

            const string sql = @"SELECT
    counter.[id] AS [单据id],
    CONVERT(datetime, LEFT(CONVERT(varchar(23), counter.[检测日期], 121), 19), 120) AS [订单日期],
    counter.[总额] AS [收款金额],
    counter.[购药人],
    counter.[购药人电话],
    counter.[处方药师],
    counter.[处方备注],
    cashier.[操作员],
    counter.[_proofstate],
    detail.[商品名称],
    detail.[单付数量],
    detail.[数量],
    detail.[单位],
    detail.[付数],
    detail.[ri]
FROM dbo.[PF新零售收款台_处方明细] detail
INNER JOIN dbo.[PF新零售收款台] counter ON counter.[id] = detail.[PID]
OUTER APPLY (
    SELECT TOP 1 CONVERT(nvarchar(200), source_receipt.[操作员]) AS [操作员]
    FROM dbo.[AC款台_零售收款记录] source_receipt
    WHERE source_receipt.[单据id] = counter.[id]
    ORDER BY source_receipt.[操作日期] DESC
) cashier
WHERE CONVERT(datetime, LEFT(CONVERT(varchar(23), counter.[检测日期], 121), 19), 120) >= @start
  AND CONVERT(datetime, LEFT(CONVERT(varchar(23), counter.[检测日期], 121), 19), 120) < @end
ORDER BY [订单日期], counter.[id], detail.[ri];";

            try
            {
                using (var connection = new SqlConnection(builder.ConnectionString))
                using (var command = new SqlCommand(sql, connection))
                {
                    command.CommandType = CommandType.Text;
                    command.CommandTimeout = 30;
                    command.Parameters.Add("@start", SqlDbType.DateTime).Value = start;
                    command.Parameters.Add("@end", SqlDbType.DateTime).Value = end;
                    connection.Open();
                    using (var reader = command.ExecuteReader())
                    {
                        var orderNoOrdinal = reader.GetOrdinal("单据id");
                        var dateOrdinal = reader.GetOrdinal("订单日期");
                        var priceOrdinal = reader.GetOrdinal("收款金额");
                        var customerOrdinal = reader.GetOrdinal("购药人");
                        var phoneOrdinal = reader.GetOrdinal("购药人电话");
                        var cashierOrdinal = reader.GetOrdinal("操作员");
                        var doctorOrdinal = reader.GetOrdinal("处方药师");
                        var remarkOrdinal = reader.GetOrdinal("处方备注");
                        var proofStateOrdinal = reader.GetOrdinal("_proofstate");
                        var itemNameOrdinal = reader.GetOrdinal("商品名称");
                        var itemQuantityOrdinal = reader.GetOrdinal("单付数量");
                        var itemTotalQuantityOrdinal = reader.GetOrdinal("数量");
                        var itemUnitOrdinal = reader.GetOrdinal("单位");
                        var doseCountOrdinal = reader.GetOrdinal("付数");
                        var sequenceOrdinal = reader.GetOrdinal("ri");
                        var byOrderNo = new Dictionary<string, E6Order>();
                        while (reader.Read())
                        {
                            var externalOrderNo = Convert.ToString(reader.GetValue(orderNoOrdinal));
                            E6Order order;
                            if (!byOrderNo.TryGetValue(externalOrderNo, out order))
                            {
                                var proofState = reader.IsDBNull(proofStateOrdinal) ? "" : Convert.ToString(reader.GetValue(proofStateOrdinal)).Trim();
                                order = new E6Order
                                {
                                    ExternalOrderNo = externalOrderNo,
                                    ReceiptDate = reader.IsDBNull(dateOrdinal) ? start : Convert.ToDateTime(reader.GetValue(dateOrdinal)),
                                    TotalPrice = reader.IsDBNull(priceOrdinal) ? 0m : Convert.ToDecimal(reader.GetValue(priceOrdinal)),
                                    CustomerName = reader.IsDBNull(customerOrdinal) ? "" : Convert.ToString(reader.GetValue(customerOrdinal)),
                                    CustomerPhone = reader.IsDBNull(phoneOrdinal) ? "" : Convert.ToString(reader.GetValue(phoneOrdinal)),
                                    CashierName = reader.IsDBNull(cashierOrdinal) ? "" : Convert.ToString(reader.GetValue(cashierOrdinal)),
                                    DoctorName = reader.IsDBNull(doctorOrdinal) ? "" : Convert.ToString(reader.GetValue(doctorOrdinal)),
                                    PrescriptionRemark = reader.IsDBNull(remarkOrdinal) ? "" : Convert.ToString(reader.GetValue(remarkOrdinal)),
                                    IsPaid = proofState == "结单",
                                    IsCancelled = proofState == "作废"
                                };
                                byOrderNo.Add(externalOrderNo, order);
                                result.Add(order);
                            }

                            if (reader.IsDBNull(itemNameOrdinal)) continue;
                            var itemName = Convert.ToString(reader.GetValue(itemNameOrdinal)).Trim();
                            var unit = reader.IsDBNull(itemUnitOrdinal) ? "" : Convert.ToString(reader.GetValue(itemUnitOrdinal)).Trim();
                            if (string.IsNullOrWhiteSpace(itemName))
                            {
                                order.ValidationError = "处方明细存在空商品名称";
                                continue;
                            }
                            decimal multiplier;
                            string normalizedUnit;
                            if (unit == "10g" || unit == "10克") multiplier = 10m;
                            else if (unit == "g" || unit == "1g" || unit == "1克" || unit == "克") multiplier = 1m;
                            else if (unit == "条" || unit == "个") multiplier = 1m;
                            else
                            {
                                order.ValidationError = "处方明细「" + itemName + "」单位不支持：" + unit;
                                continue;
                            }
                            normalizedUnit = unit == "条" || unit == "个" ? unit : "g";
                            decimal singleDoseQuantity;
                            try { singleDoseQuantity = reader.IsDBNull(itemQuantityOrdinal) ? 0m : Convert.ToDecimal(reader.GetValue(itemQuantityOrdinal)); }
                            catch { order.ValidationError = "处方明细「" + itemName + "」单付数量无效"; continue; }
                            if (singleDoseQuantity <= 0m)
                            {
                                order.ValidationError = "处方明细「" + itemName + "」单付数量必须大于零";
                                continue;
                            }
                            decimal totalQuantity;
                            try { totalQuantity = reader.IsDBNull(itemTotalQuantityOrdinal) ? 0m : Convert.ToDecimal(reader.GetValue(itemTotalQuantityOrdinal)); }
                            catch { order.ValidationError = "处方明细「" + itemName + "」数量无效"; continue; }
                            if (totalQuantity <= 0m)
                            {
                                order.ValidationError = "处方明细「" + itemName + "」数量必须大于零";
                                continue;
                            }
                            int doseCount;
                            try { doseCount = reader.IsDBNull(doseCountOrdinal) ? 0 : Convert.ToInt32(reader.GetValue(doseCountOrdinal)); }
                            catch { order.ValidationError = "处方明细「" + itemName + "」付数无效"; continue; }
                            if (doseCount <= 0)
                            {
                                order.ValidationError = "处方明细「" + itemName + "」付数必须为正整数";
                                continue;
                            }
                            int sequence;
                            try { sequence = reader.IsDBNull(sequenceOrdinal) ? 0 : Convert.ToInt32(reader.GetValue(sequenceOrdinal)); }
                            catch { order.ValidationError = "处方明细「" + itemName + "」ri 无效"; continue; }
                            if (sequence <= 0)
                            {
                                order.ValidationError = "处方明细「" + itemName + "」ri 必须为正整数";
                                continue;
                            }
                            order.DoseCount = Math.Max(order.DoseCount, doseCount);
                            var quantityInGrams = singleDoseQuantity * multiplier;
                            order.Items.Add(new E6PrescriptionItem
                            {
                                Sequence = sequence,
                                Name = itemName,
                                Quantity = quantityInGrams,
                                TotalQuantity = totalQuantity * multiplier,
                                Unit = normalizedUnit,
                                DoseCount = doseCount
                            });
                        }
                    }
                }
                return result;
            }
            catch (Exception ex)
            {
                log.Error("SQL 查询异常：" + ex.Message);
                throw;
            }
        }

        public E6PharmacyProductSnapshot QueryPharmacyProducts(string cursor)
        {
            var result = new E6PharmacyProductSnapshot();
            var cursorBytes = DecodeCursor(cursor);
            var cursorClause = cursorBytes == null ? "" : " AND p.[_c_] > @cursor ";
            var sql = @"SELECT p.[编号], p.[名称], p.[分类], p.[分类编号], p.[条形码], p.[规格], p.[剂型], p.[生产厂商], p.[商品类别属性], p.[单位], p.[创建日期], p.[修改日期], p.[_c_]
FROM dbo.[DC商品] p
WHERE EXISTS (SELECT 1 FROM dbo.[AC货位商品帐] i WHERE i.[商品id] = p.[ID] AND i.[数量] > 0) " + cursorClause + @"
 ORDER BY p.[_c_];";
            using (var connection = new SqlConnection(BuildConnectionString(config.PharmacyE6)))
            using (var command = new SqlCommand(sql, connection))
            {
                if (cursorBytes != null) command.Parameters.Add("@cursor", SqlDbType.Binary, 8).Value = cursorBytes;
                connection.Open();
                using (var reader = command.ExecuteReader())
                {
                    while (reader.Read())
                    {
                        result.Products.Add(new E6PharmacyProductUpload
                        {
                            productCode = Convert.ToString(reader["编号"])?.Trim(),
                            name = Convert.ToString(reader["名称"])?.Trim(),
                            category = ToNullableText(reader["分类"]),
                            categoryCode = ToNullableText(reader["分类编号"]),
                            barcode = ToNullableText(reader["条形码"]),
                            specification = ToNullableText(reader["规格"]),
                            dosageForm = ToNullableText(reader["剂型"]),
                            manufacturer = ToNullableText(reader["生产厂商"]),
                            categoryAttribute = ToNullableText(reader["商品类别属性"]),
                            unit = ToNullableText(reader["单位"]),
                            e6CreatedAt = ToIso(reader["创建日期"]),
                            e6ModifiedAt = ToIso(reader["修改日期"])
                        });
                        result.Cursor = Convert.ToBase64String((byte[])reader["_c_"]);
                    }
                }
            }
            return result;
        }

        public E6PharmacyInventorySnapshot QueryPharmacyInventory(DateTime inventoryDate, string cursor, string locationCursor)
        {
            var result = new E6PharmacyInventorySnapshot();
            var cursorBytes = DecodeCursor(cursor);
            var locationCursorBytes = DecodeCursor(locationCursor);
            var cursorClause = cursorBytes == null && locationCursorBytes == null ? "" : @" AND EXISTS (
    SELECT 1
    FROM dbo.[AC货位商品帐] changed
    LEFT JOIN dbo.[DC货位] changedLocation ON changedLocation.[ID] = changed.[货位id]
    WHERE changed.[商品id] = i.[商品id]
      AND ISNULL(changed.[批号], '') = ISNULL(i.[批号], '')" +
                ((cursorBytes == null && locationCursorBytes == null) ? "" : " AND (" +
                    (cursorBytes == null ? "" : "changed.[_c_] > @cursor") +
                    (cursorBytes == null || locationCursorBytes == null ? "" : " OR ") +
                    (locationCursorBytes == null ? "" : "changedLocation.[_c_] > @locationCursor") + ")") + @") ";
            var sql = @"SELECT p.[编号] AS [商品编号], l.[名称] AS [货位名称], i.[批号], i.[生产日期], i.[有效期至], i.[入库时间], i.[数量], i.[金额], i.[_c_], l.[_c_] AS [货位_c_]
FROM dbo.[AC货位商品帐] i
LEFT JOIN dbo.[DC商品] p ON p.[ID] = i.[商品id]
LEFT JOIN dbo.[DC货位] l ON l.[ID] = i.[货位id]
WHERE i.[数量] > 0 " + cursorClause + "ORDER BY i.[_c_];";
            using (var connection = new SqlConnection(BuildConnectionString(config.PharmacyE6)))
            using (var command = new SqlCommand(sql, connection))
            {
                if (cursorBytes != null) command.Parameters.Add("@cursor", SqlDbType.Binary, 8).Value = cursorBytes;
                if (locationCursorBytes != null) command.Parameters.Add("@locationCursor", SqlDbType.Binary, 8).Value = locationCursorBytes;
                connection.Open();
                using (var reader = command.ExecuteReader())
                {
                    while (reader.Read())
                    {
                        result.Batches.Add(new E6PharmacyBatchUpload
                        {
                            productCode = ToNullableText(reader["商品编号"]),
                            locationName = ToNullableText(reader["货位名称"]),
                            batchNo = ToNullableText(reader["批号"]) ?? "",
                            productionDate = ToDate(reader["生产日期"]),
                            expiryDate = ToDate(reader["有效期至"]),
                            inboundDate = ToDate(reader["入库时间"]),
                            quantity = Convert.ToDecimal(reader["数量"]).ToString("0.###", System.Globalization.CultureInfo.InvariantCulture),
                            amount = Convert.ToDecimal(reader["金额"]).ToString("0.##", System.Globalization.CultureInfo.InvariantCulture)
                        });
                        result.Cursor = MaxCursor(result.Cursor, reader["_c_"]);
                        if (!reader.IsDBNull(reader.GetOrdinal("货位_c_"))) result.LocationCursor = MaxCursor(result.LocationCursor, reader["货位_c_"]);
                    }
                }
            }
            return result;
        }

        private static string BuildConnectionString(E6PharmacyConfig e6)
        {
            var builder = new SqlConnectionStringBuilder { DataSource = e6.Server, InitialCatalog = e6.Database, ConnectTimeout = 5, IntegratedSecurity = e6.WindowsAuthentication };
            if (!builder.IntegratedSecurity) { builder.UserID = e6.Username; builder.Password = e6.Password; }
            return builder.ConnectionString;
        }

        private static string ToNullableText(object value) => value == null || value == DBNull.Value ? null : Convert.ToString(value)?.Trim();
        private static string ToIso(object value) => value == null || value == DBNull.Value ? null : Convert.ToDateTime(value).ToString("o");
        private static string ToDate(object value) => value == null || value == DBNull.Value ? null : Convert.ToDateTime(value).ToString("yyyy-MM-dd");
        private static byte[] DecodeCursor(string value)
        {
            if (string.IsNullOrWhiteSpace(value)) return null;
            try { var bytes = Convert.FromBase64String(value); return bytes.Length == 8 ? bytes : null; }
            catch { return null; }
        }

        private static string MaxCursor(string current, object value)
        {
            if (value == null || value == DBNull.Value) return current;
            var candidate = (byte[])value;
            if (string.IsNullOrWhiteSpace(current)) return Convert.ToBase64String(candidate);
            var existing = Convert.FromBase64String(current);
            for (var i = 0; i < candidate.Length; i++)
            {
                if (candidate[i] == existing[i]) continue;
                return candidate[i] > existing[i] ? Convert.ToBase64String(candidate) : current;
            }
            return current;
        }
    }
}
