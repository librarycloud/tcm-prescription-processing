<template>
  <div class="page herb-page">
    <div class="page-header">
      <div>
        <h1 class="page-title">斗谱管理</h1>
        <p class="page-subtitle">{{ currentStore ? `${currentStore.name} · 药材与位置` : '选择门店后维护斗谱' }}</p>
      </div>
      <div class="header-actions">
        <el-select v-if="userStore.isSuperAdmin" v-model="storeId" class="store-select" placeholder="选择门店" @change="loadData">
          <el-option v-for="store in stores" :key="store.id" :label="store.name" :value="store.id" />
        </el-select>
        <el-button :icon="Download" :disabled="!storeId" @click="download(true)">模板</el-button>
        <el-button :icon="Upload" :disabled="!storeId" @click="uploadVisible = true">导入</el-button>
        <el-button :icon="Location" :disabled="!storeId" @click="moveUploadVisible = true">批量改位置</el-button>
        <el-button :icon="Download" :disabled="!storeId" @click="download(false)">导出</el-button>
        <el-button type="primary" :icon="Plus" :disabled="!storeId" @click="openAssignment()">配置药材</el-button>
      </div>
    </div>

    <div class="control-bar">
      <el-radio-group v-model="locationType" @change="resetLocationSelection">
        <el-radio-button value="D">药斗 D</el-radio-button>
        <el-radio-button value="G">柜 G</el-radio-button>
        <el-radio-button value="F">冰箱 F</el-radio-button>
        <el-radio-button value="C">仓库 C</el-radio-button>
      </el-radio-group>
      <el-input v-model.trim="keyword" clearable :prefix-icon="Search" placeholder="药材名称、拼音首字母、编码或位置编号" />
      <el-radio-group v-model="viewMode" class="view-switch">
        <el-radio-button value="map">位置图</el-radio-button>
        <el-radio-button value="table">明细</el-radio-button>
      </el-radio-group>
    </div>

    <div v-if="unitNumbers.length" class="unit-strip" aria-label="选择柜号">
      <span class="unit-strip-label">{{ locationType === 'D' ? '斗柜' : typeName(locationType) }}：</span>
      <el-button
        v-for="unit in unitNumbers"
        :key="unit"
        :type="selectedUnit === unit ? 'primary' : 'default'"
        plain
        @click="selectedUnit = unit"
      >
        {{ unit }}号
      </el-button>
    </div>

    <div v-loading="loading" class="workspace">
      <template v-if="viewMode === 'map' && storeId">
        <section v-if="locationType === 'D'" class="map-surface paired-layout">
          <div class="map-main">
            <div class="surface-title">药斗柜 D{{ selectedUnit }}</div>
            <div class="d-grid" role="grid">
              <div class="drawer-row" :style="drawerRowStyle(columns.length)">
                <div class="grid-corner">层 / 列</div>
                <div v-for="column in columns" :key="column" class="grid-axis">{{ column }}</div>
              </div>
              <div v-for="layer in layers" :key="layer" class="drawer-row" :style="drawerRowStyle(columnsForLayer(layer).length)">
                <div class="grid-axis">{{ layer === 0 ? '顶层' : layer }}</div>
                <button
                  v-for="column in columnsForLayer(layer)"
                  :key="`D-${selectedUnit}-${layer}-${column}`"
                  class="location-cell"
                  :class="{ selected: selectedLocation?.code === dCode(layer, column), empty: !getLocation(dCode(layer, column))?.herbs.length }"
                  @click="selectLocation(getLocation(dCode(layer, column)))"
                >
                  <span>{{ displayCode(dCode(layer, column)) }}</span>
                  <strong>{{ herbText(getLocation(dCode(layer, column))) || '未配置' }}</strong>
                </button>
              </div>
            </div>
          </div>
          <aside class="paired-cabinet">
            <div class="surface-title">柜 G{{ selectedUnit }}</div>
            <button
              v-for="layer in bigCabinetLayers"
              :key="layer.code"
              class="large-location"
              :class="{ selected: selectedLocation?.code === layer.code, empty: !layer.herbs.length }"
              @click="selectLocation(layer)"
            >
              <span>{{ displayCode(layer.code) }} · 第 {{ layer.layerNo }} 层</span>
              <strong>{{ herbText(layer) || '未配置' }}</strong>
            </button>
          </aside>
        </section>

        <section v-else class="map-surface shelf-layout">
          <div class="surface-title">{{ locationType === 'G' ? `柜 G${selectedUnit}` : `${locationType === 'F' ? '冰箱' : '仓库'} ${selectedUnit} 号` }}</div>
          <button
            v-for="location in shelfLocations"
            :key="location.code"
            class="shelf-location"
            :class="{ selected: selectedLocation?.code === location.code, empty: !location.herbs.length }"
            @click="selectLocation(location)"
          >
            <span>{{ displayCode(location.code) }} · 第 {{ location.layerNo }} 层</span>
            <strong>{{ herbText(location) || '未配置' }}</strong>
          </button>
          <el-empty v-if="!shelfLocations.length" description="暂无位置" :image-size="72">
            <el-button type="primary" @click="openAssignment()">配置药材</el-button>
          </el-empty>
        </section>
      </template>

      <section v-else-if="storeId" class="table-surface">
        <el-table :data="filteredLocations" border row-key="id" table-layout="auto">
          <el-table-column label="位置编号">
            <template #default="{ row }">{{ displayCode(row.code) }}</template>
          </el-table-column>
          <el-table-column label="区域">
            <template #default="{ row }">{{ typeName(row.type) }}</template>
          </el-table-column>
          <el-table-column label="药材">
            <template #default="{ row }">{{ herbText(row) || '-' }}</template>
          </el-table-column>
          <el-table-column label="操作">
            <template #default="{ row }"><el-button link type="primary" @click="selectLocation(row)">查看</el-button></template>
          </el-table-column>
        </el-table>
      </section>
      <el-empty v-else description="请选择门店" />
    </div>

    <el-drawer v-model="drawerVisible" direction="rtl" size="min(430px, 100%)" :with-header="false">
      <div v-if="selectedLocation" class="location-detail">
        <div class="detail-heading">
          <div>
            <div class="detail-code">{{ displayCode(selectedLocation.code) }}</div>
            <div class="detail-area">{{ typeName(selectedLocation.type) }}</div>
          </div>
          <div class="detail-actions">
            <el-button :icon="Plus" type="primary" circle title="添加药材" @click="openAssignment(selectedLocation)" />
            <el-button :icon="Close" circle title="关闭位置详情" aria-label="关闭位置详情" @click="drawerVisible = false" />
          </div>
        </div>
        <div class="detail-list">
            <div v-for="herb in selectedLocation.herbs" :key="herb.assignmentId" class="herb-line">
            <div>
              <strong>{{ herb.name }}</strong>
              <span>{{ [herbPosition(selectedLocation.code, herb.slotNo), herb.code, herb.specification].filter(Boolean).join(' · ') || '-' }}</span>
            </div>
            <div class="herb-actions">
              <el-button :icon="Location" link type="primary" title="编辑位置" @click="openPositionEdit(herb)">位置</el-button>
              <el-button :icon="Edit" text type="primary" title="编辑药材" @click="openHerbEdit(herb)" />
              <el-button :icon="Delete" text type="danger" title="移除药材" @click="removeAssignment(herb)" />
            </div>
          </div>
          <el-empty v-if="!selectedLocation.herbs.length" description="未配置药材" :image-size="72" />
        </div>
      </div>
    </el-drawer>

    <el-dialog v-model="assignmentVisible" title="配置药材" width="520px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="位置编号" required>
          <el-input v-model.trim="assignmentForm.locationCode" placeholder="D122 或 D1222（末位为格内序号）" />
        </el-form-item>
        <el-form-item label="已有药材">
          <el-select v-model="assignmentForm.herbId" clearable filterable :filter-method="filterExistingHerbs" placeholder="名称、拼音首字母或编码" style="width: 100%" @change="selectExistingHerb">
            <el-option v-for="herb in filteredExistingHerbs" :key="herb.id" :label="[herb.code, herb.name, herb.specification].filter(Boolean).join(' · ')" :value="herb.id" />
          </el-select>
        </el-form-item>
        <template v-if="!assignmentForm.herbId">
          <el-form-item label="药材名称" required><el-input v-model.trim="assignmentForm.name" maxlength="100" /></el-form-item>
          <el-form-item label="药材编码"><el-input v-model.trim="assignmentForm.code" maxlength="64" /></el-form-item>
          <el-form-item label="规格"><el-input v-model.trim="assignmentForm.specification" maxlength="100" /></el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="assignmentVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveAssignment">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="herbEditVisible" title="编辑药材" width="430px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="药材名称" required><el-input v-model.trim="herbEditForm.name" maxlength="100" /></el-form-item>
        <el-form-item label="药材编码"><el-input v-model.trim="herbEditForm.code" maxlength="64" /></el-form-item>
        <el-form-item label="规格"><el-input v-model.trim="herbEditForm.specification" maxlength="100" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="herbEditVisible = false">取消</el-button>
        <el-button type="primary" :loading="herbEditSaving" @click="saveHerbEdit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="positionEditVisible" :title="`编辑“${positionEditForm.name}”位置`" width="430px" destroy-on-close>
      <el-form label-position="top">
        <div class="position-form-grid">
          <el-form-item label="位置类型" required>
            <el-select v-model="positionEditForm.type">
              <el-option label="药斗" value="D" />
              <el-option label="柜" value="G" />
              <el-option label="冰箱" value="F" />
              <el-option label="仓库" value="C" />
            </el-select>
          </el-form-item>
          <el-form-item :label="positionUnitLabel" required>
            <el-input v-model.trim="positionEditForm.unitNo" inputmode="numeric" />
          </el-form-item>
          <el-form-item label="层" required>
            <el-input v-model.trim="positionEditForm.layerNo" inputmode="numeric" />
          </el-form-item>
          <el-form-item v-if="positionEditForm.type === 'D'" label="列" required>
            <el-input v-model.trim="positionEditForm.columnNo" inputmode="numeric" />
          </el-form-item>
          <el-form-item v-if="positionEditForm.type === 'D'" label="格内序号">
            <el-input v-model.trim="positionEditForm.slotNo" inputmode="numeric" placeholder="可选" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="positionEditVisible = false">取消</el-button>
        <el-button type="primary" :loading="positionEditSaving" @click="savePositionEdit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="uploadVisible" title="导入斗谱" width="500px" destroy-on-close>
      <el-upload drag :auto-upload="false" accept=".xlsx" :show-file-list="false" :on-change="handleFileChange">
        <el-icon class="upload-icon"><UploadFilled /></el-icon>
        <div class="el-upload__text">选择 Excel 文件</div>
      </el-upload>
    </el-dialog>

    <el-dialog v-model="moveUploadVisible" title="批量修改位置" width="500px" destroy-on-close>
      <div class="move-upload-actions">
        <el-button :icon="Download" @click="downloadMoveTemplate">下载当前位置模板</el-button>
      </div>
      <el-upload drag :auto-upload="false" accept=".xlsx" :show-file-list="false" :on-change="handleMoveFileChange">
        <el-icon class="upload-icon"><UploadFilled /></el-icon>
        <div class="el-upload__text">选择修改后的 Excel 文件</div>
      </el-upload>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { Close, Delete, Download, Edit, Location, Plus, Search, Upload, UploadFilled } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { pinyin } from 'pinyin-pro';
