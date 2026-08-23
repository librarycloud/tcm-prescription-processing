<template>
  <div class="page yd-check-page">
    <div class="page-header">
      <div><h1 class="page-title">药店商品盘点</h1><p class="page-subtitle">按门店记录初盘、复盘和库存调整依据</p></div>
      <div class="header-actions"><el-button type="primary" :icon="Plus" @click="createDialog = true">新建盘点</el-button></div>
    </div>

    <el-card shadow="never">
      <el-form class="filters check-filters" inline @submit.prevent="loadChecks">
        <el-select v-if="userStore.isSuperAdmin" v-model="checkQuery.storeId" clearable placeholder="全部门店" @change="loadChecks">
          <el-option v-for="store in stores" :key="store.id" :label="store.name" :value="store.id" />
        </el-select>
        <el-select v-model="checkQuery.status" clearable placeholder="全部状态" @change="loadChecks">
          <el-option label="进行中" :value="1" /><el-option label="已完成" :value="2" />
        </el-select>
        <el-button :icon="Refresh" @click="resetChecks">重置</el-button>
      </el-form>
      <el-table v-loading="checksLoading" :data="checks" row-key="id" border table-layout="auto" highlight-current-row @row-click="selectCheck">
        <template #empty><EmptyView description="暂无盘点单" /></template>
        <el-table-column v-if="userStore.isSuperAdmin" label="门店"><template #default="{ row }">{{ row.store?.name || '-' }}</template></el-table-column>
        <el-table-column prop="checkName" label="盘点名称" min-width="180" />
        <el-table-column label="类型"><template #default="{ row }">{{ typeText(row.checkType) }}</template></el-table-column>
        <el-table-column label="状态"><template #default="{ row }"><el-tag :type="row.status === 2 ? 'success' : 'warning'">{{ row.status === 2 ? '已完成' : '进行中' }}</el-tag></template></el-table-column>
        <el-table-column label="记录数"><template #default="{ row }">{{ row.summary?.total || 0 }} / {{ row.summary?.counted || 0 }}</template></el-table-column>
        <el-table-column label="需调整"><template #default="{ row }">{{ row.summary?.adjustment || 0 }}</template></el-table-column>
        <el-table-column label="创建时间" min-width="160"><template #default="{ row }">{{ dateTime(row.createdAt) }}</template></el-table-column>
      </el-table>
      <Pagination v-model:page="checkPagination.page" v-model:page-size="checkPagination.pageSize" :total="checkPagination.total" />
    </el-card>

    <el-card v-if="selectedCheck" class="detail-card" shadow="never">
      <div class="detail-header">
        <div><strong>{{ selectedCheck.checkName }}</strong><span class="muted">{{ selectedCheck.store?.name || '' }}</span></div>
        <div class="header-actions">
          <el-button size="small" type="primary" :icon="Plus" @click="openCandidateDialog">新增盘点记录</el-button>
          <el-button size="small" :icon="Download" @click="download('all')">导出全部</el-button>
          <el-button size="small" @click="download('recount')">导出待复盘</el-button>
          <el-button size="small" type="warning" @click="download('adjustment')">导出需调整库存</el-button>
          <el-button v-if="selectedCheck.status !== 2" size="small" type="success" @click="finish">结束盘点</el-button>
        </div>
      </div>
      <el-form class="filters detail-filters" inline @submit.prevent="loadItems">
        <el-input v-model.trim="itemQuery.keyword" clearable placeholder="商品编号、名称或条形码" @keyup.enter="loadItems" />
        <el-input v-model.trim="itemQuery.locationName" clearable placeholder="系统货位或盘点货位" @keyup.enter="loadItems" />
        <el-select v-model="itemQuery.checkStatus" clearable placeholder="全部盘点状态" @change="loadItems">
          <el-option label="未盘" :value="0" /><el-option label="待复核" :value="1" /><el-option label="待复盘" :value="2" /><el-option label="复盘待复核" :value="3" /><el-option label="需调整库存" :value="4" /><el-option label="新增批号" :value="5" /><el-option label="已确认" :value="6" />
        </el-select>
        <el-select v-model="itemQuery.locationStatus" clearable placeholder="全部货位状态" @change="loadItems">
          <el-option label="货位未变化" :value="0" /><el-option label="货位有变化" :value="1" />
        </el-select>
        <el-select v-model="itemQuery.status" clearable placeholder="快捷筛选" @change="loadItems">
          <el-option label="待复盘" value="recount" /><el-option label="需调整库存" value="adjustment" /><el-option label="漏盘" value="missing" /><el-option label="待复核" value="unreviewed" /><el-option label="新增批号" value="new" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="loadItems">查询</el-button>
      </el-form>
      <el-table v-loading="itemsLoading" :data="items" border table-layout="auto">
        <template #empty><EmptyView description="暂无盘点明细" /></template>
        <el-table-column v-if="userStore.isSuperAdmin" label="门店"><template #default="{ row }">{{ row.store?.name || selectedCheck.store?.name || '-' }}</template></el-table-column>
        <el-table-column label="商品编号"><template #default="{ row }">{{ row.product?.productCode || '-' }}</template></el-table-column>
        <el-table-column label="商品名称" min-width="140"><template #default="{ row }"><el-tooltip v-if="isLongText(row.product?.name)" :content="row.product.name" placement="top"><span class="ellipsis-text">{{ shortText(row.product.name) }}</span></el-tooltip><span v-else>{{ row.product?.name || '-' }}</span></template></el-table-column>
        <el-table-column label="规格"><template #default="{ row }"><el-tooltip v-if="isLongText(row.product?.specification)" :content="row.product.specification" placement="top"><span class="ellipsis-text">{{ shortText(row.product.specification) }}</span></el-tooltip><span v-else>{{ row.product?.specification || '-' }}</span></template></el-table-column>
        <el-table-column label="单位" width="65"><template #default="{ row }">{{ row.product?.unit || '-' }}</template></el-table-column>
        <el-table-column prop="batchNo" label="批号" min-width="110" />
        <el-table-column label="系统货位"><template #default="{ row }">{{ row.systemLocationName || '-' }}</template></el-table-column>
        <el-table-column label="盘点货位"><template #default="{ row }">{{ row.countLocationName || '-' }}</template></el-table-column>
        <el-table-column label="系统数量" align="right"><template #default="{ row }">{{ qty(row.systemQty) }}</template></el-table-column>
        <el-table-column label="初盘" align="right"><template #default="{ row }">{{ row.firstCountQty === null ? '-' : qty(row.firstCountQty) }}</template></el-table-column>
        <el-table-column label="复盘" align="right"><template #default="{ row }">{{ row.recountQty === null ? '-' : qty(row.recountQty) }}</template></el-table-column>
        <el-table-column label="差异" align="right"><template #default="{ row }"><strong :class="{ danger: row.difference }">{{ row.difference === null ? '-' : qty(row.difference) }}</strong></template></el-table-column>
        <el-table-column label="状态"><template #default="{ row }">{{ statusText(row.checkStatus, row.needsAdjustment) }}</template></el-table-column>
        <el-table-column label="操作" min-width="240" fixed="right"><template #default="{ row }"><el-button link type="primary" :icon="Edit" @click="openCount(row)">{{ row.recountQty === null && row.firstCountQty !== null ? '复盘' : '盘点' }}</el-button><el-button link type="primary" @click="openLocation(row)">货位</el-button><el-button link type="success" :disabled="row.reviewStatus === 1" @click="review(row)">二次确认</el-button></template></el-table-column>
      </el-table>
      <Pagination v-model:page="itemPagination.page" v-model:page-size="itemPagination.pageSize" :total="itemPagination.total" />
    </el-card>

    <el-dialog v-model="createDialog" title="新建药店盘点" width="420px">
      <el-form label-width="90px"><el-form-item label="门店" required><el-select v-model="createForm.storeId" :disabled="!userStore.isSuperAdmin" placeholder="选择门店"><el-option v-for="store in stores" :key="store.id" :label="store.name" :value="store.id" /></el-select></el-form-item><el-form-item label="盘点名称" required><el-input v-model="createForm.checkName" placeholder="如：2026年7月临时盘点" /></el-form-item><el-form-item label="盘点类型"><el-select v-model="createForm.checkType"><el-option label="临时盘点" :value="1" /><el-option label="季度盘点" :value="2" /><el-option label="年度盘点" :value="3" /></el-select></el-form-item></el-form>
      <template #footer><el-button @click="createDialog = false">取消</el-button><el-button type="primary" @click="create">创建</el-button></template>
    </el-dialog>
    <el-dialog v-model="countDialog" :title="countForm.mode === 'recount' ? '复盘' : '初盘'" width="420px"><el-form label-width="90px"><el-form-item label="商品">{{ countForm.product?.productCode }} {{ countForm.product?.name }}</el-form-item><el-form-item label="批号"><el-input v-if="countForm.manualBatch" v-model="countForm.batchNo" placeholder="输入新增批号" /><span v-else>{{ countForm.batchNo || '-' }}</span></el-form-item><el-form-item label="系统数量">{{ qty(countForm.systemQty) }}</el-form-item><el-form-item v-if="countForm.mode === 'initial'" label="盘点货位"><el-input v-model="countForm.location" placeholder="没有变化可留空" /></el-form-item><el-form-item :label="countForm.mode === 'recount' ? '复盘数量' : '初盘数量'"><el-input-number v-model="countForm.qty" :min="0" :precision="3" controls-position="right" /></el-form-item></el-form><template #footer><el-button @click="countDialog = false">取消</el-button><el-button type="primary" @click="saveCount">保存</el-button></template></el-dialog>
    <el-dialog v-model="candidateDialog" title="选择 E6 商品或库存批次" width="820px"><el-form class="filters" inline @submit.prevent="loadCandidates"><el-input v-model.trim="candidateKeyword" clearable placeholder="条形码、商品编码或商品名称" @keyup.enter="loadCandidates" /><el-button type="primary" :icon="Search" @click="loadCandidates">查询</el-button></el-form><el-table v-loading="candidateLoading" :data="candidates" border max-height="420" @row-click="chooseCandidate"><el-table-column label="商品编号"><template #default="{ row }">{{ row.product?.productCode }}</template></el-table-column><el-table-column label="商品名称"><template #default="{ row }"><el-tooltip v-if="isLongText(row.product?.name)" :content="row.product.name" placement="top"><span class="ellipsis-text">{{ shortText(row.product.name) }}</span></el-tooltip><span v-else>{{ row.product?.name || '-' }}</span></template></el-table-column><el-table-column label="批号"><template #default="{ row }">{{ row.manualBatch ? '新增批号' : (row.batchNo || '-') }}</template></el-table-column><el-table-column label="货位"><template #default="{ row }">{{ row.manualBatch ? '-' : (row.locationName || '-') }}</template></el-table-column><el-table-column label="系统数量" align="right"><template #default="{ row }">{{ qty(row.quantity) }}</template></el-table-column><el-table-column label="状态"><template #default="{ row }">{{ row.manualBatch ? '手动新增' : (row.counted ? '已盘' : '未盘') }}</template></el-table-column></el-table></el-dialog>
    <el-dialog v-model="locationDialog" title="修改盘点货位" width="400px"><el-form label-width="90px"><el-form-item label="原货位">{{ locationForm.systemLocationName || '-' }}</el-form-item><el-form-item label="盘点货位"><el-input v-model="locationForm.location" placeholder="留空表示未变化" /></el-form-item></el-form><template #footer><el-button @click="locationDialog = false">取消</el-button><el-button type="primary" @click="saveLocation">保存</el-button></template></el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue';
