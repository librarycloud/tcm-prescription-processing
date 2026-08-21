<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">群机器人通知</h1>
        <p class="page-subtitle">按总部或门店向内部工作群推送业务动态</p>
      </div>
      <div class="header-actions">
        <el-button :icon="Refresh" :loading="loading" @click="refreshCurrent">刷新</el-button>
        <el-button
          v-if="activeTab === 'robots'"
          type="primary"
          :icon="Plus"
          @click="openRobotDialog()"
          >新增机器人</el-button
        >
      </div>
    </div>

    <el-tabs v-model="activeTab" class="notification-tabs" @tab-change="handleTabChange">
      <el-tab-pane label="机器人配置" name="robots">
        <el-table v-loading="loading" :data="robots" border table-layout="auto">
          <el-table-column prop="name" label="机器人名称" />
          <el-table-column label="平台"
            ><template #default="{ row }">{{
              platformName(row.platform)
            }}</template></el-table-column
          >
          <el-table-column label="归属"
            ><template #default="{ row }">{{
              row.scopeType === 'HEADQUARTERS' ? '总部' : row.store?.name || '门店'
            }}</template></el-table-column
          >
          <el-table-column label="订阅事件"
            ><template #default="{ row }"
              >{{ row.events.filter((item) => item.enabled).length }} /
              {{ row.events.length }}</template
            ></el-table-column
          >
          <el-table-column label="状态"
            ><template #default="{ row }"
              ><el-tag :type="row.enabled ? 'success' : 'info'">{{
                row.enabled ? '启用' : '停用'
              }}</el-tag></template
            ></el-table-column
          >
          <el-table-column prop="remark" label="备注" show-overflow-tooltip />
          <el-table-column label="操作">
            <template #default="{ row }">
              <div class="table-actions">
                <el-button link type="primary" @click="selectRobotEvents(row)">事件</el-button>
                <el-button link type="primary" @click="openTestDialog(row)">测试</el-button>
                <el-button link type="primary" @click="openRobotDialog(row)">编辑</el-button>
                <el-button link type="danger" @click="removeRobot(row)">删除</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!loading && !robots.length" description="还没有配置群机器人" />
      </el-tab-pane>

      <el-tab-pane label="事件配置" name="events">
        <div class="event-toolbar">
          <span class="toolbar-label">选择机器人</span>
          <el-select
            v-model="selectedRobotId"
            placeholder="请选择机器人"
            filterable
            @change="syncSelectedRobot"
          >
            <el-option
              v-for="item in robots"
              :key="item.id"
              :label="`${item.name} · ${item.scopeType === 'HEADQUARTERS' ? '总部' : item.store?.name || '门店'}`"
              :value="item.id"
            />
          </el-select>
        </div>
        <el-table v-if="selectedRobot" :data="selectedRobot.events" border table-layout="auto">
          <el-table-column prop="eventName" label="业务事件" />
          <el-table-column label="推送状态">
            <template #default="{ row }"
              ><el-switch
                :model-value="row.enabled"
                :loading="savingEvent === row.eventCode"
                @change="toggleEvent(row, $event)"
            /></template>
          </el-table-column>
          <el-table-column prop="templateContent" label="消息模板" show-overflow-tooltip />
          <el-table-column label="可用变量"
            ><template #default="{ row }">{{ row.variables.length }} 个</template></el-table-column
          >
          <el-table-column label="操作"
            ><template #default="{ row }"
              ><el-button link type="primary" @click="editEvent(row)">编辑模板</el-button></template
            ></el-table-column
          >
        </el-table>
        <el-empty v-else description="请选择一个机器人配置事件" />
      </el-tab-pane>

      <el-tab-pane label="发送记录" name="logs">
        <div class="log-filters">
          <el-select v-model="logQuery.robotId" clearable placeholder="全部机器人" @change="searchLogs"
            ><el-option v-for="item in robots" :key="item.id" :label="item.name" :value="item.id"
          /></el-select>
          <el-select v-model="logQuery.eventCode" clearable placeholder="全部事件" @change="searchLogs"
            ><el-option
              v-for="item in eventDefinitions"
              :key="item.eventCode"
              :label="item.name"
              :value="item.eventCode"
          /></el-select>
          <el-select v-model="logQuery.status" clearable placeholder="全部状态" @change="searchLogs"
            ><el-option
              v-for="item in statusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
          /></el-select>
          <el-button type="primary" :icon="Search" @click="searchLogs">查询</el-button>
        </div>
        <el-table v-loading="logsLoading" :data="logs" border table-layout="auto">
          <el-table-column prop="createdAt" label="发生时间"
            ><template #default="{ row }">{{
              formatDate(row.createdAt)
            }}</template></el-table-column
          >
          <el-table-column label="事件"
            ><template #default="{ row }">{{
              eventName(row.event.eventCode)
            }}</template></el-table-column
          >
          <el-table-column label="业务编号"
            ><template #default="{ row }">{{ row.event.businessId }}</template></el-table-column
          >
          <el-table-column label="机器人"
            ><template #default="{ row }">{{ row.robot.name }}</template></el-table-column
          >
          <el-table-column label="平台"
            ><template #default="{ row }">{{
              platformName(row.platform)
            }}</template></el-table-column
          >
          <el-table-column label="状态"
            ><template #default="{ row }"
              ><el-tag :type="statusType(row.status)">{{
                statusName(row.status)
              }}</el-tag></template
            ></el-table-column
          >
          <el-table-column prop="attemptCount" label="发送次数" />
          <el-table-column prop="errorMessage" label="失败原因" show-overflow-tooltip />
          <el-table-column label="操作">
            <template #default="{ row }"
              ><el-button link type="primary" @click="showLog(row.id)">详情</el-button
              ><el-button
                v-if="row.status === ROBOT_DELIVERY_STATUS.FAILED"
                link
                type="danger"
                @click="retryLog(row)"
                >重试</el-button
              ></template
            >
          </el-table-column>
        </el-table>
        <Pagination
          v-model:page="logQuery.page"
          v-model:page-size="logQuery.pageSize"
          :total="logTotal"
          @update:page="loadLogs"
          @update:page-size="searchLogs"
        />
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      v-model="robotDialogVisible"
      :title="robotForm.id ? '编辑群机器人' : '新增群机器人'"
      width="620px"
    >
      <el-form label-position="top" :model="robotForm">
        <div class="form-grid">
          <el-form-item label="机器人名称"
            ><el-input v-model.trim="robotForm.name" maxlength="100"
          /></el-form-item>
          <el-form-item label="平台"
            ><el-select v-model="robotForm.platform" class="full-width"
              ><el-option label="企业微信" value="wecom" /><el-option
                label="钉钉"
                value="dingtalk" /><el-option label="飞书" value="feishu" /></el-select
          ></el-form-item>
          <el-form-item v-if="userStore.isSuperAdmin" label="归属"
            ><el-segmented v-model="robotForm.scopeType" :options="scopeOptions"
          /></el-form-item>
          <el-form-item
            v-if="userStore.isSuperAdmin && robotForm.scopeType === 'STORE'"
            label="所属门店"
            ><el-select v-model="robotForm.storeId" class="full-width" filterable
              ><el-option
                v-for="item in stores"
                :key="item.id"
                :label="item.name"
                :value="item.id" /></el-select
          ></el-form-item>
        </div>
        <el-form-item label="Webhook"
          ><el-input
            v-model.trim="robotForm.webhook"
            type="password"
            show-password
            :placeholder="
              robotForm.webhookConfigured ? '已配置，留空不修改' : '请输入官方机器人 Webhook'
            "
        /></el-form-item>
        <el-form-item label="签名 Secret"
          ><el-input
            v-model.trim="robotForm.secret"
            type="password"
            show-password
            :placeholder="
              robotForm.secretConfigured ? '已配置，留空不修改' : '可选，钉钉或飞书加签密钥'
            "
        /></el-form-item>
        <el-form-item label="备注"
          ><el-input v-model="robotForm.remark" type="textarea" :rows="2" maxlength="500"
        /></el-form-item>
        <el-form-item label="启用机器人"><el-switch v-model="robotForm.enabled" /></el-form-item>
      </el-form>
      <template #footer
        ><el-button @click="robotDialogVisible = false">取消</el-button
        ><el-button type="primary" :loading="savingRobot" @click="saveRobot"
          >保存</el-button
        ></template
      >
    </el-dialog>

    <el-dialog
      v-model="eventDialogVisible"
      :title="`编辑模板 · ${eventForm.eventName || ''}`"
      width="720px"
    >
      <el-form label-position="top">
        <el-form-item label="推送状态"><el-switch v-model="eventForm.enabled" /></el-form-item>
        <el-form-item label="消息内容"
          ><el-input
            ref="templateInput"
            v-model="eventForm.templateContent"
            type="textarea"
            :rows="11"
            maxlength="4000"
            show-word-limit
        /></el-form-item>
        <el-form-item label="可用变量"
          ><div class="variable-list">
            <el-button
              v-for="variable in eventForm.variables"
              :key="variable.key"
              size="small"
              @click="insertVariable(variable.key)"
              >{{ variable.label }}</el-button
            >
          </div></el-form-item
        >
        <el-form-item label="示例预览">
          <pre class="template-preview">{{ eventPreview }}</pre>
        </el-form-item>
      </el-form>
      <template #footer
        ><el-button :icon="RefreshLeft" @click="restoreTemplate">恢复初始模板</el-button
        ><el-button @click="eventDialogVisible = false">取消</el-button
        ><el-button type="primary" :loading="savingEvent === eventForm.eventCode" @click="saveEvent"
          >保存</el-button
        ></template
      >
    </el-dialog>

    <el-dialog v-model="testDialogVisible" title="发送测试消息" width="460px">
      <el-form label-position="top"
        ><el-form-item label="测试机器人"
          ><el-input :model-value="testRobotTarget?.name" disabled /></el-form-item
        ><el-form-item label="测试事件"
          ><el-select v-model="testEventCode" class="full-width"
            ><el-option
              v-for="item in testRobotTarget?.events || []"
              :key="item.eventCode"
              :label="item.eventName"
              :value="item.eventCode" /></el-select></el-form-item
      ></el-form>
      <template #footer
        ><el-button @click="testDialogVisible = false">取消</el-button
        ><el-button type="primary" :loading="testing" @click="sendTest">发送</el-button></template
      >
    </el-dialog>

    <el-dialog v-model="logDialogVisible" title="发送记录详情" width="720px">
      <div v-if="logDetail" class="log-detail">
        <div><span>事件</span>{{ eventName(logDetail.event.eventCode) }}</div>
        <div><span>机器人</span>{{ logDetail.robot.name }}</div>
        <div><span>状态</span>{{ statusName(logDetail.status) }}</div>
        <div><span>发送次数</span>{{ logDetail.attemptCount }}</div>
        <section>
          <span>发送内容</span>
          <pre>{{ logDetail.renderedContent }}</pre>
        </section>
        <section v-if="logDetail.errorMessage">
          <span>失败原因</span>
          <p>{{ logDetail.errorMessage }}</p>
        </section>
        <section v-if="logDetail.providerResponse">
          <span>平台响应</span>
          <pre>{{ logDetail.providerResponse }}</pre>
        </section>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Plus, Refresh, RefreshLeft, Search } from '@element-plus/icons-vue';