import {
  downloadHerbLocationMoveTemplate,
  downloadHerbLocationWorkbook,
  getHerbLocationStores,
  getHerbLocations,
  importHerbLocationMoves,
  importHerbLocations,
  removeHerbLocationAssignment,
  saveHerbLocationAssignment,
  updateHerb,
  updateHerbLocationAssignment
} from '@/api/herbLocation';
import { useUserStore } from '@/stores/user';

const userStore = useUserStore();
const loading = ref(false);
const saving = ref(false);
const stores = ref([]);
const storeId = ref(null);
const currentStore = ref(null);
const locations = ref([]);
const herbs = ref([]);
const locationType = ref('D');
const selectedUnit = ref(1);
const keyword = ref('');
const existingHerbKeyword = ref('');
const viewMode = ref('map');
const selectedLocation = ref(null);
const drawerVisible = ref(false);
const assignmentVisible = ref(false);
const uploadVisible = ref(false);
const moveUploadVisible = ref(false);
const herbEditVisible = ref(false);
const herbEditSaving = ref(false);
const positionEditVisible = ref(false);
const positionEditSaving = ref(false);
const positionEditForm = ref(emptyPositionEditForm());
const herbEditForm = ref({ id: null, name: '', code: '', specification: '' });
const assignmentForm = ref(emptyAssignmentForm());
const layout = ref(defaultLayout());
const layers = computed(() => [0, ...Array.from({ length: layout.value.drawerLayerCount }, (_, index) => index + 1)]);
const columns = computed(() => Array.from({ length: Math.max(...columnsForSelectedCabinet.value) }, (_, index) => index + 1));
const columnsForSelectedCabinet = computed(() => layout.value.drawerLayerColumns[selectedUnit.value - 1] || []);
const positionUnitLabel = computed(() => ({ D: '斗', G: '柜', F: '冰箱', C: '仓库' }[positionEditForm.value.type] || '编号'));

