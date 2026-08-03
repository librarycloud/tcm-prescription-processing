import drawQrcode from 'weapp-qrcode';
import { normalizePickupCode } from '../../utils/format';

Component({
  properties: {
    text: {
      type: String,
      value: ''
    },
    size: {
      type: Number,
      value: 220
    }
  },

  data: {
    canvasId: `qr${Date.now()}${Math.floor(Math.random() * 1000)}`,
    ready: false
  },

  observers: {
    text() {
      this.draw();
    }
  },

  lifetimes: {
    ready() {
      this.setData({ ready: true });
      this.draw();
    }
  },

  methods: {
    draw() {
      if (!this.data.ready || !this.data.text) return;
      setTimeout(() => {
        try {
          drawQrcode({
            width: this.data.size,
            height: this.data.size,
            canvasId: this.data.canvasId,
            text: normalizePickupCode(this.data.text) || this.data.text,
            _this: this
          });
        } catch (error) {
          console.error('二维码生成失败', error);
        }
      }, 50);
    }
  }
});
