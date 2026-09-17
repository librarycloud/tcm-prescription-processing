export const ROBOT_PLATFORMS = Object.freeze({
  WECOM: "wecom",
  DINGTALK: "dingtalk",
  FEISHU: "feishu",
});

export const ROBOT_SCOPE_TYPES = Object.freeze({
  HEADQUARTERS: "HEADQUARTERS",
  STORE: "STORE",
});

export const ROBOT_DELIVERY_STATUS = Object.freeze({
  PENDING: 0,
  SENDING: 1,
  RETRYING: 2,
  SUCCESS: 3,
  FAILED: 4,
});

export const ROBOT_DELIVERY_STATUS_VALUES = Object.freeze(
  Object.values(ROBOT_DELIVERY_STATUS),
);

const commonVariables = [
  { key: "eventTime", label: "事件时间" },
  { key: "operatorName", label: "操作人" },
  { key: "storeName", label: "门店名称" },
];

export const ROBOT_EVENT_DEFINITIONS = Object.freeze({
  PACKAGE_CREATED: {
    name: "包裹已创建",
    businessType: "PACKAGE",
    defaultEnabled: true,
    variables: [
      ...commonVariables,
      { key: "packageId", label: "包裹编号" },
      { key: "pickupCode", label: "取货码" },
      { key: "receiverName", label: "收件人" },
      { key: "receiverPhoneMasked", label: "脱敏手机号" },
      { key: "itemName", label: "包裹名称" },
      { key: "itemInfo", label: "包裹信息" },
      { key: "pickupMethod", label: "取货方式" },
      { key: "createdAt", label: "创建时间" },
    ],
    defaultTemplate: `【包裹已创建】
门店：{{storeName}}
收件人：{{receiverName}}
包裹：{{itemName}}
取货码：{{pickupCode}}
取货方式：{{pickupMethod}}
创建人：{{operatorName}}
创建时间：{{createdAt}}`,
  },
  PACKAGE_VERIFIED: {
    name: "包裹已核销",
    businessType: "PACKAGE",
    defaultEnabled: true,
    variables: [
      ...commonVariables,
      { key: "packageId", label: "包裹编号" },
      { key: "pickupCode", label: "取货码" },
      { key: "receiverName", label: "收件人" },
      { key: "receiverPhoneMasked", label: "脱敏手机号" },
      { key: "itemName", label: "包裹名称" },
      { key: "itemInfo", label: "包裹信息" },
      { key: "pickupMethod", label: "核销方式" },
      { key: "verifiedAt", label: "核销时间" },
    ],
    defaultTemplate: `【包裹已核销】
门店：{{storeName}}
收件人：{{receiverName}}
包裹：{{itemName}}
取货码：{{pickupCode}}
核销方式：{{pickupMethod}}
核销人：{{operatorName}}
核销时间：{{verifiedAt}}`,
  },
  PROCESSING_COMPLETED: {
    name: "加工已完成",
    businessType: "PROCESSING_PLAN",
    defaultEnabled: true,
    variables: [
      ...commonVariables,
      { key: "planId", label: "加工计划编号" },
      { key: "prescriptionNo", label: "处方号" },
      { key: "customerName", label: "患者姓名" },
      { key: "processType", label: "加工类型" },
      { key: "totalDose", label: "总剂数" },
      { key: "bagCount", label: "袋数" },
      { key: "pickupCode", label: "取货码" },
      { key: "pickupMethod", label: "取货方式" },
      { key: "notifyType", label: "提醒方式" },
      { key: "finishTime", label: "完成时间" },
    ],
    defaultTemplate: `【加工已完成】
门店：{{storeName}}
处方号：{{prescriptionNo}}
患者：{{customerName}}
加工类型：{{processType}}
加工数量：{{totalDose}} 剂
取货码：{{pickupCode}}
取货方式：{{pickupMethod}}
提醒方式：{{notifyType}}
操作人：{{operatorName}}
完成时间：{{finishTime}}`,
  },
  TRANSFER_REQUESTED: {
    name: "发起调货申请",
    businessType: "STORE_TRANSFER",
    defaultEnabled: true,
    variables: [
      ...commonVariables,
      { key: "transferId", label: "调拨编号" },
      { key: "transferNo", label: "调拨单号" },
      { key: "fromStoreName", label: "调出门店" },
      { key: "toStoreName", label: "调入门店" },
      { key: "itemCount", label: "明细数量" },
      { key: "itemSummary", label: "调拨内容" },
      { key: "transferDate", label: "调拨日期" },
      { key: "expectedReturnDate", label: "预计归还日期" },
      { key: "remark", label: "备注" },
    ],
    defaultTemplate: `【发起调货申请】
调拨单：{{transferNo}}
调出门店：{{fromStoreName}}
调入门店：{{toStoreName}}
调拨内容：{{itemSummary}}
调拨日期：{{transferDate}}
预计归还：{{expectedReturnDate}}
申请人：{{operatorName}}
备注：{{remark}}`,
  },
  TRANSFER_OUTBOUND_CONFIRMED: {
    name: "调货已确认调出",
    businessType: "STORE_TRANSFER",
    defaultEnabled: false,
    variables: [],
    defaultTemplate: `【调货已确认调出】
调拨单：{{transferNo}}
调拨方向：{{fromStoreName}} → {{toStoreName}}
确认人：{{operatorName}}
确认时间：{{eventTime}}`,
  },
  TRANSFER_RETURN_REQUESTED: {
    name: "调货已提交归还",
    businessType: "STORE_TRANSFER",
    defaultEnabled: false,
    variables: [],
    defaultTemplate: `【调货已提交归还】
调拨单：{{transferNo}}
调拨方向：{{fromStoreName}} → {{toStoreName}}
提交人：{{operatorName}}
提交时间：{{eventTime}}`,
  },
  TRANSFER_RETURN_CONFIRMED: {
    name: "调货归还已确认",
    businessType: "STORE_TRANSFER",
    defaultEnabled: false,
    variables: [],
    defaultTemplate: `【调货归还已确认】
调拨单：{{transferNo}}
调拨方向：{{fromStoreName}} → {{toStoreName}}
确认人：{{operatorName}}
确认时间：{{eventTime}}`,
  },
  TRANSFER_CANCELLED: {
    name: "调货已取消",
    businessType: "STORE_TRANSFER",
    defaultEnabled: true,
    variables: [],
    defaultTemplate: `【调货已取消】
调拨单：{{transferNo}}
调拨方向：{{fromStoreName}} → {{toStoreName}}
取消原因：{{remark}}
操作人：{{operatorName}}
取消时间：{{eventTime}}`,
  },
});

const transferVariables = [
  ...commonVariables,
  { key: "transferId", label: "调拨编号" },
  { key: "transferNo", label: "调拨单号" },
  { key: "fromStoreName", label: "调出门店" },
  { key: "toStoreName", label: "调入门店" },
  { key: "itemCount", label: "明细数量" },
  { key: "itemSummary", label: "调拨内容" },
  { key: "transferDate", label: "调拨日期" },
  { key: "expectedReturnDate", label: "预计归还日期" },
  { key: "remark", label: "备注" },
];

for (const code of [
  "TRANSFER_OUTBOUND_CONFIRMED",
  "TRANSFER_RETURN_REQUESTED",
  "TRANSFER_RETURN_CONFIRMED",
  "TRANSFER_CANCELLED",
]) {
  ROBOT_EVENT_DEFINITIONS[code].variables = transferVariables;
}

export const ROBOT_EVENT_CODES = Object.freeze(
  Object.keys(ROBOT_EVENT_DEFINITIONS),
);
