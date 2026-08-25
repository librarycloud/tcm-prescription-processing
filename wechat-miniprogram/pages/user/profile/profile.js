import { getProfile, sendEmailCode, updateProfile, verifyEmail } from '../../../api/user';
import { bindWechat, rebindWechat } from '../../../api/auth';
import { clearSession, getToken, getUser, setSession } from '../../../utils/auth';
import { onUserTabChange } from '../../../utils/user-tabbar';
import { getAppInfo } from '../../../utils/app-info';
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
    emailCountdown: 0,
    emailSending: false,
    emailVerifying: false,
    user: {},
    avatarText: '我',
    form: {
      phone: '',
      username: '',
      nickname: '',
      email: '',
      emailCode: '',
      password: '',
      confirmPassword: ''
    }
  },

  onTabChange: onUserTabChange,

  onShow() {
    const user = getUser() || {};
    this.setData({
      user,
      avatarText: user.nickname ? user.nickname.slice(0, 1) : '我',
      form: {
        ...this.data.form,
        phone: user.phone || '',
        username: user.username || '',
        nickname: user.nickname || '',
        email: user.email || ''
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
          username: user.username || '',
          nickname: user.nickname || '',
          email: user.email || ''
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
        username: this.data.user.username || '',
        nickname: this.data.user.nickname || '',
        email: this.data.user.email || '',
        emailCode: '',
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
        username: this.data.user.username || '',
        nickname: this.data.user.nickname || '',
        email: this.data.user.email || '',
        emailCode: '',
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

  async sendEmailCode() {
    const email = String(this.data.form.email || '').trim();
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      wx.showToast({ title: '请输入正确的邮箱', icon: 'none' });
      return;
    }
    this.setData({ emailSending: true });
    try {
      await sendEmailCode(email);
      wx.showToast({ title: '验证码已发送', icon: 'success' });
      this.setData({ emailCountdown: 60 });
      const timer = setInterval(() => {
        const next = this.data.emailCountdown - 1;
        this.setData({ emailCountdown: next });
        if (next <= 0) clearInterval(timer);
      }, 1000);
    } finally {
      this.setData({ emailSending: false });
    }
  },

  async verifyEmail() {
    const { email, emailCode } = this.data.form;
    if (!emailCode || emailCode.length !== 6) {
      wx.showToast({ title: '请输入6位验证码', icon: 'none' });
      return;
    }
    this.setData({ emailVerifying: true });
    try {
      const data = await verifyEmail(email, emailCode);
      setSession(data);
      this.setData({ user: data.user, 'form.emailCode': '' });
      wx.showToast({ title: '邮箱绑定成功', icon: 'success' });
    } finally {
      this.setData({ emailVerifying: false });
    }
  },

  async submit() {
    const { phone, username, nickname, email, password, confirmPassword } = this.data.form;
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
    if (String(email || '').trim().toLowerCase() !== String(this.data.user.email || '').trim().toLowerCase()) {
      wx.showToast({ title: '请先完成邮箱验证码验证', icon: 'none' });
      return;
    }

    if (username && (!/^[A-Za-z0-9]{2,64}$/.test(username) || !/[A-Za-z]/.test(username))) {
      wx.showToast({ title: '用户名需为2-64位英文和数字，且不能是纯数字', icon: 'none' });
      return;
    }
    const payload = { phone, username: username || null, nickname };
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
          username: data.user.username || '',
          nickname: data.user.nickname || '',
          email: data.user.email || '',
          emailCode: '',
          password: '',
          confirmPassword: ''
        }
      });
      wx.showToast({ title: '保存成功', icon: 'success' });
    } finally {
      this.setData({ saving: false });
    }
  },

  showAbout() {
    const { version, uploadDate } = getAppInfo();
    wx.showModal({
      title: '关于',
      content: `版本号：${version}\n上传日期：${uploadDate}`,
      showCancel: false,
      confirmText: '知道了'
    });
  },

  logout() {
    clearSession();
    wx.reLaunch({ url: '/pages/login/login' });
  }
});
