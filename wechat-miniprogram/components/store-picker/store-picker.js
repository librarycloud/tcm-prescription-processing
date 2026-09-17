import { getStores } from '../../api/admin';
import { getUser } from '../../utils/auth';

Component({
  externalClasses: ['t-class'],
  properties: {
    value: {
      type: null,
      value: '',
      observer: '_onValueChange'
    },
    includeAll: {
      type: Boolean,
      value: true
    },
    allLabel: {
      type: String,
      value: '全部门店'
    },
    placeholder: {
      type: String,
      value: '请选择门店'
    },
    prefix: {
      type: String,
      value: ''
    },
    variant: {
      type: String,
      value: 'button' // 'button' | 'filter' | 'row'
    },
    size: {
      type: String,
      value: 'small'
    },
    themeVariant: {
      type: String,
      value: 'outline'
    },
    block: {
      type: Boolean,
      value: false
    },
    buttonClass: {
      type: String,
      value: ''
    },
    label: {
      type: String,
      value: '门店'
    },
    disabled: {
      type: Boolean,
      value: false
    },
    onlyAdmin: {
      type: Boolean,
      value: true
    },
    autoSelectFirst: {
      type: Boolean,
      value: false
    }
  },

  data: {
    stores: [],
    storeIndex: 0,
    storeName: '',
    visible: false
  },

  lifetimes: {
    attached() {
      this.init();
    }
  },

  pageLifetimes: {
    show() {
      const user = getUser();
      const isSuperAdmin = Number(user?.role) === 0;
      const visible = this.data.onlyAdmin ? isSuperAdmin : true;
      if (visible !== this.data.visible) {
        this.setData({ visible });
        if (visible && !this.data.stores.length) {
          this.fetchStores();
        }
      }
    }
  },

  methods: {
    init() {
      const user = getUser();
      const isSuperAdmin = Number(user?.role) === 0;
      const visible = this.data.onlyAdmin ? isSuperAdmin : true;
      this.setData({ visible });
      if (visible) {
        this.fetchStores();
      }
    },

    async fetchStores() {
      try {
        const res = await getStores({ page: 1, pageSize: 100 });
        const rawList = res.list || [];
        let stores = [];
        if (this.data.includeAll) {
          stores = [{ id: '', name: this.data.allLabel }, ...rawList];
        } else {
          stores = [...rawList];
        }

        let storeIndex = 0;
        let storeName = this.data.placeholder;
        const currentValue = this.data.value;

        if (currentValue !== '' && currentValue !== null && currentValue !== undefined) {
          const idx = stores.findIndex((s) => String(s.id) === String(currentValue));
          if (idx >= 0) {
            storeIndex = idx;
            storeName = stores[idx].name;
          }
        } else if (this.data.includeAll) {
          storeIndex = 0;
          storeName = stores[0]?.name || this.data.allLabel;
        } else if (this.data.autoSelectFirst && stores.length > 0) {
          storeIndex = 0;
          storeName = stores[0].name;
          this.triggerEvent('change', {
            storeId: stores[0].id,
            store: stores[0],
            storeIndex: 0,
            value: stores[0].id
          });
        }

        this.setData({ stores, storeIndex, storeName });
        this.triggerEvent('loaded', { stores });
      } catch (e) {
        console.error('[store-picker] fetchStores failed:', e);
      }
    },

    _onValueChange(newVal) {
      const { stores } = this.data;
      if (!stores || !stores.length) return;
      const idx = stores.findIndex((s) => String(s.id) === String(newVal));
      if (idx >= 0) {
        this.setData({
          storeIndex: idx,
          storeName: stores[idx].name
        });
      } else if (newVal === '' && this.data.includeAll && stores[0]) {
        this.setData({
          storeIndex: 0,
          storeName: stores[0].name
        });
      }
    },

    onPickerChange(e) {
      const storeIndex = Number(e.detail.value || 0);
      const store = this.data.stores[storeIndex] || null;
      const storeId = store ? store.id : '';
      const storeName = store ? store.name : '';
      this.setData({ storeIndex, storeName });
      this.triggerEvent('change', {
        storeId,
        store,
        storeIndex,
        value: storeId
      });
    }
  }
});
