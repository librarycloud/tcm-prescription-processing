export const PRINT_TEMPLATE_TYPES = Object.freeze({
  PACKAGE_PICKUP: 'PACKAGE_PICKUP',
  PROCESSING: 'PROCESSING',
  PACKAGING: 'PACKAGING'
});

export const PRINT_TEMPLATE_TYPE_NAMES = Object.freeze({
  PACKAGE_PICKUP: '取货标签',
  PROCESSING: '加工标签',
  PACKAGING: '包装标签'
});

export const PRINT_FONT_FAMILIES = Object.freeze([
  { value: 'system', label: '默认黑体' },
  { value: 'yahei', label: '微软雅黑' },
  { value: 'heiti', label: '黑体' },
  { value: 'songti', label: '宋体' },
  { value: 'kaiti', label: '楷体' },
  { value: 'arial', label: 'Arial' }
]);

export const PRINT_FIELD_DEFINITIONS = Object.freeze({
  PACKAGE_PICKUP: [
    { id: 'qrcode', label: '二维码', kind: 'qrcode' },
    { id: 'pickupCode', label: '取货码', kind: 'text' },
    { id: 'itemName', label: '物品名称', kind: 'text' },
    { id: 'itemInfo', label: '备注信息', kind: 'text' },
    { id: 'receiverName', label: '收件人', kind: 'text' },
    { id: 'receiverPhone', label: '手机号', kind: 'text' },
    { id: 'pickupMethod', label: '取货方式', kind: 'text' },
    { id: 'createdAt', label: '录入时间', kind: 'text' },
    { id: 'pickedAt', label: '取货时间', kind: 'text' },
    { id: 'storeName', label: '所属门店', kind: 'text' }
  ],
  PROCESSING: [
    { id: 'prescriptionNo', label: '处方号', kind: 'text' },
    { id: 'customerName', label: '顾客姓名', kind: 'text' },
    { id: 'batchNo', label: '批次号', kind: 'text' },
    { id: 'processType', label: '加工方式', kind: 'text' },
    { id: 'totalDose', label: '剂数', kind: 'text' },
    { id: 'bagCount', label: '袋数', kind: 'text' },
    { id: 'volumeMl', label: '毫升数', kind: 'text' },
    { id: 'processDate', label: '加工日期', kind: 'text' }
  ],
  PACKAGING: [
    { id: 'prescriptionNo', label: '处方号', kind: 'text' },
    { id: 'customerName', label: '顾客姓名', kind: 'text' },
    { id: 'batchNo', label: '批次号', kind: 'text' },
    { id: 'processType', label: '加工方式', kind: 'text' },
    { id: 'totalDose', label: '剂数', kind: 'text' },
    { id: 'bagCount', label: '袋数', kind: 'text' },
    { id: 'volumeMl', label: '毫升数', kind: 'text' },
    { id: 'processDate', label: '加工日期', kind: 'text' },
    { id: 'usageMethod', label: '服用方法', kind: 'text' }
  ]
});

const horizontalPickupFields = [
  { id: 'qrcode', x: 2.5, y: 4, width: 26, height: 26, fontSize: 3, align: 'center', bold: false, wrap: false, visible: true },
  { id: 'pickupCode', x: 31, y: 4, width: 36, height: 8, fontSize: 7.5, align: 'left', bold: true, wrap: false, visible: true },
  { id: 'itemName', x: 31, y: 13, width: 36, height: 5, fontSize: 3.8, align: 'left', bold: true, wrap: false, visible: true },
  { id: 'receiverName', x: 31, y: 21, width: 36, height: 6, fontSize: 4.8, align: 'left', bold: true, wrap: false, visible: true },
  { id: 'receiverPhone', x: 31, y: 28, width: 36, height: 6, fontSize: 4.4, align: 'left', bold: true, wrap: false, visible: true },
  { id: 'pickupMethod', x: 2.5, y: 33, width: 26, height: 5, fontSize: 3.6, align: 'center', bold: true, wrap: false, visible: true },
  { id: 'createdAt', x: 31, y: 36, width: 36, height: 5, fontSize: 2.8, align: 'left', bold: false, wrap: false, visible: true },
  { id: 'itemInfo', x: 31, y: 42, width: 36, height: 4, fontSize: 2.6, align: 'left', bold: false, wrap: false, visible: false },
  { id: 'pickedAt', x: 31, y: 42, width: 36, height: 4, fontSize: 2.6, align: 'left', bold: false, wrap: false, visible: false },
  { id: 'storeName', x: 31, y: 46, width: 36, height: 3, fontSize: 2.4, align: 'left', bold: false, wrap: false, visible: false }
];

