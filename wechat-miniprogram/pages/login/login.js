import { bindWechat, bindWechatByPickupCode, login, userLogin, wechatLogin } from '../../api/auth';
import { getToken, getUser, redirectByRole, setSession } from '../../utils/auth';
import { getWechatLoginCode } from '../../utils/wechat';
import { formatPickupCode, normalizePickupCode } from '../../utils/format';

Page({
  data: {
    mode: 'wechat',
    requiresBind: false,
    bindToken: '',
    bindMethod: 'password',
    identifier: '',
    phone: '',
    password: '',
    pickupCode: '',
    passwordVisible: false,
    adminIdentifier: '',
    adminPassword: '',
    agreeProtocol: true,
    loading: false
  },

  onLoad() {
    const token = getToken();
    const user = getUser();
    if (token && user) {
      redirectByRole(user);
    }
  },

  switchWechat() {
    this.setData({ mode: 'wechat' });
  },

  switchAdmin() {
    this.setData({ mode: 'admin' });
  },

  onPhoneChange(e) {
    const field = this.data.bindMethod === 'pickup' ? 'phone' : 'identifier';
    this.setData({ [field]: e.detail.value });
  },

  onPasswordChange(e) {
    this.setData({ password: e.detail.value });
  },

  onPickupCodeChange(e) {
    this.setData({ pickupCode: formatPickupCode(e.detail.value) });
  },

  switchBindMethod(e) {
    this.setData({ bindMethod: e.currentTarget.dataset.method });
  },

  togglePasswordVisible() {
    this.setData({ passwordVisible: !this.data.passwordVisible });
  },

  onAdminPhoneChange(e) {
    this.setData({ adminIdentifier: e.detail.value });
  },

  onAdminPasswordChange(e) {
    this.setData({ adminPassword: e.detail.value });
  },

  onProtocolChange(e) {
    const detail = e.detail || {};
    const checked = typeof detail === 'object' ? detail.checked || detail.value === 'agree' : detail;
    this.setData({ agreeProtocol: Boolean(checked) });
  },

  selectProtocol() {
    this.setData({ agreeProtocol: true });
  },

  goService() {
    wx.navigateTo({ url: '/pages/agreement/service/service' });
  },

  goPrivacy() {
    wx.navigateTo({ url: '/pages/agreement/privacy/privacy' });
  },

  ensureProtocol() {
    if (this.data.agreeProtocol) return true;
    wx.showToast({ title: '请先阅读并同意协议', icon: 'none' });
    return false;
  },

  async submitWechat() {
    if (!this.ensureProtocol()) return;
    this.setData({ loading: true });
    try {
      const code = await getWechatLoginCode();
      const data = await wechatLogin(code);
      if (data.requiresBind) {
        this.setData({ requiresBind: true, bindToken: data.bindToken, password: '', pickupCode: '', phone: '', identifier: '' });
        wx.showToast({ title: '请选择绑定方式', icon: 'none' });
        return;
      }
      setSession(data);
      redirectByRole(data.user);
    } finally {
      this.setData({ loading: false });
    }
  },

  async submitBind() {
    if (!this.ensureProtocol()) return;
    if (this.data.bindMethod === 'pickup' && !this.data.phone) {
      wx.showToast({ title: '请输入手机号', icon: 'none' });
      return;
    }

    this.setData({ loading: true });
    try {
      if (this.data.bindMethod === 'pickup') {
        const pickupCode = normalizePickupCode(this.data.pickupCode);
        if (!/^\d{6}$/.test(pickupCode)) {
          wx.showToast({ title: '请输入6位数字取货码', icon: 'none' });
          return;
        }
        const data = await bindWechatByPickupCode({
          bindToken: this.data.bindToken,
          phone: this.data.phone,
          pickupCode
        });
        setSession(data);
        redirectByRole(data.user);
        return;
      }
      if (!this.data.password) {
        wx.showToast({ title: '请输入密码', icon: 'none' });
        return;
      }
      const account = await userLogin({
        identifier: this.data.identifier,
        password: this.data.password
      });
      setSession(account);
      const code = await getWechatLoginCode();
      const data = await bindWechat(code);
      const session = { token: account.token, user: data.user };
      setSession(session);
      redirectByRole(session.user);
    } finally {
      this.setData({ loading: false, passwordVisible: false });
    }
  },

  async submitAdmin() {
    if (!this.ensureProtocol()) return;
    if (!this.data.adminIdentifier || !this.data.adminPassword) {
      wx.showToast({ title: '请输入管理员账号和密码', icon: 'none' });
      return;
    }

    this.setData({ loading: true });
    try {
      const data = await login({
        identifier: this.data.adminIdentifier,
        password: this.data.adminPassword
      });
      setSession(data);
      redirectByRole(data.user);
    } finally {
      this.setData({ loading: false });
    }
  }
});