import Pagination from '@/components/Pagination.vue';
import { getStores } from '@/api/store';
import {
  createRobot,
  deleteRobot,
  getRobotLog,
  getRobotLogs,
  getRobotNotifications,
  resetRobotEvent,
  retryRobotLog,
  testRobot,
  updateRobot,
  updateRobotEvent
} from '@/api/robotNotification';
import { useUserStore } from '@/stores/user';
import { formatDate } from '@/utils/date';
import { ROBOT_DELIVERY_STATUS } from '@/constants/robotNotification';

const userStore = useUserStore();
const activeTab = ref('robots');
const loading = ref(false);
const logsLoading = ref(false);
const robots = ref([]);
const stores = ref([]);
const eventDefinitions = ref([]);
const selectedRobotId = ref(null);
const selectedRobot = computed(
  () => robots.value.find((item) => item.id === selectedRobotId.value) || null
);
const eventPreview = computed(() => {
  const examples = {
    eventTime: '2026-07-24 15:30:00',
    operatorName: '李四',
    storeName: '园区店',
    packageId: '1001',
    pickupCode: '123456',
    receiverName: '张三',
    receiverPhoneMasked: '138****8000',
    itemName: '中药包裹',
    itemInfo: '代煎 7 剂',
    pickupMethod: '自提',
    createdAt: '2026-07-24 15:20:00',
    verifiedAt: '2026-07-24 15:30:00',
    planId: '2001',
    prescriptionNo: 'CF202607240001',
    customerName: '张三',
    processType: '代煎',
    totalDose: '7',
    bagCount: '14',
    notifyType: '微信',
    finishTime: '2026-07-24 15:30:00',
    transferId: '3001',
    transferNo: 'DB202607240001',
    fromStoreName: '中心店',
    toStoreName: '园区店',
    itemCount: '2',
    itemSummary: '黄芪 5kg、党参 3kg',
    transferDate: '2026-07-24',
    expectedReturnDate: '2026-08-24',
    remark: '门店补货'
  };
  return eventForm.templateContent.replace(
    /\{\{\s*([A-Za-z][A-Za-z0-9]*)\s*\}\}/g,
    (_match, key) => examples[key] || '-'
  );
});
const robotDialogVisible = ref(false);
const eventDialogVisible = ref(false);
const testDialogVisible = ref(false);
const logDialogVisible = ref(false);
const savingRobot = ref(false);
const savingEvent = ref('');
const testing = ref(false);
const templateInput = ref(null);
const testRobotTarget = ref(null);
const testEventCode = ref('PACKAGE_CREATED');
const logs = ref([]);
const logTotal = ref(0);
const logDetail = ref(null);
const scopeOptions = [
  { label: '总部', value: 'HEADQUARTERS' },
  { label: '门店', value: 'STORE' }
];
const statusOptions = [
  { label: '等待发送', value: ROBOT_DELIVERY_STATUS.PENDING },
  { label: '发送中', value: ROBOT_DELIVERY_STATUS.SENDING },
  { label: '等待重试', value: ROBOT_DELIVERY_STATUS.RETRYING },
  { label: '成功', value: ROBOT_DELIVERY_STATUS.SUCCESS },
  { label: '失败', value: ROBOT_DELIVERY_STATUS.FAILED }
];
const robotForm = reactive({
  id: null,
  name: '',
  platform: 'wecom',
  scopeType: 'STORE',
  storeId: null,
  webhook: '',
  secret: '',
  enabled: false,
  remark: '',
  webhookConfigured: false,
  secretConfigured: false
});
const eventForm = reactive({
  eventCode: '',
  eventName: '',
  enabled: false,
  templateContent: '',
  variables: []
});
const logQuery = reactive({ page: 1, pageSize: 20, robotId: null, eventCode: '', status: '' });

