using System.Collections.Generic;

namespace E6Sync.Models
{
    public sealed class E6PharmacyProductUpload
    {
        public string productCode { get; set; }
        public string name { get; set; }
        public string category { get; set; }
        public string categoryCode { get; set; }
        public string barcode { get; set; }
        public string specification { get; set; }
        public string dosageForm { get; set; }
        public string manufacturer { get; set; }
        public string categoryAttribute { get; set; }
        public string unit { get; set; }
        public string e6CreatedAt { get; set; }
        public string e6ModifiedAt { get; set; }
    }

    public sealed class E6PharmacyBatchUpload
    {
        public string productCode { get; set; }
        public string locationName { get; set; }
        public string batchNo { get; set; }
        public string productionDate { get; set; }
        public string expiryDate { get; set; }
        public string inboundDate { get; set; }
        public string quantity { get; set; }
        public string amount { get; set; }
    }

    public sealed class E6PharmacyInventorySnapshot
    {
        public List<E6PharmacyBatchUpload> Batches { get; set; } = new List<E6PharmacyBatchUpload>();
        public string Cursor { get; set; }
        public string LocationCursor { get; set; }
    }

    public sealed class E6PharmacyProductSnapshot
    {
        public List<E6PharmacyProductUpload> Products { get; set; } = new List<E6PharmacyProductUpload>();
        public string Cursor { get; set; }
    }

    public sealed class PrescriptionRequest
    {
        public string externalOrderNo { get; set; }
        public string storeCode { get; set; }
        public string customerName { get; set; }
        public string phone { get; set; }
        public string cashierName { get; set; }
        public string e6DoctorCode { get; set; }
        public string totalPrice { get; set; }
        public int doseCount { get; set; }
        public string paymentStatus { get; set; }
        public string sourceStatus { get; set; }
        public E6PrescriptionItemRequest[] items { get; set; }
        public string remark { get; set; }
        public string sourceCreatedAt { get; set; }
        public string sourceUpdatedAt { get; set; }
    }

    public sealed class E6PrescriptionItemRequest
    {
        public int sequence { get; set; }
        public string name { get; set; }
        public string quantity { get; set; }
        public string totalQuantity { get; set; }
        public string unit { get; set; }
        public int doseCount { get; set; }
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
