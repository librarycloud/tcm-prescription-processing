import {
  getHerbLocationStores,
  getHerbLocations,
  removeHerbLocationAssignment,
  saveHerbLocationAssignment,
  updateHerb,
  updateHerbLocationAssignment
} from '../../../api/admin';
import { onAdminTabChange } from '../../../utils/admin-tabbar';
import { getUser } from '../../../utils/auth';

let pinyinConverter = null;
try {
  // The dependency is optional at runtime so the page still opens before WeChat builds npm.
  const pinyinModule = require('pinyin-pro');
  pinyinConverter = pinyinModule.pinyin || pinyinModule.default?.pinyin || null;
} catch (error) {
  pinyinConverter = null;
}

const COMMON_HERB_INITIALS = Object.freeze({
  黄: 'h', 芪: 'q', 白: 'b', 芍: 's', 熟: 's', 地: 'd', 炙: 'z', 甘: 'g', 草: 'c',
  当: 'd', 归: 'g', 川: 'c', 芎: 'x', 枸: 'g', 杞: 'q', 菊: 'j', 花: 'h', 陈: 'c', 皮: 'p',
  半: 'b', 夏: 'x', 茯: 'f', 苓: 'l', 党: 'd', 参: 's', 人: 'r', 术: 'z', 山: 's', 药: 'y',
  山楂: 'sz', 麦: 'm', 冬: 'd', 五: 'w', 味: 'w', 子: 'z', 金: 'j', 银: 'y', 花: 'h',
  连: 'l', 翘: 'q', 薏: 'y', 米: 'm', 莲: 'l', 藕: 'o', 桔: 'j', 梗: 'g', 柴: 'c', 胡: 'h',
  芥: 'j', 穗: 's', 荆: 'j', 芥: 'j', 防: 'f', 风: 'f', 羌: 'q', 活: 'h', 独: 'd',
  牛: 'n', 蒡: 'b', 蛇: 's', 床: 'c', 子: 'z', 酸: 's', 枣: 'z', 仁: 'r', 远: 'y', 志: 'z',
  石: 's', 菖: 'c', 蒲: 'p', 郁: 'y', 金: 'j', 钱: 'q', 薄: 'b', 荷: 'h', 紫: 'z', 苏: 's',
  葛: 'g', 根: 'g', 升: 's', 麻: 'm', 黄: 'h', 芩: 'q', 栀: 'z', 橘: 'j', 红: 'h',
  花: 'h', 丹: 'd', 参: 's', 麦: 'm', 门: 'm', 参: 's', 知: 'z', 母: 'm', 贝: 'b',
  桑: 's', 叶: 'y', 菟: 't', 丝: 's', 淫: 'y', 羊: 'y', 藿: 'h', 肉: 'r', 桂: 'g', 附: 'f',
  子: 'z', 乌: 'w', 姜: 'j', 枳: 'z', 实: 's', 厚: 'h', 朴: 'p', 枳: 'z', 壳: 'k',
  大: 'd', 黄: 'h', 芒: 'm', 硝: 'x', 牡: 'm', 丹: 'd', 皮: 'p', 赤: 'c', 芍: 's', 桃: 't',
  仁: 'r', 红: 'h', 车: 'c', 前: 'q', 子: 'z', 泽: 'z', 泻: 'x', 木: 'm', 通: 't',
  草: 'c', 薏: 'y', 苡: 'y', 仁: 'r', 砂: 's', 仁: 'r', 蔻: 'k', 白: 'b', 豆: 'd', 蔻: 'k',
  藕: 'o', 节: 'j', 桑: 's', 寄: 'j', 生: 's', 杜: 'd', 仲: 'z', 续: 'x', 断: 'd', 牛: 'n',
  膝: 'x', 鸡: 'j', 血: 'x', 藤: 't', 鸡: 'j', 内: 'n', 金: 'j', 石: 's', 斛: 'h',
  麦: 'm', 芽: 'y', 神: 's', 曲: 'q', 山: 's', 楂: 'z', 莱: 'l', 菔: 'f'
});

const TYPES = [
  { label: '全部位置', value: '' },
  { label: '斗 D', value: 'D' },
  { label: '柜 G', value: 'G' },
  { label: '冰箱 F', value: 'F' },
  { label: '仓库 C', value: 'C' }
];
const POSITION_TYPES = TYPES.filter((item) => item.value);

function typeText(type) { return TYPES.find((item) => item.value === type)?.label || type || '-'; }
function locationText(code) { return String(code || '').replaceAll('-', ''); }