function platformName(value) {
  return { wecom: '企业微信', dingtalk: '钉钉', feishu: '飞书' }[value] || value;
}
function eventName(code) {
  return eventDefinitions.value.find((item) => item.eventCode === code)?.name || code;
}
function statusName(value) {
  return statusOptions.find((item) => item.value === value)?.label || value;
}
function statusType(value) {
  return (
    {
      [ROBOT_DELIVERY_STATUS.SUCCESS]: 'success',
      [ROBOT_DELIVERY_STATUS.FAILED]: 'danger',
      [ROBOT_DELIVERY_STATUS.SENDING]: 'warning',
      [ROBOT_DELIVERY_STATUS.RETRYING]: 'warning'
    }[value] || 'info'
  );
}

async function loadRobots() {
  loading.value = true;
  try {
    const data = await getRobotNotifications();
    robots.value = data.robots || [];
    eventDefinitions.value = data.eventDefinitions || [];
    if (!selectedRobot.value) selectedRobotId.value = robots.value[0]?.id || null;
  } finally {
    loading.value = false;
  }
}
async function loadStores() {
  if (userStore.isSuperAdmin) {
    const data = await getStores({ page: 1, pageSize: 100, status: 1 });
    stores.value = data.list || [];
  }
}
async function loadLogs() {
  logsLoading.value = true;
  try {
    const data = await getRobotLogs(logQuery);
    logs.value = data.list || [];
    logTotal.value = data.pagination?.total || 0;
  } finally {
    logsLoading.value = false;
  }
}
function searchLogs() {
  logQuery.page = 1;
  loadLogs();
}
function handleTabChange(name) {
  if (name === 'logs') loadLogs();
}
function refreshCurrent() {
  if (activeTab.value === 'logs') loadLogs();
  else loadRobots();
}
function syncSelectedRobot() {}
function selectRobotEvents(row) {
  selectedRobotId.value = row.id;
  activeTab.value = 'events';
}