function defaultLayout() {
  return {
    drawerUnitCount: 5,
    drawerLayerCount: 8,
    drawerLayerColumns: Array.from({ length: 5 }, () => [6, 6, 6, 6, 6, 6, 6, 6, 3]),
    bigCabinetUnitCount: 5,
    bigCabinetLayerCount: 3
  };
}

function normalizeLayout(value) {
  const next = { ...defaultLayout(), ...(value || {}) };
  const rawColumns = Array.isArray(next.drawerLayerColumns) ? next.drawerLayerColumns : [];
  const legacyColumns = rawColumns.every((column) => !Array.isArray(column)) ? rawColumns.map(Number) : null;
  const cabinetColumns = Array.from({ length: Number(next.drawerUnitCount) || 5 }, (_, unitIndex) => {
    const source = legacyColumns
      ? [Number(next.drawerTopColumnCount) || 6, ...legacyColumns]
      : (rawColumns[unitIndex] || []);
    const columnsByLayer = source.map(Number).slice(0, next.drawerLayerCount + 1);
    while (columnsByLayer.length < next.drawerLayerCount + 1) columnsByLayer.push(3);
    return columnsByLayer;
  });
  return { ...next, drawerLayerColumns: cabinetColumns };
}

function emptyAssignmentForm(locationCode = '') {
  return { locationCode, herbId: null, name: '', code: '', specification: '' };
}