function positionText(location = {}) {
  const compactCode = locationText(location.code).toUpperCase();
  const type = String(location.type || compactCode.charAt(0)).toUpperCase();
  const digits = compactCode.slice(1).split('');
  const unitNo = location.unitNo ?? digits[0] ?? '-';
  const layerNo = location.layerNo ?? digits[1] ?? '-';

  if (type === 'D') {
    const columnNo = location.columnNo ?? digits[2] ?? '-';
    const layerText = Number(layerNo) === 0 ? '顶层' : `${layerNo}行`;
    return `斗${unitNo}-${layerText}-${columnNo}列`;
  }

  const typeName = { G: '柜', F: '冰箱', C: '仓库' }[type] || type || '位置';
  return `${typeName}${unitNo}-${layerNo}层`;
}

function herbInitials(value) {
  const text = String(value || '').trim();
  const pinyinInitials = pinyinConverter
    ? pinyinConverter(text, { pattern: 'first', toneType: 'none', type: 'array' })
    : [];
  return pinyinInitials.length
    ? pinyinInitials.join('')
    : Array.from(text).map((char) => COMMON_HERB_INITIALS[char] || '').join('');
}

function herbSearchText(herb) {
  const name = String(herb?.name || '').trim();
  const initials = herbInitials(name);
  return [name, initials, herb?.code]
    .filter(Boolean)
    .join(' ')
    .toLowerCase();
}

function highlightSegments(value, keyword, { matchPinyin = false } = {}) {
  const text = String(value ?? '');
  const search = String(keyword || '').trim();
  if (!search) return [{ text, highlighted: false }];

  const lowerText = text.toLowerCase();
  const lowerSearch = search.toLowerCase();
  const segments = [];
  let cursor = 0;
  let matchIndex = lowerText.indexOf(lowerSearch);
  while (matchIndex !== -1) {
    if (matchIndex > cursor) segments.push({ text: text.slice(cursor, matchIndex), highlighted: false });
    segments.push({
      text: text.slice(matchIndex, matchIndex + search.length),
      highlighted: true
    });
    cursor = matchIndex + search.length;
    matchIndex = lowerText.indexOf(lowerSearch, cursor);
  }
  if (segments.length) {
    if (cursor < text.length) segments.push({ text: text.slice(cursor), highlighted: false });
    return segments;
  }

  const isPinyinMatch = matchPinyin
    && /^[a-z]+$/i.test(search)
    && /[\u3400-\u9fff]/.test(text)
    && herbInitials(text).toLowerCase().includes(lowerSearch);
  return [{ text, highlighted: isPinyinMatch }];
}

function herbSummarySegments(herbs, keyword) {
  if (!herbs.length) return highlightSegments('未配置', keyword);
  return herbs.reduce((segments, herb, index) => {
    if (index) segments.push({ text: ' / ', highlighted: false });
    segments.push(...highlightSegments(herb.name, keyword, { matchPinyin: true }));
    return segments;
  }, []);
}

function decorateLocation(location, keyword) {
  const herbs = location.herbs.map((herb) => ({
    ...herb,
    nameSegments: highlightSegments(herb.name, keyword, { matchPinyin: true }),
    descriptionSegments: highlightSegments(herb.description, keyword)
  }));
  return {
    ...location,
    herbs,
    displayCodeSegments: highlightSegments(location.displayCode, keyword),
    positionLabelSegments: highlightSegments(location.positionLabel, keyword),
    herbSummarySegments: herbSummarySegments(herbs, keyword)
  };
}