function openRobotDialog(row = null) {
  Object.assign(
    robotForm,
    row
      ? { ...row, webhook: '', secret: '' }
      : {
          id: null,
          name: '',
          platform: 'wecom',
          scopeType: userStore.isSuperAdmin ? 'HEADQUARTERS' : 'STORE',
          storeId: userStore.user?.storeId || null,
          webhook: '',
          secret: '',
          enabled: false,
          remark: '',
          webhookConfigured: false,
          secretConfigured: false
        }
  );
  robotDialogVisible.value = true;
}
async function saveRobot() {
  savingRobot.value = true;
  try {
    const payload = { ...robotForm };
    if (!payload.webhook) delete payload.webhook;
    if (!payload.secret) delete payload.secret;
    if (robotForm.id) await updateRobot(robotForm.id, payload);
    else await createRobot(payload);
    ElMessage.success('群机器人已保存');
    robotDialogVisible.value = false;
    await loadRobots();
  } finally {
    savingRobot.value = false;
  }
}
async function removeRobot(row) {
  await ElMessageBox.confirm(`确认删除机器人“${row.name}”？历史发送记录会保留。`, '删除机器人', {
    type: 'warning'
  });
  await deleteRobot(row.id);
  ElMessage.success('群机器人已删除');
  await loadRobots();
}
function openTestDialog(row) {
  testRobotTarget.value = row;
  testEventCode.value = row.events[0]?.eventCode || '';
  testDialogVisible.value = true;
}
async function sendTest() {
  testing.value = true;
  try {
    await testRobot(testRobotTarget.value.id, { eventCode: testEventCode.value });
    ElMessage.success('测试消息已发送');
    testDialogVisible.value = false;
  } finally {
    testing.value = false;
  }
}