function emptyPositionEditForm() {
  return { assignmentId: null, name: '', type: 'D', unitNo: '', layerNo: '', columnNo: '', slotNo: '' };
}

const locationMap = computed(() => new Map(locations.value.map((location) => [location.code, location])));
const typeLocations = computed(() => locations.value.filter((location) => location.type === locationType.value));
const unitNumbers = computed(() => {
  const units = [...new Set(typeLocations.value.map((location) => location.unitNo))].sort((a, b) => a - b);
  if (locationType.value === 'D') return Array.from({ length: layout.value.drawerUnitCount }, (_, index) => index + 1);
  if (locationType.value === 'G') return Array.from({ length: layout.value.bigCabinetUnitCount }, (_, index) => index + 1);
  return units;
});
const bigCabinetLayers = computed(() => Array.from({ length: layout.value.bigCabinetLayerCount }, (_, index) => index + 1).map((layerNo) => getLocation(`G-${selectedUnit.value}-${layerNo}`)).filter(Boolean));
const shelfLocations = computed(() => typeLocations.value.filter((location) => location.unitNo === selectedUnit.value));
const existingHerbSearchIndex = computed(() =>
  herbs.value.map((herb) => ({
    herb,
    text: [herb.name, herbNameInitials(herb.name), herb.code]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()
  }))
);
const filteredExistingHerbs = computed(() => {
  const value = existingHerbKeyword.value.toLowerCase();
  if (!value) return herbs.value;
  return existingHerbSearchIndex.value.filter((item) => item.text.includes(value)).map((item) => item.herb);
});
const locationSearchIndex = computed(() =>
  locations.value.map((location) => ({
    location,
    text: [
      location.code,
      displayCode(location.code),
      ...location.herbs.flatMap((herb) => [
        herb.name,
        herbNameInitials(herb.name),
        herb.code
      ])
    ]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()
  }))
);
const filteredLocations = computed(() => {
  const value = keyword.value.toLowerCase();
  if (!value) return locations.value;
  return locationSearchIndex.value.filter((item) => item.text.includes(value)).map((item) => item.location);
});

