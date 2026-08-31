import drawQrcode from 'weapp-qrcode';
import { normalizePickupCode } from '../../utils/format';

let previousBrightness = -1;

Component({
  properties: {
    text: {
      type: String,
      value: ''
    },
    size: {
      type: Number,
      value: 220
    },
    enablePreview: {
      type: Boolean,
      value: true
    }
  },

  data: {
    canvasId: `qr${Date.now()}${Math.floor(Math.random() * 1000)}`,
    previewCanvasId: `qr_big_${Date.now()}${Math.floor(Math.random() * 1000)}`,
    ready: false,
    previewing: false,
    bigSize: 280
  },

  observers: {
    text() {
      this.draw();
      if (this.data.previewing) {
        this.drawBig();
      }
    }
  },

  lifetimes: {
    ready() {
      this.setData({ ready: true });
      this.draw();
    },
    detached() {
      this.restoreBrightness();
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
            text: this.data.text.startsWith('TCM:PICKUP:1:')
              ? this.data.text
              : (normalizePickupCode(this.data.text) || this.data.text),
            _this: this
          });
        } catch (error) {
          console.error('二维码生成失败', error);
        }
      }, 50);
    },

    drawBig() {
      setTimeout(() => {
        try {
          drawQrcode({
            width: this.data.bigSize,
            height: this.data.bigSize,
            canvasId: this.data.previewCanvasId,
            text: this.data.text.startsWith('TCM:PICKUP:1:')
              ? this.data.text
              : (normalizePickupCode(this.data.text) || this.data.text),
            _this: this
          });
        } catch (error) {
          console.error('大号二维码生成失败', error);
        }
      }, 60);
    },

    onTapQr() {
      if (!this.data.enablePreview || !this.data.text) return;
      this.setData({ previewing: true });
      this.drawBig();

      try {
        wx.getScreenBrightness({
          success: (res) => {
            previousBrightness = res.value;
            wx.setScreenBrightness({ value: 0.95 });
          }
        });
        wx.setKeepScreenOn({ keepScreenOn: true });
      } catch (e) {
        // Brightness api is optional
      }
    },

    closePreview() {
      this.setData({ previewing: false });
      this.restoreBrightness();
    },

    restoreBrightness() {
      try {
        if (previousBrightness >= 0) {
          wx.setScreenBrightness({ value: previousBrightness });
          previousBrightness = -1;
        }
        wx.setKeepScreenOn({ keepScreenOn: false });
      } catch (e) {
        // ignore
      }
    }
  }
});