async function toggleEvent(row, enabled) {
  savingEvent.value = row.eventCode;
  try {
    await updateRobotEvent(selectedRobot.value.id, row.eventCode, { enabled });
    row.enabled = enabled;
    ElMessage.success('事件状态已保存');
  } finally {
    savingEvent.value = '';
  }
}
function editEvent(row) {
  Object.assign(eventForm, { ...row, variables: [...row.variables] });
  eventDialogVisible.value = true;
}
async function saveEvent() {
  savingEvent.value = eventForm.eventCode;
  try {
    await updateRobotEvent(selectedRobot.value.id, eventForm.eventCode, {
      enabled: eventForm.enabled,
      templateContent: eventForm.templateContent
    });
    ElMessage.success('事件模板已保存');
    eventDialogVisible.value = false;
    await loadRobots();
  } finally {
    savingEvent.value = '';
  }
}
async function restoreTemplate() {
  await ElMessageBox.confirm('确认恢复该事件的初始模板？当前内容将被替换。', '恢复模板', {
    type: 'warning'
  });
  const data = await resetRobotEvent(selectedRobot.value.id, eventForm.eventCode);
  eventForm.templateContent = data.templateContent;
  ElMessage.success('已恢复初始模板');
}
async function insertVariable(key) {
  const token = `{{${key}}}`;
  const textarea = templateInput.value?.textarea;
  const start = textarea?.selectionStart ?? eventForm.templateContent.length;
  eventForm.templateContent = `${eventForm.templateContent.slice(0, start)}${token}${eventForm.templateContent.slice(start)}`;
  await nextTick();
  textarea?.focus();
  textarea?.setSelectionRange(start + token.length, start + token.length);
}

async function showLog(id) {
  logDetail.value = await getRobotLog(id);
  logDialogVisible.value = true;
}
async function retryLog(row) {
  await ElMessageBox.confirm('确认重新发送这条失败通知？', '重新发送', { type: 'warning' });
  await retryRobotLog(row.id);
  ElMessage.success('通知已重新排队');
  await loadLogs();
}

onMounted(async () => {
  await Promise.all([loadRobots(), loadStores()]);
});
</script>

<style scoped>
.header-actions,
.event-toolbar,
.log-filters,
.variable-list {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.notification-tabs {
  padding: 0 16px 16px;
  border: 1px solid var(--app-border);
  background: #fff;
}
.event-toolbar,
.log-filters {
  padding: 4px 0 16px;
}
.event-toolbar .el-select,
.log-filters .el-select {
  width: 230px;
}
.toolbar-label {
  color: var(--app-muted);
  font-size: 14px;
}
.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}
.full-width {
  width: 100%;
}
.template-preview,
.log-detail pre {
  width: 100%;
  margin: 0;
  padding: 12px;
  overflow: auto;
  border: 1px solid var(--app-border);
  background: #f8fafc;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  line-height: 1.7;
}
.log-detail {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}
.log-detail div,
.log-detail section {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}
.log-detail section {
  grid-column: 1 / -1;
}
.log-detail span {
  color: var(--app-muted);
  font-size: 13px;
}
.log-detail p {
  margin: 0;
  word-break: break-word;
}
@media (max-width: 768px) {
  .header-actions {
    width: 100%;
  }
  .header-actions .el-button {
    flex: 1;
  }
  .form-grid,
  .log-detail {
    grid-template-columns: 1fr;
  }
  .event-toolbar .el-select,
  .log-filters .el-select {
    width: 100%;
  }
}
</style>