Page({
  data: {
    activeTab: 'herbs',
    isSuperAdmin: false,
    stores: [],
    storeIndex: 0,
    storeId: '',
    storeName: '',
    types: TYPES,
    positionTypes: POSITION_TYPES,
    typeIndex: 0,
    type: '',
    keyword: '',
    locations: [],
    filteredLocations: [],
    herbs: [],
    loading: false,
    saving: false,
    detailVisible: false,
    assignmentVisible: false,
    herbEditVisible: false,
    positionEditVisible: false,
    selectedLocation: null,
    assignmentForm: { type: 'D', unitNo: '', layerNo: '', columnNo: '', slotNo: '', herbId: '', name: '', code: '', specification: '' },
    assignmentTypeIndex: 0,
    herbEditForm: { id: '', name: '', code: '', specification: '' },
    positionEditForm: { assignmentId: '', name: '', type: 'D', unitNo: '', layerNo: '', columnNo: '', slotNo: '' },
    positionTypeIndex: 0
  },

  onTabChange: onAdminTabChange,

  async onShow() {
    const user = getUser();
    const isSuperAdmin = Number(user.role) === 0;
    this.setData({ isSuperAdmin });
    try {
      const stores = await getHerbLocationStores();
      const selectedStoreId = isSuperAdmin ? stores?.[0]?.id : user.storeId;
      const storeIndex = Math.max(0, (stores || []).findIndex((item) => Number(item.id) === Number(selectedStoreId)));
      this.setData({ stores: stores || [], storeIndex, storeId: selectedStoreId || '', storeName: stores?.[storeIndex]?.name || '' });
      await this.load();
    } catch (error) { console.error('load herb locations failed', error); }
  },

  async load() {
    if (!this.data.storeId) return;
    this.setData({ loading: true });
    try {
      const data = await getHerbLocations(this.data.isSuperAdmin ? this.data.storeId : undefined);
      const locations = (data?.locations || []).map((location) => {
        const displayCode = locationText(location.code);
        const positionLabel = positionText(location);
        const herbs = (location.herbs || []).map((herb) => ({
          ...herb,
          description: `${herb.code || '-'}${herb.specification ? ` · ${herb.specification}` : ''} · 格内${herb.slotNo || '-'}`,
          searchText: herbSearchText(herb)
        }));
        return {
          ...location,
          displayCode,
          positionLabel,
          typeLabel: typeText(location.type),
          herbs,
          herbSummary: herbs.map((herb) => herb.name).join(' / ') || '未配置',
          searchText: [location.code, displayCode, positionLabel, ...herbs.map((herb) => herb.searchText)]
            .filter(Boolean)
            .join(' ')
            .toLowerCase()
        };
      });
      const herbs = (data?.herbs || []).map((herb) => ({
        ...herb,
        label: [herb.code, herb.name, herb.specification].filter(Boolean).join(' · '),
        searchText: herbSearchText(herb)
      }));
      this.setData({ locations, herbs }, () => this.applyFilter());
    } finally { this.setData({ loading: false }); }
  },

  applyFilter() {
    const keyword = this.data.keyword.trim().toLowerCase();
    const filteredLocations = this.data.locations
      .filter((location) => {
        if (this.data.type && location.type !== this.data.type) return false;
        if (!keyword) return true;
        return location.searchText.includes(keyword);
      })
      .map((location) => decorateLocation(location, keyword));
    const selectedLocation = this.data.selectedLocation
      ? decorateLocation(this.data.locations.find((location) => Number(location.id) === Number(this.data.selectedLocation.id)) || this.data.selectedLocation, keyword)
      : null;
    this.setData({ filteredLocations, selectedLocation });
  },

  onKeywordChange(e) { this.setData({ keyword: e.detail.value }, this.applyFilter); },
  search() { this.applyFilter(); },
  onTypeChange(e) { const typeIndex = Number(e.detail.value); this.setData({ typeIndex, type: TYPES[typeIndex].value }, this.applyFilter); },
  onStoreChange(e) {
    const storeIndex = Number(e.detail.value);
    const store = this.data.stores[storeIndex];
    this.setData({ storeIndex, storeId: store?.id || '', storeName: store?.name || '' });
    this.load();
  },

  selectLocation(e) {
    const location = this.data.locations.find((item) => Number(item.id) === Number(e.currentTarget.dataset.id));
    if (location) this.setData({ selectedLocation: decorateLocation(location, this.data.keyword), detailVisible: true });
  },
  closeDetail() { this.setData({ detailVisible: false, selectedLocation: null }); },

  openAssignment() {
    const location = this.data.selectedLocation;
    const assignmentTypeIndex = Math.max(0, POSITION_TYPES.findIndex((item) => item.value === (location?.type || 'D')));
    this.setData({
      assignmentVisible: true,
      assignmentTypeIndex,
      assignmentForm: {
        type: location?.type || 'D',
        unitNo: String(location?.unitNo ?? ''),
        layerNo: String(location?.layerNo ?? ''),
        columnNo: String(location?.columnNo ?? ''),
        slotNo: '',
        herbId: '',
        name: '',
        code: '',
        specification: ''
      }
    });
  },
  closeAssignment() { this.setData({ assignmentVisible: false }); },
  onAssignmentChange(e) { this.setData({ [`assignmentForm.${e.currentTarget.dataset.field}`]: e.detail.value }); },
  onAssignmentTypeChange(e) {
    const assignmentTypeIndex = Number(e.detail.value);
    this.setData({ assignmentTypeIndex, 'assignmentForm.type': POSITION_TYPES[assignmentTypeIndex].value });
  },
  onHerbChange(e) {
    const herb = this.data.herbs[Number(e.detail.value)];
    if (!herb) return;
    this.setData({ 'assignmentForm.herbId': herb.id, 'assignmentForm.name': herb.name, 'assignmentForm.code': herb.code || '', 'assignmentForm.specification': herb.specification || '' });
  },
  async saveAssignment() {
    const form = this.data.assignmentForm;
    if (!form.type || !form.unitNo || form.layerNo === '' || (form.type === 'D' && !form.columnNo) || (!form.herbId && !form.name)) {
      return wx.showToast({ title: '请完整填写位置和药材名称', icon: 'none' });
    }
    const locationCode = form.type === 'D'
      ? ['D', form.unitNo, form.layerNo, form.columnNo, form.slotNo].filter(Boolean).join('-')
      : [form.type, form.unitNo, form.layerNo].join('-');
    this.setData({ saving: true });
    try {
      await saveHerbLocationAssignment({ ...form, locationCode, ...(this.data.isSuperAdmin ? { storeId: this.data.storeId } : {}) });
      this.setData({ assignmentVisible: false });
      await this.load();
      this.refreshSelectedLocation();
      wx.showToast({ title: '斗谱已保存', icon: 'success' });
    } finally { this.setData({ saving: false }); }
  },

  openPositionEdit(e) {
    const herb = this.data.selectedLocation.herbs[Number(e.currentTarget.dataset.index)];
    const location = this.data.selectedLocation;
    const positionTypeIndex = Math.max(0, POSITION_TYPES.findIndex((item) => item.value === location.type));
    this.setData({
      positionEditVisible: true,
      positionTypeIndex,
      positionEditForm: {
        assignmentId: herb.assignmentId,
        name: herb.name,
        type: location.type,
        unitNo: String(location.unitNo ?? ''),
        layerNo: String(location.layerNo ?? ''),
        columnNo: String(location.columnNo ?? ''),
        slotNo: String(herb.slotNo ?? '')
      }
    });
  },
  onPositionChange(e) { this.setData({ [`positionEditForm.${e.currentTarget.dataset.field}`]: e.detail.value }); },
  onPositionTypeChange(e) {
    const positionTypeIndex = Number(e.detail.value);
    this.setData({ positionTypeIndex, 'positionEditForm.type': POSITION_TYPES[positionTypeIndex].value });
  },
  async savePosition() {
    const form = this.data.positionEditForm;
    if (!form.type || !form.unitNo || form.layerNo === '' || (form.type === 'D' && !form.columnNo)) {
      return wx.showToast({ title: '请完整填写位置', icon: 'none' });
    }
    const locationCode = form.type === 'D'
      ? ['D', form.unitNo, form.layerNo, form.columnNo, form.slotNo].filter(Boolean).join('-')
      : [form.type, form.unitNo, form.layerNo].join('-');
    this.setData({ saving: true });
    try { await updateHerbLocationAssignment(form.assignmentId, { locationCode }); this.setData({ positionEditVisible: false }); await this.load(); this.refreshSelectedLocation(); }
    finally { this.setData({ saving: false }); }
  },

  openHerbEdit(e) {
    const herb = this.data.selectedLocation.herbs[Number(e.currentTarget.dataset.index)];
    this.setData({ herbEditVisible: true, herbEditForm: { id: herb.id, name: herb.name || '', code: herb.code || '', specification: herb.specification || '' } });
  },
  onHerbEditChange(e) { this.setData({ [`herbEditForm.${e.currentTarget.dataset.field}`]: e.detail.value }); },
  async saveHerbEdit() {
    const form = this.data.herbEditForm;
    if (!form.name) return wx.showToast({ title: '请填写药材名称', icon: 'none' });
    this.setData({ saving: true });
    try { await updateHerb(form.id, { name: form.name, code: form.code, specification: form.specification, ...(this.data.isSuperAdmin ? { storeId: this.data.storeId } : {}) }); this.setData({ herbEditVisible: false }); await this.load(); this.refreshSelectedLocation(); }
    finally { this.setData({ saving: false }); }
  },

  async removeAssignment(e) {
    const herb = this.data.selectedLocation.herbs[Number(e.currentTarget.dataset.index)];
    const result = await new Promise((resolve) => wx.showModal({ title: '移除药材', content: `确认从当前位置移除${herb.name}吗？`, success: resolve }));
    if (!result.confirm) return;
    await removeHerbLocationAssignment(herb.assignmentId);
    await this.load();
    this.refreshSelectedLocation();
  },

  refreshSelectedLocation() {
    if (!this.data.selectedLocation) return;
    const selectedLocation = this.data.locations.find((item) => Number(item.id) === Number(this.data.selectedLocation.id));
    this.setData({ selectedLocation: selectedLocation ? decorateLocation(selectedLocation, this.data.keyword) : null });
  },
  closePositionEdit() { this.setData({ positionEditVisible: false }); },
  closeHerbEdit() { this.setData({ herbEditVisible: false }); },
  noop() {}
});
