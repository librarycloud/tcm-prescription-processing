using System;
using System.Globalization;
using System.Net;
using System.Net.Http;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Linq;
using System.Web.Script.Serialization;
using E6Sync.Models;

namespace E6Sync.Services
{
    public sealed class ApiService : IDisposable
    {
        private readonly ApiConfig config;
        private readonly LogService log;
        private readonly HttpClient client;
        private readonly JavaScriptSerializer serializer = new JavaScriptSerializer();

        public ApiService(ApiConfig config, LogService log)
        {
            this.config = config;
            this.log = log;
            client = new HttpClient { Timeout = TimeSpan.FromSeconds(15) };
            client.DefaultRequestHeaders.ConnectionClose = true;
        }

        public async Task<ApiResult> SendAsync(E6Order order, string doctorCode, CancellationToken cancellationToken)
        {
            var requestBody = new PrescriptionRequest
            {
                externalOrderNo = order.ExternalOrderNo,
                storeCode = config.StoreCode,
                customerName = order.CustomerName ?? "",
                phone = order.CustomerPhone ?? "",
                cashierName = LimitLength(order.CashierName, 200),
                e6DoctorCode = doctorCode,
                totalPrice = order.TotalPrice.ToString("0.00", CultureInfo.InvariantCulture),
                doseCount = order.DoseCount > 0 ? order.DoseCount : 1,
                paymentStatus = order.IsPaid ? "PAID" : "UNPAID",
                sourceStatus = order.IsCancelled ? "CANCELLED" : "ACTIVE",
                items = order.Items.Select(item => new E6PrescriptionItemRequest
                {
                    sequence = item.Sequence,
                    name = LimitLength(item.Name, 200),
                    quantity = item.Quantity.ToString("0.###", CultureInfo.InvariantCulture),
                    totalQuantity = item.TotalQuantity.ToString("0.###", CultureInfo.InvariantCulture),
                    unit = item.Unit,
                    doseCount = item.DoseCount
                }).ToArray(),
                remark = LimitLength(order.PrescriptionRemark, 500),
                sourceCreatedAt = ToIso8601(order.ReceiptDate),
                sourceUpdatedAt = ToIso8601(order.ReceiptDate)
            };
            var endpoint = config.BaseUrl.TrimEnd('/') + "/integrations/e6/v1/prescriptions";
            var json = serializer.Serialize(requestBody);
            using (var request = new HttpRequestMessage(HttpMethod.Post, endpoint))
            {
                request.Headers.Add("X-API-Key", config.ApiKey);
                request.Content = new StringContent(json, Encoding.UTF8, "application/json");
                try
                {
                    using (var response = await client.SendAsync(request, HttpCompletionOption.ResponseContentRead, cancellationToken).ConfigureAwait(false))
                    {
                        var responseText = await response.Content.ReadAsStringAsync().ConfigureAwait(false);
                        var statusCode = (int)response.StatusCode;
                        log.Info(string.Format("单据 {0} API HTTP {1}", order.ExternalOrderNo, statusCode));
                        if (!response.IsSuccessStatusCode)
                            log.Error(string.Format("单据 {0} API 响应：{1}", order.ExternalOrderNo, responseText));
                        ApiResponse payload = null;
                        try { payload = serializer.Deserialize<ApiResponse>(responseText); }
                        catch { /* malformed response is reported below */ }

                        if (response.StatusCode != HttpStatusCode.OK)
                        {
                            var message = payload == null ? "HTTP 请求失败" : (payload.message ?? "HTTP 请求失败");
                            return new ApiResult
                            {
                                HttpStatus = statusCode,
                                Retryable = statusCode == 429 || statusCode >= 500,
                                Message = string.Format("HTTP {0}：{1}", statusCode, message)
                            };
                        }
                        if (payload == null)
                            return new ApiResult { HttpStatus = statusCode, Message = "响应不是有效 JSON", Retryable = false };
                        if (payload.code != 0)
                            return new ApiResult { HttpStatus = statusCode, Message = payload.message ?? "接口返回失败", Retryable = false };

                        var data = payload.data;
                        return new ApiResult
                        {
                            Success = true,
                            Duplicate = data != null && data.duplicate,
                            BusinessStatus = data == null ? (int?)null : data.status,
                            HttpStatus = statusCode,
                            Message = payload.message ?? "同步成功"
                        };
                    }
                }
                catch (TaskCanceledException)
                {
                    if (cancellationToken.IsCancellationRequested) throw;
                    log.Warn("单据 " + order.ExternalOrderNo + " 请求超时");
                    return new ApiResult { Retryable = true, Message = "网络超时（15秒）" };
                }
                catch (HttpRequestException ex)
                {
                    log.Warn("单据 " + order.ExternalOrderNo + " 网络异常：" + ex.Message);
                    return new ApiResult { Retryable = true, Message = "网络异常：" + ex.Message };
                }
                catch (Exception ex)
                {
                    log.Error("单据 " + order.ExternalOrderNo + " API 调用异常：" + ex.Message);
                    return new ApiResult { Message = "API 调用异常：" + ex.Message };
                }
            }
        }

        private static string ToIso8601(DateTime value)
        {
            var local = DateTime.SpecifyKind(value, DateTimeKind.Local);
            return new DateTimeOffset(local).ToString("yyyy-MM-dd'T'HH:mm:sszzz", CultureInfo.InvariantCulture);
        }

        private static string LimitLength(string value, int maximumLength)
        {
            var text = value ?? "";
            return text.Length <= maximumLength ? text : text.Substring(0, maximumLength);
        }

        public void Dispose() { client.Dispose(); }
    }
}