const verticalPickupFields = [
  { id: 'qrcode', x: 12, y: 4, width: 26, height: 26, fontSize: 3, align: 'center', bold: false, wrap: false, visible: true },
  { id: 'pickupMethod', x: 10, y: 31, width: 30, height: 5, fontSize: 3.5, align: 'center', bold: true, wrap: false, visible: true },
  { id: 'pickupCode', x: 5, y: 37, width: 40, height: 8, fontSize: 7, align: 'center', bold: true, wrap: false, visible: true },
  { id: 'itemName', x: 5, y: 46, width: 40, height: 5, fontSize: 3.5, align: 'center', bold: true, wrap: false, visible: true },
  { id: 'receiverName', x: 5, y: 52, width: 40, height: 6, fontSize: 4.3, align: 'center', bold: true, wrap: false, visible: true },
  { id: 'receiverPhone', x: 5, y: 58, width: 40, height: 5, fontSize: 4.1, align: 'center', bold: true, wrap: false, visible: true },
  { id: 'createdAt', x: 5, y: 64, width: 40, height: 4, fontSize: 2.4, align: 'center', bold: false, wrap: false, visible: true },
  { id: 'itemInfo', x: 5, y: 68, width: 40, height: 2, fontSize: 2.2, align: 'center', bold: false, wrap: false, visible: false },
  { id: 'pickedAt', x: 5, y: 68, width: 40, height: 2, fontSize: 2.2, align: 'center', bold: false, wrap: false, visible: false },
  { id: 'storeName', x: 5, y: 69, width: 40, height: 1, fontSize: 2, align: 'center', bold: false, wrap: false, visible: false }
];

export function defaultPickupFields(orientation = 'landscape') {
  return orientation === 'portrait' ? verticalPickupFields : horizontalPickupFields;
}

const processingFields = [
  { id: 'customerName', x: 3, y: 3, width: 40, height: 8, fontSize: 6, align: 'left', bold: true, wrap: false, visible: true },
  { id: 'totalDose', x: 45, y: 3, width: 22, height: 8, fontSize: 5, align: 'right', bold: true, wrap: false, visible: true },
  { id: 'bagCount', x: 3, y: 13, width: 20, height: 6, fontSize: 4, align: 'left', bold: true, wrap: false, visible: true },
  { id: 'volumeMl', x: 25, y: 13, width: 20, height: 6, fontSize: 4, align: 'left', bold: true, wrap: false, visible: true },
  { id: 'processDate', x: 3, y: 22, width: 40, height: 5, fontSize: 3.4, align: 'left', bold: false, wrap: false, visible: true },
  { id: 'processType', x: 45, y: 22, width: 22, height: 5, fontSize: 3.4, align: 'right', bold: false, wrap: false, visible: true },
  { id: 'prescriptionNo', x: 3, y: 29, width: 42, height: 4, fontSize: 2.8, align: 'left', bold: false, wrap: false, visible: false },
  { id: 'batchNo', x: 47, y: 29, width: 20, height: 4, fontSize: 2.8, align: 'right', bold: false, wrap: false, visible: false }
];

