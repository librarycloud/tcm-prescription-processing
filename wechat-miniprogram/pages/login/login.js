import { login, wechatLogin, bindWechat } from '../../api/auth';
import { getToken, getUser, redirectByRole, setSession } from '../../utils/auth';
import { getWechatLoginCode } from '../../utils/wechat';

Page({
  data: {
    adminIdentifier: '',
    adminPassword: '',
    agreeProtocol: false,
    loading: false,
    loadingWechat: false,
    pendingBind: false
  },

  onLoad() {
    const token = getToken();
    const user = getUser();
    if (token && user) {
      redirectByRole(user);
    }
  },

  onAdminPhoneChange(e) {
    this.setData({ adminIdentifier: e.detail.value });
  },

  onAdminPasswordChange(e) {
    this.setData({ adminPassword: e.detail.value });
  },

  onProtocolChange(e) {
    const detail = e.detail || {};
    const checked = detail.checked !== undefined ? detail.checked : detail;
    this.setData({ agreeProtocol: Boolean(checked) });
  },

  selectProtocol() {
    this.setData({ agreeProtocol: !this.data.agreeProtocol });
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
    this.setData({ loadingWechat: true });
    try {
      const code = await getWechatLoginCode();
      const data = await wechatLogin(code);
      
      if (data.requiresBind) {
        this.setData({ pendingBind: true });
        wx.showModal({
          title: '绑定微信',
          content: '首次使用微信登录，请先在上方输入账号密码完成登录，系统将自动为您绑定微信。',
          showCancel: false,
          confirmText: '我知道了'
        });
        return;
      }
      
      setSession(data);
      redirectByRole(data.user);
    } catch (e) {
      wx.showToast({ title: e.message || '微信登录失败', icon: 'none' });
    } finally {
      this.setData({ loadingWechat: false });
    }
  },

  async submitAdmin() {
    if (!this.ensureProtocol()) return;
    if (!this.data.adminIdentifier || !this.data.adminPassword) {
      wx.showToast({ title: '请输入账号和密码', icon: 'none' });
      return;
    }

    this.setData({ loading: true });
    try {
      const account = await login({
        identifier: this.data.adminIdentifier,
        password: this.data.adminPassword
      });
      setSession(account);

      if (this.data.pendingBind) {
        try {
          const code = await getWechatLoginCode();
          const bindData = await bindWechat(code);
          setSession({ token: account.token, user: bindData.user });
          wx.showToast({ title: '微信绑定成功', icon: 'success' });
        } catch (e) {
          console.warn('Auto bind wechat failed:', e);
        }
      }

      setTimeout(() => redirectByRole(account.user), 500);
    } catch (e) {
      wx.showToast({ title: e.message || '登录失败', icon: 'none' });
    } finally {
      this.setData({ loading: false });
    }
  }
});
