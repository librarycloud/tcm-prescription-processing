export const EMAIL_SCENES = {
  VERIFICATION: 'verification',
  PICKUP: [0, 1, 2].map((method) => `pickup_${method}`)
};

export const EMAIL_SCENE_NAMES = {
  verification: '邮箱验证码',
  pickup_0: '自提通知',
  pickup_1: '跑腿通知',
  pickup_2: '快递通知'
};

export const EMAIL_TEMPLATE_VARIABLES = [
  'code',
  'expiresMinutes',
  'receiverName',
  'pickupCode',
  'itemName',
  'itemInfo',
  'pickupMethod',
  'createdAt'
];
