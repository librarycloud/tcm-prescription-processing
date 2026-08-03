export const PACKAGE_PICKUP_TEMPLATE_TYPE = 'PACKAGE_PICKUP';
export const PROCESSING_TEMPLATE_TYPE = 'PROCESSING';
export const PACKAGING_TEMPLATE_TYPE = 'PACKAGING';

export const PRINT_FONT_OPTIONS = Object.freeze([
  { value: 'system', label: '默认黑体' },
  { value: 'yahei', label: '微软雅黑' },
  { value: 'heiti', label: '黑体' },
  { value: 'songti', label: '宋体' },
  { value: 'kaiti', label: '楷体' },
  { value: 'arial', label: 'Arial' }
]);

const PRINT_FONT_STACKS = Object.freeze({
  system: '"Microsoft YaHei", "PingFang SC", "Noto Sans CJK SC", Arial, sans-serif',
  yahei: '"Microsoft YaHei", "PingFang SC", Arial, sans-serif',
  heiti: 'SimHei, "Heiti SC", "Microsoft YaHei", sans-serif',
  songti: 'SimSun, "Songti SC", serif',
  kaiti: 'KaiTi, "Kaiti SC", serif',
  arial: 'Arial, "Helvetica Neue", sans-serif'
});

export function printFontFamily(value) {
  return PRINT_FONT_STACKS[value] || PRINT_FONT_STACKS.system;
}

export const PRINT_FIELD_DEFINITIONS = Object.freeze([
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
]);

export const DEFAULT_PICKUP_TEMPLATE = Object.freeze({
  id: 'local-default',
  templateType: PACKAGE_PICKUP_TEMPLATE_TYPE,
  templateTypeName: '取货标签',
  name: '取货标签（横版）',
  widthMm: 70,
  heightMm: 50,
  enabled: true,
  isDefault: true,
  fields: [
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
  ]
});

export const DEFAULT_PROCESSING_TEMPLATE = Object.freeze({
  id: 'local-processing-default',
  templateType: PROCESSING_TEMPLATE_TYPE,
  templateTypeName: '加工标签',
  name: '加工标签（标准）',
  widthMm: 70,
  heightMm: 35,
  enabled: true,
  isDefault: true,
  fields: [
    { id: 'customerName', x: 3, y: 3, width: 40, height: 8, fontSize: 6, align: 'left', bold: true, wrap: false, visible: true },
    { id: 'totalDose', x: 45, y: 3, width: 22, height: 8, fontSize: 5, align: 'right', bold: true, wrap: false, visible: true },
    { id: 'bagCount', x: 3, y: 13, width: 20, height: 6, fontSize: 4, align: 'left', bold: true, wrap: false, visible: true },
    { id: 'volumeMl', x: 25, y: 13, width: 20, height: 6, fontSize: 4, align: 'left', bold: true, wrap: false, visible: true },
    { id: 'processDate', x: 3, y: 22, width: 40, height: 5, fontSize: 3.4, align: 'left', bold: false, wrap: false, visible: true },
    { id: 'processType', x: 45, y: 22, width: 22, height: 5, fontSize: 3.4, align: 'right', bold: false, wrap: false, visible: true },
    { id: 'prescriptionNo', x: 3, y: 29, width: 42, height: 4, fontSize: 2.8, align: 'left', bold: false, wrap: false, visible: false },
    { id: 'batchNo', x: 47, y: 29, width: 20, height: 4, fontSize: 2.8, align: 'right', bold: false, wrap: false, visible: false }
  ]
});

export const PORTRAIT_PROCESSING_TEMPLATE = Object.freeze({
  id: 'local-processing-portrait',
  templateType: PROCESSING_TEMPLATE_TYPE,
  templateTypeName: '加工标签',
  name: '加工标签（50×70竖版）',
  widthMm: 50,
  heightMm: 70,
  enabled: true,
  isDefault: false,
  fields: [
    { id: 'customerName', x: 3, y: 3, width: 44, height: 9, fontSize: 7, align: 'center', bold: true, wrap: false, visible: true },
    { id: 'prescriptionNo', x: 3, y: 15, width: 29, height: 5, fontSize: 2.8, align: 'left', bold: false, wrap: false, visible: true },
    { id: 'batchNo', x: 34, y: 15, width: 13, height: 5, fontSize: 3, align: 'right', bold: true, wrap: false, visible: true },
    { id: 'processType', x: 3, y: 23, width: 44, height: 8, fontSize: 5, align: 'center', bold: true, wrap: false, visible: true },
    { id: 'totalDose', x: 3, y: 34, width: 20, height: 8, fontSize: 5, align: 'center', bold: true, wrap: false, visible: true },
    { id: 'bagCount', x: 27, y: 34, width: 20, height: 8, fontSize: 5, align: 'center', bold: true, wrap: false, visible: true },
    { id: 'volumeMl', x: 3, y: 45, width: 44, height: 8, fontSize: 5, align: 'center', bold: true, wrap: false, visible: true },
    { id: 'processDate', x: 3, y: 57, width: 44, height: 5, fontSize: 3, align: 'center', bold: false, wrap: false, visible: true }
  ]
});