const portraitProcessingFields = [
  { id: 'customerName', x: 3, y: 3, width: 44, height: 9, fontSize: 7, align: 'center', bold: true, wrap: false, visible: true },
  { id: 'prescriptionNo', x: 3, y: 15, width: 29, height: 5, fontSize: 2.8, align: 'left', bold: false, wrap: false, visible: true },
  { id: 'batchNo', x: 34, y: 15, width: 13, height: 5, fontSize: 3, align: 'right', bold: true, wrap: false, visible: true },
  { id: 'processType', x: 3, y: 23, width: 44, height: 8, fontSize: 5, align: 'center', bold: true, wrap: false, visible: true },
  { id: 'totalDose', x: 3, y: 34, width: 20, height: 8, fontSize: 5, align: 'center', bold: true, wrap: false, visible: true },
  { id: 'bagCount', x: 27, y: 34, width: 20, height: 8, fontSize: 5, align: 'center', bold: true, wrap: false, visible: true },
  { id: 'volumeMl', x: 3, y: 45, width: 44, height: 8, fontSize: 5, align: 'center', bold: true, wrap: false, visible: true },
  { id: 'processDate', x: 3, y: 57, width: 44, height: 5, fontSize: 3, align: 'center', bold: false, wrap: false, visible: true }
];

const packagingFields = [
  { id: 'customerName', x: 3, y: 3, width: 40, height: 8, fontSize: 6, align: 'left', bold: true, wrap: false, visible: true },
  { id: 'totalDose', x: 45, y: 3, width: 22, height: 8, fontSize: 5, align: 'right', bold: true, wrap: false, visible: true },
  { id: 'bagCount', x: 3, y: 13, width: 20, height: 6, fontSize: 4, align: 'left', bold: true, wrap: false, visible: true },
  { id: 'volumeMl', x: 25, y: 13, width: 20, height: 6, fontSize: 4, align: 'left', bold: true, wrap: false, visible: true },
  { id: 'processDate', x: 47, y: 13, width: 20, height: 6, fontSize: 3.2, align: 'right', bold: false, wrap: false, visible: true },
  { id: 'usageMethod', x: 3, y: 22, width: 64, height: 12, fontSize: 4, align: 'left', bold: true, wrap: true, visible: true },
  { id: 'processType', x: 3, y: 36, width: 25, height: 4, fontSize: 2.8, align: 'left', bold: false, wrap: false, visible: false },
  { id: 'prescriptionNo', x: 30, y: 36, width: 25, height: 4, fontSize: 2.6, align: 'right', bold: false, wrap: false, visible: false },
  { id: 'batchNo', x: 57, y: 36, width: 10, height: 4, fontSize: 2.6, align: 'right', bold: false, wrap: false, visible: false }
];

const portraitPackagingFields = [
  { id: 'customerName', x: 3, y: 3, width: 44, height: 9, fontSize: 6.5, align: 'center', bold: true, wrap: false, visible: true },
  { id: 'prescriptionNo', x: 3, y: 14, width: 29, height: 4, fontSize: 2.6, align: 'left', bold: false, wrap: false, visible: true },
  { id: 'batchNo', x: 34, y: 14, width: 13, height: 4, fontSize: 2.8, align: 'right', bold: true, wrap: false, visible: true },
  { id: 'totalDose', x: 3, y: 21, width: 20, height: 7, fontSize: 4.5, align: 'center', bold: true, wrap: false, visible: true },
  { id: 'bagCount', x: 27, y: 21, width: 20, height: 7, fontSize: 4.5, align: 'center', bold: true, wrap: false, visible: true },
  { id: 'volumeMl', x: 3, y: 30, width: 44, height: 6, fontSize: 4, align: 'center', bold: true, wrap: false, visible: true },
  { id: 'usageMethod', x: 3, y: 39, width: 44, height: 18, fontSize: 3.6, align: 'left', bold: true, wrap: true, visible: true },
  { id: 'processType', x: 3, y: 61, width: 20, height: 5, fontSize: 3, align: 'left', bold: false, wrap: false, visible: true },
  { id: 'processDate', x: 24, y: 61, width: 23, height: 5, fontSize: 2.8, align: 'right', bold: false, wrap: false, visible: true }
];

export function defaultProcessingFields(orientation = 'landscape') {
  return orientation === 'portrait' ? portraitProcessingFields : processingFields;
}

export function defaultPackagingFields(orientation = 'landscape') {
  return orientation === 'portrait' ? portraitPackagingFields : packagingFields;
}