function herbNameInitials(name) {
  if (!name) return '';
  return pinyin(name, { pattern: 'first', toneType: 'none', type: 'array' }).join('');
}

function filterExistingHerbs(value) {
  existingHerbKeyword.value = value.trim();
}

function dCode(layer, column) {
  return `D-${selectedUnit.value}-${layer}-${column}`;
}

function columnsForLayer(layer) {
  return Array.from({ length: columnsForSelectedCabinet.value[layer] || 0 }, (_, index) => index + 1);
}

function drawerRowStyle(columnCount) {
  return {
    gridTemplateColumns: `52px repeat(${columnCount}, minmax(88px, 1fr))`,
    minWidth: `${60 + columns.value.length * 96}px`
  };
}

function displayCode(code) {
  return String(code || '').replaceAll('-', '');
}

function herbPosition(locationCode, slotNo) {
  return `${displayCode(locationCode)}${slotNo || ''}`;
}

function getLocation(code) {
  return locationMap.value.get(code) || null;
}

function herbText(location) {
  return location?.herbs?.map((herb) => herb.name).join(' / ') || '';
}

function typeName(type) {
  return { D: '药斗', G: '柜', F: '冰箱', C: '仓库' }[type] || type;
}

function resetLocationSelection() {
  selectedUnit.value = unitNumbers.value[0] || 1;
  drawerVisible.value = false;
}

function selectLocation(location) {
  if (!location) return;
  selectedLocation.value = location;
  drawerVisible.value = true;
}

function openAssignment(location = null) {
  existingHerbKeyword.value = '';
  assignmentForm.value = emptyAssignmentForm(displayCode(location?.code || selectedLocation.value?.code || ''));
  assignmentVisible.value = true;
}

function openHerbEdit(herb) {
  herbEditForm.value = {
    id: herb.id,
    name: herb.name || '',
    code: herb.code || '',
    specification: herb.specification || '',
  };
  herbEditVisible.value = true;
}

function openPositionEdit(herb) {
  const location = selectedLocation.value;
  positionEditForm.value = {
    assignmentId: herb.assignmentId,
    name: herb.name,
    type: location.type,
    unitNo: String(location.unitNo ?? ''),
    layerNo: String(location.layerNo ?? ''),
    columnNo: String(location.columnNo ?? ''),
    slotNo: String(herb.slotNo ?? '')
  };
  positionEditVisible.value = true;
}

async function savePositionEdit() {
  const form = positionEditForm.value;
  if (!form.type || !form.unitNo || form.layerNo === '' || (form.type === 'D' && !form.columnNo)) {
    return ElMessage.warning('请完整填写位置');
  }
  const locationCode = form.type === 'D'
    ? ['D', form.unitNo, form.layerNo, form.columnNo, form.slotNo].filter(Boolean).join('-')
    : [form.type, form.unitNo, form.layerNo].join('-');
  positionEditSaving.value = true;
  try {
    await updateHerbLocationAssignment(form.assignmentId, {
      storeId: storeId.value,
      locationCode
    });
    positionEditVisible.value = false;
    ElMessage.success('药材位置已更新');
    await loadData();
  } finally {
    positionEditSaving.value = false;
  }
}

