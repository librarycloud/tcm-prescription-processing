using System;
using System.Collections.Generic;

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
        public bool IsPaid { get; set; }
        public int DoseCount { get; set; }
        public string ValidationError { get; set; }
        public List<E6PrescriptionItem> Items { get; set; } = new List<E6PrescriptionItem>();
    }

    public sealed class E6PrescriptionItem
    {
        public int Sequence { get; set; }
        public string Name { get; set; }
        public decimal Quantity { get; set; }
        public string Unit { get; set; }
    }
}
