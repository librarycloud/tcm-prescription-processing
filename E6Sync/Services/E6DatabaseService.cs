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

            const string sql = @"SELECT
    r.[单据id],
    r.[单据日期],
    r.[收款金额],
    p.[购药人],
    p.[处方药师]
FROM dbo.[AC款台_零售收款记录] r
LEFT JOIN dbo.[AC款台_处方登记] p ON r.[单据id] = p.[单据ID]
WHERE r.[单据日期] >= @start AND r.[单据日期] < @end
ORDER BY r.[单据日期], r.[单据ID];";

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
                        var dateOrdinal = reader.GetOrdinal("单据日期");
                        var priceOrdinal = reader.GetOrdinal("收款金额");
                        var customerOrdinal = reader.GetOrdinal("购药人");
                        var doctorOrdinal = reader.GetOrdinal("处方药师");
                        while (reader.Read())
                        {
                            var order = new E6Order
                            {
                                ExternalOrderNo = Convert.ToString(reader.GetValue(orderNoOrdinal)),
                                ReceiptDate = reader.IsDBNull(dateOrdinal) ? start : Convert.ToDateTime(reader.GetValue(dateOrdinal)),
                                TotalPrice = reader.IsDBNull(priceOrdinal) ? 0m : Convert.ToDecimal(reader.GetValue(priceOrdinal)),
                                CustomerName = reader.IsDBNull(customerOrdinal) ? "" : Convert.ToString(reader.GetValue(customerOrdinal)),
                                DoctorName = reader.IsDBNull(doctorOrdinal) ? "" : Convert.ToString(reader.GetValue(doctorOrdinal))
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
