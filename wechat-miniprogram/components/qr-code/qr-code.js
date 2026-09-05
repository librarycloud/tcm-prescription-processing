import { drawQrcode2d } from '../../utils/qrcode-2d';
import { normalizePickupCode } from '../../utils/format';

let previousBrightness = -1;
const DEFAULT_CORRECT_LEVEL = 0; // QR M level: balanced density and error correction.

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
      this.setData({ ready: true }, () => {
        this.draw();
      });
    },
    detached() {
      this.restoreBrightness();
    }
  },

  pageLifetimes: {
    hide() {
      if (this.data.previewing) this.setData({ previewing: false });
      this.restoreBrightness();
    }
  },

  methods: {
    draw(retry = 0) {
      if (!this.data.ready || !this.data.text) return;
      this.createSelectorQuery()
        .select('#qrCanvas')
        .fields({ node: true, size: true })
        .exec((res) => {
          const canvas = res?.[0]?.node;
          if (!canvas) {
            if (retry < 5) {
              setTimeout(() => this.draw(retry + 1), 50);
            }
            return;
          }
          const content = this.data.text.startsWith('TCM:PICKUP:1:')
            ? this.data.text
            : (normalizePickupCode(this.data.text) || this.data.text);
          try {
            drawQrcode2d(canvas, {
              width: this.data.size,
              height: this.data.size,
              correctLevel: DEFAULT_CORRECT_LEVEL,
              text: content
            });
          } catch (error) {
            console.error('Canvas 2D 二维码生成失败', error);
          }
        });
    },

    drawBig(retry = 0) {
      setTimeout(() => {
        this.createSelectorQuery()
          .select('#qrBigCanvas')
          .fields({ node: true, size: true })
          .exec((res) => {
            const canvas = res?.[0]?.node;
            if (!canvas) {
              if (retry < 5) {
                setTimeout(() => this.drawBig(retry + 1), 60);
              }
              return;
            }
            const content = this.data.text.startsWith('TCM:PICKUP:1:')
              ? this.data.text
              : (normalizePickupCode(this.data.text) || this.data.text);
            try {
              drawQrcode2d(canvas, {
                width: this.data.bigSize,
                height: this.data.bigSize,
                correctLevel: DEFAULT_CORRECT_LEVEL,
                text: content
              });
            } catch (error) {
              console.error('大号 Canvas 2D 二维码生成失败', error);
            }
          });
      }, 50);
    },

    onTapQr() {
      if (!this.data.enablePreview || !this.data.text) return;
      this.setData({ previewing: true }, () => {
        this.drawBig();
      });

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
