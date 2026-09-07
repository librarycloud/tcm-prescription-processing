import { drawQrcode2d } from '../../utils/qrcode-2d';
import { normalizePickupCode } from '../../utils/format';

let previousBrightness = -1;
const DEFAULT_CORRECT_LEVEL = 0; // QR M level: balanced density and error correction.
const DRAW_SIZE = 320; // Generate high-resolution 320px * DPR QR image

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
    bigSize: 280,
    qrImageUrl: ''
  },

  observers: {
    text() {
      this.generateQr();
    }
  },

  lifetimes: {
    ready() {
      this.setData({ ready: true }, () => {
        this.generateQr();
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
    noop() {},

    getContent() {
      const text = this.data.text || '';
      if (!text) return '';
      return text.startsWith('TCM:PICKUP:1:')
        ? text
        : (normalizePickupCode(text) || text);
    },

    generateQr() {
      const content = this.getContent();
      if (!content) {
        if (this.data.qrImageUrl) {
          this.setData({ qrImageUrl: '' });
        }
        return;
      }

      // 1. Primary Engine: Synchronous OffscreenCanvas (fast, 100% in-memory, eliminates native canvas lag)
      if (typeof wx !== 'undefined' && typeof wx.createOffscreenCanvas === 'function') {
        try {
          const info = (wx.getWindowInfo && wx.getWindowInfo()) || (wx.getSystemInfoSync && wx.getSystemInfoSync()) || {};
          const dpr = Math.max(1, Math.min(info.pixelRatio || 2, 3));
          const offscreen = wx.createOffscreenCanvas({ type: '2d', width: DRAW_SIZE * dpr, height: DRAW_SIZE * dpr });
          drawQrcode2d(offscreen, {
            width: DRAW_SIZE,
            height: DRAW_SIZE,
            correctLevel: DEFAULT_CORRECT_LEVEL,
            text: content
          });
          if (typeof offscreen.toDataURL === 'function') {
            const dataUrl = offscreen.toDataURL();
            if (dataUrl && dataUrl.startsWith('data:image')) {
              this.setData({ qrImageUrl: dataUrl });
              return;
            }
          }
        } catch (err) {
          console.warn('OffscreenCanvas 离屏渲染失败，启动 DOM Canvas 降级方案', err);
        }
      }

      // 2. Fallback Engine: Offscreen DOM Canvas
      this.drawFallback(content);
    },

    drawFallback(content, retry = 0) {
      if (!this.data.ready) return;
      this.createSelectorQuery()
        .select('#qrFallbackCanvas')
        .fields({ node: true, size: true })
        .exec((res) => {
          const canvas = res?.[0]?.node;
          if (!canvas) {
            if (retry < 5) {
              setTimeout(() => this.drawFallback(content, retry + 1), 60);
            }
            return;
          }
          try {
            drawQrcode2d(canvas, {
              width: DRAW_SIZE,
              height: DRAW_SIZE,
              correctLevel: DEFAULT_CORRECT_LEVEL,
              text: content
            });

            if (typeof canvas.toDataURL === 'function') {
              const dataUrl = canvas.toDataURL();
              if (dataUrl && dataUrl.startsWith('data:image')) {
                this.setData({ qrImageUrl: dataUrl });
                return;
              }
            }

            setTimeout(() => {
              wx.canvasToTempFilePath({
                canvas,
                success: (tempRes) => {
                  this.setData({ qrImageUrl: tempRes.tempFilePath });
                },
                fail: (err) => {
                  console.error('Canvas 2D 导出临时文件失败', err);
                }
              }, this);
            }, 50);
          } catch (error) {
            console.error('降级 Canvas 2D 渲染失败', error);
          }
        });
    },

    onTapQr() {
      if (!this.data.enablePreview || !this.data.text) return;
      this.setData({ previewing: true });

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