async function saveHerbEdit() {
  const form = herbEditForm.value;
  if (!form.name) return ElMessage.warning('请填写药材名称');
  herbEditSaving.value = true;
  try {
    await updateHerb(form.id, { storeId: storeId.value, ...form });
    herbEditVisible.value = false;
    ElMessage.success('药材已更新');
    await loadData();
  } finally {
    herbEditSaving.value = false;
  }
}

function selectExistingHerb(id) {
  if (!id) return;
  const herb = herbs.value.find((item) => item.id === id);
  if (herb) Object.assign(assignmentForm.value, { name: herb.name, code: herb.code || '', specification: herb.specification || '' });
}

async function loadData() {
  if (!storeId.value) return;
  loading.value = true;
  try {
    const data = await getHerbLocations(storeId.value);
    currentStore.value = data.store;
    layout.value = normalizeLayout(data.layout);
    locations.value = data.locations || [];
    herbs.value = data.herbs || [];
    if (!unitNumbers.value.includes(selectedUnit.value)) selectedUnit.value = unitNumbers.value[0] || 1;
    if (selectedLocation.value) selectedLocation.value = locationMap.value.get(selectedLocation.value.code) || null;
  } finally {
    loading.value = false;
  }
}

async function saveAssignment() {
  const form = assignmentForm.value;
  if (!form.locationCode || (!form.herbId && !form.name)) return ElMessage.warning('请填写位置编号和药材名称');
  saving.value = true;
  try {
    const result = await saveHerbLocationAssignment({ ...form, storeId: storeId.value });
    assignmentVisible.value = false;
    ElMessage.success(result.created ? '斗谱已保存' : '该药材已在此位置');
    await loadData();
  } finally {
    saving.value = false;
  }
}

async function removeAssignment(herb) {
  await ElMessageBox.confirm(`确认从 ${displayCode(selectedLocation.value.code)} 移除“${herb.name}”？`, '移除药材', { type: 'warning' });
  await removeHerbLocationAssignment(herb.assignmentId);
  ElMessage.success('药材已移除');
  await loadData();
}

function saveBlob(blob, filename) {
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = filename;
  link.click();
  URL.revokeObjectURL(link.href);
}

async function download(template) {
  const blob = await downloadHerbLocationWorkbook(storeId.value, template);
  saveBlob(blob, template ? '斗谱导入模板.xlsx' : '斗谱表.xlsx');
}

async function downloadMoveTemplate() {
  const blob = await downloadHerbLocationMoveTemplate(storeId.value);
  saveBlob(blob, '斗谱批量修改位置.xlsx');
}

async function handleFileChange(uploadFile) {
  if (!uploadFile.raw) return;
  try {
    const result = await importHerbLocations(storeId.value, uploadFile.raw);
    uploadVisible.value = false;
    ElMessage.success(`导入完成：新增 ${result.added} 条，更新 ${result.updated} 条，跳过 ${result.skipped} 条`);
    await loadData();
  } catch {
    // Request interceptor displays the server-side validation message.
  }
}

async function handleMoveFileChange(uploadFile) {
  if (!uploadFile.raw) return;
  try {
    const result = await importHerbLocationMoves(storeId.value, uploadFile.raw);
    moveUploadVisible.value = false;
    ElMessage.success(`位置修改完成：移动 ${result.moved} 条，未变更 ${result.skipped} 条`);
    await loadData();
  } catch {
    // Request interceptor displays the server-side validation message.
  }
}

watch(locationType, resetLocationSelection);
onMounted(async () => {
  stores.value = await getHerbLocationStores();
  storeId.value = userStore.isStoreAdmin ? userStore.user?.storeId : stores.value[0]?.id || null;
  await loadData();
});
</script>

