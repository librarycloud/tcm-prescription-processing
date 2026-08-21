using System;

namespace E6Sync.Models
{
    public sealed class E6Order
    {
        public string ExternalOrderNo { get; set; }
        public DateTime ReceiptDate { get; set; }
        public decimal TotalPrice { get; set; }
        public string CustomerName { get; set; }
        public string CustomerPhone { get; set; }
        public string CashierName { get; set; }
        public string DoctorName { get; set; }
        public string PrescriptionRemark { get; set; }
    }
}