import { Download, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useUserStore } from '@/stores/user';
import { getProductStores } from '@/api/productDifference';
import { addInitialCount, createGoodsCheck, exportGoodsCheck, finishGoodsCheck, getGoodsCheckCandidates, getGoodsCheckItems, getGoodsChecks, recountGoodsCheckItem, reviewGoodsCheckItem, updateGoodsCheckLocation } from '@/api/ydGoodsCheck';
import EmptyView from '@/components/EmptyView.vue';
import Pagination from '@/components/Pagination.vue';
import { formatDateSeconds } from '@/utils/date';

const userStore = useUserStore(); const stores = ref([]); const checks = ref([]); const items = ref([]); const selectedCheck = ref(null); const checksLoading = ref(false); const itemsLoading = ref(false);
const checkQuery = reactive({ storeId: undefined, status: undefined }); const checkPagination = reactive({ page: 1, pageSize: 20, total: 0 }); const itemQuery = reactive({ keyword: '', locationName: '', checkStatus: undefined, locationStatus: undefined, status: undefined }); const itemPagination = reactive({ page: 1, pageSize: 50, total: 0 });
const createDialog = ref(false); const createForm = reactive({ storeId: undefined, checkName: '', checkType: 1 }); const countDialog = ref(false); const countForm = reactive({ mode: 'initial', id: null, product: null, batchNo: '', location: '', manualBatch: false, systemQty: 0, qty: 0 }); const locationDialog = ref(false); const locationForm = reactive({ id: null, systemLocationName: '', location: '' }); const candidateDialog = ref(false); const candidates = ref([]); const candidateKeyword = ref(''); const candidateLoading = ref(false);
const typeText = (value) => ({ 1: '临时', 2: '季度', 3: '年度' }[Number(value)] || '-'); const dateTime = (value) => formatDateSeconds(value); const qty = (value) => { const n = Number(value || 0); return Number.isInteger(n) ? String(n) : n.toFixed(3).replace(/0+$/, '').replace(/\.$/, ''); };
const statusText = (value, adjustment) => adjustment ? '需调整库存' : ({ 0: '未盘', 1: '待复核', 2: '待复盘', 3: '复盘待复核', 4: '需调整库存', 5: '新增批号', 6: '已确认' }[Number(value)] || '-');
const isLongText = (value) => Array.from(String(value || '')).length > 12;
const shortText = (value) => `${Array.from(String(value || '')).slice(0, 12).join('')}…`;
async function loadStores() { if (userStore.isSuperAdmin) stores.value = await getProductStores(); else createForm.storeId = userStore.user?.storeId; }
async function loadChecks() { checksLoading.value = true; try { const data = await getGoodsChecks({ ...checkQuery, page: checkPagination.page, pageSize: checkPagination.pageSize }); checks.value = data.list || []; Object.assign(checkPagination, data.pagination || {}); if (selectedCheck.value) { const fresh = checks.value.find((x) => x.id === selectedCheck.value.id); if (fresh) selectedCheck.value = fresh; } } finally { checksLoading.value = false; } }
function resetChecks() { checkQuery.storeId = undefined; checkQuery.status = undefined; checkPagination.page = 1; loadChecks(); }
async function selectCheck(row) { selectedCheck.value = row; itemPagination.page = 1; await loadItems(); }
async function loadItems() { if (!selectedCheck.value) return; itemsLoading.value = true; try { const data = await getGoodsCheckItems(selectedCheck.value.id, { ...itemQuery, page: itemPagination.page, pageSize: itemPagination.pageSize }); items.value = data.list || []; Object.assign(itemPagination, data.pagination || {}); } finally { itemsLoading.value = false; } }
async function create() { if (!createForm.checkName.trim() || !createForm.storeId) return ElMessage.warning('请填写盘点名称并选择门店'); const result = await createGoodsCheck(createForm); createDialog.value = false; await loadChecks(); await selectCheck(result); ElMessage.success('盘点单已创建'); }
function openCount(row) { Object.assign(countForm, { mode: row.firstCountQty !== null && row.recountQty === null ? 'recount' : 'initial', id: row.id, product: row.product, batchNo: row.batchNo, location: row.countLocationName || '', manualBatch: false, systemQty: row.recountQty !== null ? row.recountSystemQty : row.systemQty, qty: row.recountQty !== null ? row.recountQty : row.firstCountQty ?? 0 }); countDialog.value = true; }
async function saveCount() { if (countForm.mode === 'recount') await recountGoodsCheckItem(countForm.id, { recountQty: countForm.qty }); else { if (countForm.manualBatch && !countForm.batchNo.trim()) return ElMessage.warning('请输入新增批号'); await addInitialCount(selectedCheck.value.id, { productId: countForm.product.id, batchNo: countForm.batchNo, locationName: countForm.location, firstCountQty: countForm.qty }); } countDialog.value = false; await loadItems(); await loadChecks(); ElMessage.success('盘点数量已保存'); }
function openCandidateDialog() { candidateKeyword.value = ''; candidateDialog.value = true; loadCandidates(); }
async function loadCandidates() { if (!selectedCheck.value) return; candidateLoading.value = true; try { candidates.value = await getGoodsCheckCandidates(selectedCheck.value.id, { keyword: candidateKeyword.value || undefined }); } finally { candidateLoading.value = false; } }
function chooseCandidate(row) { Object.assign(countForm, { mode: 'initial', id: null, product: row.product, batchNo: row.batchNo, location: row.locationName || '', manualBatch: Boolean(row.manualBatch), systemQty: row.quantity, qty: row.quantity }); candidateDialog.value = false; countDialog.value = true; }
function openLocation(row) { Object.assign(locationForm, { id: row.id, systemLocationName: row.systemLocationName, location: row.countLocationName || row.systemLocationName }); locationDialog.value = true; }
async function saveLocation() { await updateGoodsCheckLocation(locationForm.id, { countLocationName: locationForm.location }); locationDialog.value = false; await loadItems(); ElMessage.success('货位已保存'); }
async function review(row) { await ElMessageBox.confirm('确认由当前账号完成第二人复核？', '二次确认', { type: 'warning' }); await reviewGoodsCheckItem(row.id, { approved: true }); await loadItems(); await loadChecks(); ElMessage.success('已确认'); }
async function finish() { await ElMessageBox.confirm('结束后将不能继续盘点，是否继续？', '结束盘点', { type: 'warning' }); await finishGoodsCheck(selectedCheck.value.id); await loadChecks(); selectedCheck.value = { ...selectedCheck.value, status: 2 }; ElMessage.success('盘点已结束'); }
async function download(type) { const blob = await exportGoodsCheck(selectedCheck.value.id, type); const url = URL.createObjectURL(blob); const link = document.createElement('a'); link.href = url; link.download = `${selectedCheck.value.checkName}-${type}.xlsx`; link.click(); URL.revokeObjectURL(url); }
watch(() => [checkPagination.page, checkPagination.pageSize], loadChecks); watch(() => [itemPagination.page, itemPagination.pageSize], loadItems); onMounted(async () => { await loadStores(); await loadChecks(); });
</script>

<style scoped>
.filters { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; }
.filters :deep(.el-input) { width: 220px; }
.filters :deep(.el-select) { width: 150px; }
.check-filters { flex-wrap: nowrap; }
.check-filters :deep(.el-select) { flex: 0 0 150px; }
.check-filters :deep(.el-button) { flex: 0 0 auto; }
.detail-card { margin-top: 16px; }
.detail-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.detail-header .muted { margin-left: 12px; color: var(--el-text-color-secondary); }
.detail-filters { flex-wrap: wrap; margin-bottom: 14px; }
.detail-filters :deep(.el-form-item) { flex: 0 0 auto; }
.detail-filters :deep(.el-input) { width: 190px; }
.detail-filters :deep(.el-select) { width: 145px; flex: 0 0 145px; }
.detail-filters :deep(.el-button) { flex: 0 0 auto; }
.danger { color: var(--el-color-danger); }
.yd-check-page :deep(.el-table .cell) { min-width: 0; white-space: nowrap; }
.yd-check-page :deep(.el-table__cell) { white-space: nowrap; }
.ellipsis-text { display: inline-block; max-width: 12em; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; vertical-align: bottom; }
</style>
