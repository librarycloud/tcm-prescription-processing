import { getProfile, updateProfile } from '../../../api/user';
import { bindWechat, rebindWechat } from '../../../api/auth';
import { clearSession, getToken, getUser, setSession } from '../../../utils/auth';
import { onAdminTabChange } from '../../../utils/admin-tabbar';
import { getWechatLoginCode } from '../../../utils/wechat';

Page({
  data: {
    activeTab: 'profile',
    loading: false,
    saving: false,
    editing: false,
    passwordVisible: false,
    wechatBindingVisible: false,
    wechatBindingLoading: false,
    wechatPassword: '',
    wechatPasswordVisible: false,
    user: {},
    avatarText: '我',
    form: {
      phone: '',
      nickname: '',
      password: '',
      confirmPassword: ''
    }
  },

  onTabChange: onAdminTabChange,

  onShow() {
    const user = getUser() || {};
    this.setData({
      user,
      avatarText: user.nickname ? user.nickname.slice(0, 1) : '我',
      form: {
        ...this.data.form,
        phone: user.phone || '',
        nickname: user.nickname || ''
      }
    });
    this.load();
  },

  async load() {
    this.setData({ loading: true });
    try {
      const user = await getProfile();
      this.setData({
        user,
        avatarText: user.nickname ? user.nickname.slice(0, 1) : '我',
        form: {
          ...this.data.form,
          phone: user.phone || '',
          nickname: user.nickname || ''
        }
      });
      wx.setStorageSync('user', user);
    } finally {
      this.setData({ loading: false });
    }
  },

  onChange(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({ [`form.${field}`]: e.detail.value });
  },

  startEdit() {
    this.setData({
      editing: true,
      passwordVisible: false,
      form: {
        phone: this.data.user.phone || '',
        nickname: this.data.user.nickname || '',
        password: '',
        confirmPassword: ''
      }
    });
  },

  cancelEdit() {
    this.setData({
      editing: false,
      passwordVisible: false,
      form: {
        phone: this.data.user.phone || '',
        nickname: this.data.user.nickname || '',
        password: '',
        confirmPassword: ''
      }
    });
  },

  togglePasswordVisible() {
    this.setData({ passwordVisible: !this.data.passwordVisible });
  },

  openWechatBinding() {
    if (this.data.wechatBindingLoading) return;
    if (!this.data.user.openidBound) {
      this.submitWechatBinding();
      return;
    }
    this.setData({
      wechatBindingVisible: true,
      wechatPassword: '',
      wechatPasswordVisible: false
    });
  },

  closeWechatBinding() {
    this.setData({
      wechatBindingVisible: false,
      wechatPassword: '',
      wechatPasswordVisible: false
    });
  },

  onWechatPasswordChange(e) {
    this.setData({ wechatPassword: e.detail.value });
  },

  toggleWechatPasswordVisible() {
    this.setData({ wechatPasswordVisible: !this.data.wechatPasswordVisible });
  },

  async submitWechatBinding() {
    const modifying = Boolean(this.data.user.openidBound);
    if (modifying && !this.data.wechatPassword) {
      wx.showToast({ title: '请输入当前密码', icon: 'none' });
      return;
    }
    this.setData({ wechatBindingLoading: true });
    try {
      const code = await getWechatLoginCode();
      const data = modifying
        ? await rebindWechat({ code, password: this.data.wechatPassword })
        : await bindWechat(code);
      setSession({ token: getToken(), user: data.user });
      this.setData({
        user: data.user,
        wechatBindingVisible: false,
        wechatPassword: '',
        wechatPasswordVisible: false
      });
      wx.showToast({ title: modifying ? '绑定已修改' : '绑定成功', icon: 'success' });
    } finally {
      this.setData({ wechatBindingLoading: false });
    }
  },

  async submit() {
    const { phone, nickname, password, confirmPassword } = this.data.form;
    if (!phone) {
      wx.showToast({ title: '请输入手机号', icon: 'none' });
      return;
    }
    if (!password && confirmPassword) {
      wx.showToast({ title: '请先输入新密码', icon: 'none' });
      return;
    }
    if (password && password.length < 6) {
      wx.showToast({ title: '密码至少 6 位', icon: 'none' });
      return;
    }
    if (password && password !== confirmPassword) {
      wx.showToast({ title: '两次密码不一致', icon: 'none' });
      return;
    }

    const payload = { phone, nickname };
    if (password) payload.password = password;

    this.setData({ saving: true });
    try {
      const data = await updateProfile(payload);
      setSession(data);
      this.setData({
        editing: false,
        passwordVisible: false,
        user: data.user,
        avatarText: data.user.nickname ? data.user.nickname.slice(0, 1) : '我',
        form: {
          phone: data.user.phone || '',
          nickname: data.user.nickname || '',
          password: '',
          confirmPassword: ''
        }
      });
      wx.showToast({ title: '保存成功', icon: 'success' });
    } finally {
      this.setData({ saving: false });
    }
  },

  logout() {
    clearSession();
    wx.reLaunch({ url: '/pages/login/login' });
  }
});