<style scoped>
.herb-page { gap: 18px; }
.header-actions, .control-bar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.store-select { width: 180px; }
.control-bar { min-height: 44px; padding: 10px 12px; border: 1px solid var(--app-border); background: #fff; }
.move-upload-actions { margin-bottom: 14px; }
.control-bar :deep(.el-input) { width: min(300px, 100%); }
.view-switch { margin-left: auto; }
.unit-strip { display: flex; align-items: center; gap: 8px; min-height: 44px; padding: 8px 12px; border: 1px solid var(--app-border); background: #fff; overflow-x: auto; }
.unit-strip-label { flex: 0 0 auto; color: var(--app-muted); font-size: 13px; font-weight: 600; }
.unit-strip .el-button { flex: 0 0 auto; min-width: 58px; margin-left: 0; }
.workspace { min-height: 520px; }
.map-surface, .table-surface { min-height: 500px; border: 1px solid var(--app-border); background: #fff; }
.surface-title { padding: 14px 16px; border-bottom: 1px solid var(--app-border); font-size: 15px; font-weight: 700; }
.paired-layout { display: grid; grid-template-columns: minmax(620px, 1fr) 260px; }
.map-main { min-width: 0; border-right: 1px solid var(--app-border); }
.d-grid { display: flex; flex-direction: column; padding: 14px; gap: 8px; overflow-x: auto; }
.drawer-row { display: grid; gap: 8px; width: 100%; }
.grid-axis, .grid-corner { display: flex; align-items: center; justify-content: center; min-height: 28px; color: var(--app-muted); font-size: 12px; font-weight: 600; }
.location-cell, .large-location, .shelf-location { display: flex; min-width: 0; text-align: left; color: inherit; border: 1px solid var(--app-border); border-radius: 6px; background: #fff; cursor: pointer; }
.location-cell { min-height: 82px; flex-direction: column; justify-content: space-between; padding: 9px; }
.location-cell span, .large-location span, .shelf-location span { color: var(--app-muted); font-size: 12px; }
.location-cell strong, .large-location strong, .shelf-location strong { overflow: hidden; display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; margin-top: 7px; line-height: 1.4; font-size: 13px; }
.location-cell:hover, .large-location:hover, .shelf-location:hover, .selected { border-color: var(--el-color-primary); box-shadow: 0 0 0 2px var(--el-color-primary-light-9); }
.empty { background: #fafbfc; }
.paired-cabinet { display: flex; flex-direction: column; }
.large-location { flex: 1; flex-direction: column; justify-content: center; margin: 12px; padding: 14px; }
.shelf-layout { max-width: 760px; }
.shelf-location { width: calc(100% - 28px); min-height: 84px; flex-direction: column; justify-content: center; margin: 10px 14px; padding: 12px 14px; }
.table-surface { padding: 14px; overflow: auto; }
.location-detail { display: flex; min-height: 100%; flex-direction: column; }
.detail-heading { display: flex; align-items: flex-start; justify-content: space-between; padding: 6px 0 18px; border-bottom: 1px solid var(--app-border); }
.detail-actions { display: flex; align-items: center; gap: 8px; }
.detail-code { font-size: 22px; font-weight: 700; }
.detail-area { margin-top: 5px; color: var(--app-muted); font-size: 13px; }
.detail-list { padding-top: 8px; }
.herb-line { display: flex; align-items: center; justify-content: space-between; gap: 14px; padding: 14px 0; border-bottom: 1px solid var(--app-border); }
.herb-line div { min-width: 0; }
.herb-line strong, .herb-line span { display: block; }
.herb-actions { display: flex; align-items: center; }
.herb-line span { margin-top: 5px; color: var(--app-muted); font-size: 13px; }
.upload-icon { margin-bottom: 10px; font-size: 46px; color: var(--el-color-primary); }
.position-form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 12px; }
.position-form-grid :deep(.el-select) { width: 100%; }
@media (max-width: 960px) { .paired-layout { grid-template-columns: 1fr; } .map-main { border-right: 0; border-bottom: 1px solid var(--app-border); } .paired-cabinet { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); } .paired-cabinet .surface-title { grid-column: 1 / -1; } }
@media (max-width: 680px) { .header-actions { width: 100%; } .header-actions .el-button { flex: 1; } .store-select { width: 100%; } .control-bar { align-items: stretch; } .control-bar :deep(.el-radio-group), .control-bar :deep(.el-input) { width: 100%; } .view-switch { margin-left: 0; } .paired-cabinet { grid-template-columns: 1fr; } .paired-cabinet .surface-title { grid-column: auto; } .position-form-grid { grid-template-columns: 1fr; } }
</style>
