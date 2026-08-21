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

            const string sql = @"WITH receipt_summary AS (
SELECT
    receipt.[单据id],
    MAX(CONVERT(datetime, LEFT(CONVERT(varchar(23), receipt.[操作日期], 121), 19), 120)) AS [订单日期],
    SUM(ISNULL(receipt.[收款金额], 0)) AS [收款金额]
FROM dbo.[AC款台_零售收款记录] receipt
INNER JOIN (SELECT DISTINCT [PID] FROM dbo.[PF新零售收款台_处方明细]) detail_ids
    ON detail_ids.[PID] = receipt.[单据id]
GROUP BY receipt.[单据id]
)
SELECT
    counter.[id] AS [单据id],
    receipt.[订单日期],
    ISNULL(counter.[收款金额], 0) AS [收款金额],
    counter.[购药人],
    counter.[购药人电话],
    counter.[处方药师],
    counter.[处方备注],
    cashier.[操作员],
    counter.[_proofstate],
    detail.[商品名称],
    detail.[单付数量],
    detail.[单位],
    detail.[付数],
    detail.[ri]
FROM dbo.[PF新零售收款台_处方明细] detail
INNER JOIN dbo.[PF新零售收款台] counter ON counter.[id] = detail.[PID]
INNER JOIN receipt_summary receipt ON receipt.[单据id] = counter.[id]
OUTER APPLY (
    SELECT TOP 1 CONVERT(nvarchar(200), source_receipt.[操作员]) AS [操作员]
    FROM dbo.[AC款台_零售收款记录] source_receipt
    WHERE source_receipt.[单据id] = counter.[id]
    ORDER BY source_receipt.[操作日期] DESC
) cashier
WHERE ISNULL(CONVERT(nvarchar(50), counter.[_proofstate]), N'') <> N'作废'
  AND receipt.[订单日期] >= @start
  AND receipt.[订单日期] < @end
ORDER BY receipt.[订单日期], counter.[id], detail.[ri];";

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
                                    IsPaid = proofState == "结单"
                                };
                                byOrderNo.Add(externalOrderNo, order);
                                result.Add(order);
                            }

                            if (reader.IsDBNull(itemNameOrdinal)) continue;
                            var itemName = Convert.ToString(reader.GetValue(itemNameOrdinal)).Trim();
                            var unit = reader.IsDBNull(itemUnitOrdinal) ? "" : Convert.ToString(reader.GetValue(itemUnitOrdinal)).Trim();
                            if (string.IsNullOrWhiteSpace(itemName)) { order.ValidationError = "处方明细存在空商品名称"; continue; }
                            decimal multiplier;
                            if (unit == "10g" || unit == "10克") multiplier = 10m;
                            else if (unit == "1g" || unit == "1克") multiplier = 1m;
                            else { order.ValidationError = "处方明细「" + itemName + "」单位不支持：" + unit; continue; }
                            decimal singleDoseQuantity;
                            try { singleDoseQuantity = reader.IsDBNull(itemQuantityOrdinal) ? 0m : Convert.ToDecimal(reader.GetValue(itemQuantityOrdinal)); }
                            catch { order.ValidationError = "处方明细「" + itemName + "」单付数量无效"; continue; }
                            if (singleDoseQuantity <= 0m) { order.ValidationError = "处方明细「" + itemName + "」单付数量必须大于零"; continue; }
                            int doseCount;
                            try { doseCount = reader.IsDBNull(doseCountOrdinal) ? 0 : Convert.ToInt32(reader.GetValue(doseCountOrdinal)); }
                            catch { order.ValidationError = "处方明细「" + itemName + "」付数无效"; continue; }
                            if (doseCount <= 0) { order.ValidationError = "处方明细「" + itemName + "」付数必须为正整数"; continue; }
                            if (order.DoseCount > 0 && order.DoseCount != doseCount) { order.ValidationError = "同一订单的处方明细付数不一致"; continue; }
                            int sequence;
                            try { sequence = reader.IsDBNull(sequenceOrdinal) ? 0 : Convert.ToInt32(reader.GetValue(sequenceOrdinal)); }
                            catch { order.ValidationError = "处方明细「" + itemName + "」ri 无效"; continue; }
                            if (sequence <= 0) { order.ValidationError = "处方明细「" + itemName + "」ri 必须为正整数"; continue; }
                            order.DoseCount = doseCount;
                            order.Items.Add(new E6PrescriptionItem { Sequence = sequence, Name = itemName, Quantity = singleDoseQuantity * multiplier, Unit = "g" });
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
    }
}