export const DEFAULT_PACKAGING_TEMPLATE = Object.freeze({
  id: 'local-packaging-default',
  templateType: PACKAGING_TEMPLATE_TYPE,
  templateTypeName: '包装标签',
  name: '包装标签（标准）',
  widthMm: 70,
  heightMm: 42,
  enabled: true,
  isDefault: true,
  fields: [
    { id: 'customerName', x: 3, y: 3, width: 40, height: 8, fontSize: 6, align: 'left', bold: true, wrap: false, visible: true },
    { id: 'totalDose', x: 45, y: 3, width: 22, height: 8, fontSize: 5, align: 'right', bold: true, wrap: false, visible: true },
    { id: 'bagCount', x: 3, y: 13, width: 20, height: 6, fontSize: 4, align: 'left', bold: true, wrap: false, visible: true },
    { id: 'volumeMl', x: 25, y: 13, width: 20, height: 6, fontSize: 4, align: 'left', bold: true, wrap: false, visible: true },
    { id: 'processDate', x: 47, y: 13, width: 20, height: 6, fontSize: 3.2, align: 'right', bold: false, wrap: false, visible: true },
    { id: 'usageMethod', x: 3, y: 22, width: 64, height: 12, fontSize: 4, align: 'left', bold: true, wrap: true, visible: true },
    { id: 'processType', x: 3, y: 36, width: 25, height: 4, fontSize: 2.8, align: 'left', bold: false, wrap: false, visible: false },
    { id: 'prescriptionNo', x: 30, y: 36, width: 25, height: 4, fontSize: 2.6, align: 'right', bold: false, wrap: false, visible: false },
    { id: 'batchNo', x: 57, y: 36, width: 10, height: 4, fontSize: 2.6, align: 'right', bold: false, wrap: false, visible: false }
  ]
});

export const PORTRAIT_PACKAGING_TEMPLATE = Object.freeze({
  id: 'local-packaging-portrait',
  templateType: PACKAGING_TEMPLATE_TYPE,
  templateTypeName: '包装标签',
  name: '包装标签（50×70竖版）',
  widthMm: 50,
  heightMm: 70,
  enabled: true,
  isDefault: false,
  fields: [
    { id: 'customerName', x: 3, y: 3, width: 44, height: 9, fontSize: 6.5, align: 'center', bold: true, wrap: false, visible: true },
    { id: 'prescriptionNo', x: 3, y: 14, width: 29, height: 4, fontSize: 2.6, align: 'left', bold: false, wrap: false, visible: true },
    { id: 'batchNo', x: 34, y: 14, width: 13, height: 4, fontSize: 2.8, align: 'right', bold: true, wrap: false, visible: true },
    { id: 'totalDose', x: 3, y: 21, width: 20, height: 7, fontSize: 4.5, align: 'center', bold: true, wrap: false, visible: true },
    { id: 'bagCount', x: 27, y: 21, width: 20, height: 7, fontSize: 4.5, align: 'center', bold: true, wrap: false, visible: true },
    { id: 'volumeMl', x: 3, y: 30, width: 44, height: 6, fontSize: 4, align: 'center', bold: true, wrap: false, visible: true },
    { id: 'usageMethod', x: 3, y: 39, width: 44, height: 18, fontSize: 3.6, align: 'left', bold: true, wrap: true, visible: true },
    { id: 'processType', x: 3, y: 61, width: 20, height: 5, fontSize: 3, align: 'left', bold: false, wrap: false, visible: true },
    { id: 'processDate', x: 24, y: 61, width: 23, height: 5, fontSize: 2.8, align: 'right', bold: false, wrap: false, visible: true }
  ]
});

export function cloneTemplate(template) {
  return JSON.parse(JSON.stringify(template));
}
