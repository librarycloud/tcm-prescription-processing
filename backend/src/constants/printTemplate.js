export const PRINT_TEMPLATE_TYPES = Object.freeze({
  PACKAGE_PICKUP: 'PACKAGE_PICKUP',
  PROCESSING: 'PROCESSING',
  PACKAGING: 'PACKAGING',
  EQUIPMENT: 'EQUIPMENT'
});

export const PRINT_TEMPLATE_TYPE_NAMES = Object.freeze({
  PACKAGE_PICKUP: '取货标签',
  PROCESSING: '加工标签',
  PACKAGING: '包装标签',
  EQUIPMENT: '设备标签'
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
    { id: 'qrcode', label: '加工计划二维码', kind: 'qrcode' },
    { id: 'planCode', label: '加工计划码', kind: 'text' },
    { id: 'prescriptionNo', label: '处方号', kind: 'text' },
    { id: 'customerName', label: '顾客姓名', kind: 'text' },
    { id: 'doctorName', label: '医生', kind: 'text' },
    { id: 'batchNo', label: '批次号', kind: 'text' },
    { id: 'processType', label: '加工方式', kind: 'text' },
    { id: 'totalDose', label: '剂数', kind: 'text' },
    { id: 'bagCount', label: '袋数', kind: 'text' },
    { id: 'volumeMl', label: '毫升数', kind: 'text' },
    { id: 'processDate', label: '加工日期', kind: 'text' },
    { id: 'processRemark', label: '加工备注', kind: 'text' }
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
  ],
  EQUIPMENT: [
    { id: 'qrcode', label: '设备二维码', kind: 'qrcode' },
    { id: 'equipmentNo', label: '设备编号', kind: 'text' },
    { id: 'equipmentName', label: '设备名称', kind: 'text' },
    { id: 'equipmentType', label: '设备类型', kind: 'text' },
    { id: 'storeName', label: '所属门店', kind: 'text' }
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
  { id: 'qrcode', x: 2.5, y: 2.5, width: 22, height: 22, fontSize: 3, align: 'center', bold: false, wrap: false, visible: true },
  { id: 'planCode', x: 2.5, y: 25.2, width: 22, height: 3.5, fontSize: 2.65, align: 'center', bold: true, wrap: false, visible: true },
  { id: 'processType', x: 26.5, y: 2.7, width: 12, height: 4, fontSize: 3.1, align: 'center', bold: true, wrap: false, visible: true },
  { id: 'batchNo', x: 53, y: 2.7, width: 14.5, height: 4, fontSize: 3.1, align: 'right', bold: true, wrap: false, visible: true },
  { id: 'customerName', x: 26.5, y: 7.5, width: 41, height: 7.4, fontSize: 6.2, align: 'left', bold: true, wrap: false, visible: true },
  { id: 'totalDose', x: 26.5, y: 15.5, width: 10, height: 5.4, fontSize: 4.15, align: 'left', bold: true, wrap: false, visible: true },
  { id: 'bagCount', x: 37, y: 15.5, width: 11, height: 5.4, fontSize: 4.15, align: 'left', bold: true, wrap: false, visible: true },
  { id: 'volumeMl', x: 49, y: 15.5, width: 18.5, height: 5.4, fontSize: 4.15, align: 'left', bold: true, wrap: false, visible: true },
  { id: 'processDate', x: 26.5, y: 22, width: 41, height: 4, fontSize: 2.9, align: 'left', bold: true, wrap: false, visible: true },
  { id: 'prescriptionNo', x: 2.5, y: 31, width: 44, height: 3.8, fontSize: 2.75, align: 'left', bold: true, wrap: false, visible: true },
  { id: 'doctorName', x: 49, y: 31, width: 18.5, height: 3.8, fontSize: 2.75, align: 'right', bold: true, wrap: false, visible: true },
  { id: 'processRemark', x: 2.5, y: 36, width: 65, height: 11.5, fontSize: 3.05, align: 'left', bold: true, wrap: true, visible: true }
];

const thermalProcessingFields = [
  { id: 'qrcode', x: 1.5, y: 0.8, width: 24, height: 24, fontSize: 3, align: 'center', bold: false, wrap: false, visible: true },
  { id: 'planCode', x: 1.5, y: 25.1, width: 24, height: 4.2, fontSize: 2.7, align: 'center', bold: true, wrap: false, visible: true },
  { id: 'processType', x: 28, y: 0.8, width: 14, height: 4.2, fontSize: 3.2, align: 'center', bold: true, wrap: false, visible: true },
  { id: 'batchNo', x: 56, y: 0.8, width: 16, height: 4.2, fontSize: 3.2, align: 'right', bold: true, wrap: false, visible: true },
  { id: 'customerName', x: 28, y: 5.5, width: 44, height: 8.7, fontSize: 6.1, align: 'left', bold: true, wrap: false, visible: true },
  { id: 'totalDose', x: 28, y: 14.3, width: 11, height: 5.5, fontSize: 4.2, align: 'left', bold: true, wrap: false, visible: true },
  { id: 'bagCount', x: 40, y: 14.3, width: 12, height: 5.5, fontSize: 4.2, align: 'left', bold: true, wrap: false, visible: true },
  { id: 'volumeMl', x: 53, y: 14.3, width: 19, height: 5.5, fontSize: 4.2, align: 'left', bold: true, wrap: false, visible: true },
  { id: 'processDate', x: 28, y: 21, width: 44, height: 4.2, fontSize: 3, align: 'left', bold: true, wrap: false, visible: true },
  { id: 'prescriptionNo', x: 1.5, y: 29.8, width: 48, height: 4.4, fontSize: 2.7, align: 'left', bold: true, wrap: false, visible: true },
  { id: 'doctorName', x: 52, y: 29.8, width: 20, height: 4.4, fontSize: 2.7, align: 'right', bold: true, wrap: false, visible: true },
  { id: 'processRemark', x: 1.5, y: 35, width: 70.5, height: 12.2, fontSize: 3.15, align: 'left', bold: true, wrap: true, visible: true }
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

export function defaultProcessingFields(variant = 'landscape') {
  if (variant === 'portrait') return portraitProcessingFields;
  if (variant === 'thermal-75') return thermalProcessingFields;
  return processingFields;
}

export function defaultPackagingFields(orientation = 'landscape') {
  return orientation === 'portrait' ? portraitPackagingFields : packagingFields;
}

const equipmentFields = [
  { id: 'qrcode', x: 3, y: 3, width: 26, height: 26, fontSize: 3, align: 'center', bold: false, wrap: false, visible: true },
  { id: 'equipmentNo', x: 3, y: 30.5, width: 26, height: 6, fontSize: 4.2, align: 'center', bold: true, wrap: false, visible: true },
  { id: 'equipmentType', x: 32, y: 3, width: 35, height: 5, fontSize: 3.8, align: 'left', bold: true, wrap: false, visible: true },
  { id: 'equipmentName', x: 32, y: 10, width: 35, height: 13, fontSize: 6.5, align: 'left', bold: true, wrap: true, visible: true },
  { id: 'storeName', x: 32, y: 26, width: 35, height: 5, fontSize: 3.2, align: 'left', bold: false, wrap: false, visible: true },
  { id: 'custom_equipment_notice', text: '扫码记录设备', x: 3, y: 41, width: 64, height: 5, fontSize: 3.6, align: 'center', bold: true, wrap: false, visible: true }
];

export function defaultEquipmentFields() {
  return equipmentFields;
}
