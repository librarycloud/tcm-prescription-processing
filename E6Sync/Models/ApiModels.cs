namespace E6Sync.Models
{
    public sealed class PrescriptionRequest
    {
        public string externalOrderNo { get; set; }
        public string storeCode { get; set; }
        public string customerName { get; set; }
        public string phone { get; set; }
        public string e6DoctorCode { get; set; }
        public string totalPrice { get; set; }
        public int doseCount { get; set; }
        public string remark { get; set; }
        public string sourceCreatedAt { get; set; }
    }

    public sealed class ApiResponse
    {
        public int code { get; set; }
        public string message { get; set; }
        public ApiResponseData data { get; set; }
    }

    public sealed class ApiResponseData
    {
        public int importId { get; set; }
        public string externalOrderNo { get; set; }
        public int status { get; set; }
        public bool duplicate { get; set; }
    }

    public sealed class ApiResult
    {
        public bool Success { get; set; }
        public bool Duplicate { get; set; }
        public bool Retryable { get; set; }
        public int HttpStatus { get; set; }
        public int? BusinessStatus { get; set; }
        public string Message { get; set; }
    }
}
