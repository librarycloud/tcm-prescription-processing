export const SMS_PROVIDERS = ['tencent', 'aliyun', 'volcengine'];

export const PROVIDER_NAMES = {
  tencent: '腾讯云',
  aliyun: '阿里云',
  volcengine: '火山引擎'
};

export const PICKUP_METHOD_NAMES = {
  0: '自提',
  1: '跑腿',
  2: '快递'
};

export const NOTIFICATION_STATUS = {
  UNSENT: 0,
  SUCCESS: 1,
  FAILED: 2,
  SENDING: 3
};

export const TEMPLATE_SOURCES = [
  'receiverName',
  'receiverPhone',
  'pickupCode',
  'itemName',
  'itemInfo',
  'pickupMethod',
  'storeName',
  'storeAddress',
  'storePhone',
  'expressTrackingNo',
  'expressAddress',
  'createdAt'
];
