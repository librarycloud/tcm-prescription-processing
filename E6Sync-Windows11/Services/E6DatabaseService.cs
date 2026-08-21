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

            const string sql = @"WITH receipts AS (
SELECT
    r.[单据id],
    CONVERT(datetime, LEFT(CONVERT(varchar(23), r.[操作日期], 121), 19), 120) AS [操作日期],
    r.[收款金额],
    p.[购药人],
    p.[购药人电话],
    p.[处方药师],
    p.[处方备注]
FROM dbo.[AC款台_零售收款记录] r
LEFT JOIN dbo.[AC款台_处方登记] p ON r.[单据id] = p.[单据ID]
)
SELECT [单据id], [操作日期], [收款金额], [购药人], [购药人电话], [处方药师], [处方备注]
FROM receipts
WHERE [操作日期] >= @start AND [操作日期] < @end
ORDER BY [操作日期], [单据id];";

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
                        var dateOrdinal = reader.GetOrdinal("操作日期");
                        var priceOrdinal = reader.GetOrdinal("收款金额");
                        var customerOrdinal = reader.GetOrdinal("购药人");
                        var phoneOrdinal = reader.GetOrdinal("购药人电话");
                        var doctorOrdinal = reader.GetOrdinal("处方药师");
                        var remarkOrdinal = reader.GetOrdinal("处方备注");
                        while (reader.Read())
                        {
                            var order = new E6Order
                            {
                                ExternalOrderNo = Convert.ToString(reader.GetValue(orderNoOrdinal)),
                                ReceiptDate = reader.IsDBNull(dateOrdinal) ? start : Convert.ToDateTime(reader.GetValue(dateOrdinal)),
                                TotalPrice = reader.IsDBNull(priceOrdinal) ? 0m : Convert.ToDecimal(reader.GetValue(priceOrdinal)),
                                CustomerName = reader.IsDBNull(customerOrdinal) ? "" : Convert.ToString(reader.GetValue(customerOrdinal)),
                                CustomerPhone = reader.IsDBNull(phoneOrdinal) ? "" : Convert.ToString(reader.GetValue(phoneOrdinal)),
                                DoctorName = reader.IsDBNull(doctorOrdinal) ? "" : Convert.ToString(reader.GetValue(doctorOrdinal)),
                                PrescriptionRemark = reader.IsDBNull(remarkOrdinal) ? "" : Convert.ToString(reader.GetValue(remarkOrdinal))
                            };
                            result.Add(order);
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
