<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">加工工作台</h1>
        <p class="page-subtitle">按日期、通知状态与加工顺序统一调度任务</p>
      </div>
      <div class="page-actions">
        <el-radio-group v-model="mode" @change="changeMode">
          <el-radio-button value="list">列表</el-radio-button>
          <el-radio-button value="calendar">日历</el-radio-button>
          <el-radio-button value="pickup">待领取</el-radio-button>
        </el-radio-group>
        <el-button v-if="canEditQueue" :icon="RefreshRight" @click="restoreQueue">
          恢复默认排序
        </el-button>
        <el-button v-if="mode !== 'pickup'" type="primary" :icon="Plus" @click="openCreate">
          新建计划
        </el-button>
      </div>
    </div>

    <div class="stat-grid">
      <div class="stat-action" @click="selectView('today-all')">
        <StatisticCard
          label="今日全部"
          :value="todayAllCount"
          icon="DataLine"
          type="primary"
          size="compact"
        />
      </div>
      <div class="stat-action" @click="selectView('today-waiting')">
        <StatisticCard
          label="今日待加工"
          :value="stats.waitingCount"
          icon="List"
          type="primary"
          size="compact"
        />
      </div>
      <div class="stat-action" @click="selectView('overdue')">
        <StatisticCard
          label="逾期未开工"
          :value="stats.overdueCount"
          icon="Clock"
          type="warning"
          size="compact"
        />
      </div>
      <div class="stat-action" @click="selectView('processing')">
        <StatisticCard
          label="加工中"
          :value="stats.processingCount"
          icon="Loading"
          type="primary"
          size="compact"
        />
      </div>
      <div class="stat-action" @click="selectView('today-finished')">
        <StatisticCard
          label="已完成"
          :value="stats.todayFinished"
          icon="CircleCheck"
          type="success"
          size="compact"
        />
      </div>
      <div class="stat-action" @click="selectView('urgent')">
        <StatisticCard
          label="加急任务"
          :value="stats.urgentCount"
          icon="Warning"
          type="warning"
          size="compact"
        />
      </div>
      <div class="stat-action" @click="selectView('notice')">
        <StatisticCard
          label="等待顾客"
          :value="stats.waitingNoticeCount"
          icon="Bell"
          type="info"
          size="compact"
        />
      </div>
      <div class="stat-action" @click="selectView('tomorrow')">
        <StatisticCard
          label="明日加工"
          :value="stats.tomorrowWaitingCount"
          icon="Calendar"
          type="info"
          size="compact"
        />
      </div>
      <div class="stat-action" @click="selectView('all')">
        <StatisticCard
          label="全部"
          :value="stats.processingPlanTotalCount"
          icon="Tickets"
          type="primary"
          size="compact"
        />
      </div>
    </div>

    <template v-if="mode === 'list'">
      <el-card v-if="activeView === 'all'" shadow="never">
        <el-form class="filters" inline @submit.prevent>
          <el-form-item label="搜索">
            <el-input v-model.trim="query.keyword" clearable placeholder="姓名、手机号或备注" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.status" clearable placeholder="全部状态" @change="search">
              <el-option
                v-for="item in statuses"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="加工方式">
            <el-select v-model="query.processTypeId" clearable placeholder="全部方式" @change="search">
              <el-option
                v-for="item in processTypes"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="医生">
            <el-select v-model="query.doctorId" clearable filterable placeholder="全部医生" @change="search">
              <el-option
                v-for="item in doctors"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item v-if="userStore.isSuperAdmin" label="门店">
            <el-select v-model="query.storeId" clearable placeholder="全部门店" @change="search">
              <el-option
                v-for="item in stores"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="search">查询</el-button>
            <el-button @click="resetFilters">重置</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <el-card shadow="never">
        <PlanTable
          :rows="list"
          :loading="loading"
          :show-store="userStore.isSuperAdmin"
          :queue-editable="canEditQueue"
          :view="activeView"
          @queue-change="saveManualQueue"
          @action="handleAction"
        />
        <Pagination
          v-model:page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
        />
      </el-card>
    </template>

    <el-card v-else-if="mode === 'calendar'" shadow="never" class="calendar-workbench">
      <div class="calendar-layout">
        <el-calendar v-model="calendarDate">
          <template #date-cell="{ data }">
            <div class="calendar-cell">
              <span>{{ Number(data.day.slice(-2)) }}</span>
              <strong v-if="calendarCounts[data.day]">{{ calendarCounts[data.day] }}</strong>
            </div>
          </template>
        </el-calendar>
        <section class="calendar-day-plans">
          <div class="section-heading">
            <div>
              <h2>{{ selectedDateText }}</h2>
              <span>共 {{ calendarList.length }} 项加工计划</span>
            </div>
            <el-button :icon="Refresh" circle title="刷新" @click="loadCalendarDay" />
          </div>
          <PlanTable
            :rows="calendarList"
            :loading="calendarLoading"
            :show-store="userStore.isSuperAdmin"
            @action="handleAction"
          />
        </section>
      </div>
    </el-card>

    <ReadyPickup
      v-else
      embedded
      @detail="openPickupPackageDrawer('detail', $event)"
      @verify="openPickupPackageDrawer('verify', $event)"
    />

    <el-drawer v-model="pickupDrawerVisible" size="min(720px, 96vw)" destroy-on-close>
      <template #header>
        <div class="drawer-header">
          <span>{{ pickupDrawerTitle }}</span>
          <el-button
            v-if="pickupDrawerMode === 'detail' && pickupPackageId"
            type="primary"
            size="small"
            :icon="Printer"
            @click="printPickupPackageLabel"
          >
            打印标签
          </el-button>
        </div>
      </template>
      <PackageDetail
        v-if="pickupDrawerMode === 'detail'"
        :key="`pickup-detail-${pickupPackageId}`"
        :id="pickupPackageId"
        ref="pickupPackageDetailRef"
        embedded
        @edit="openPickupPackageDrawer('edit', $event)"
        @verify="openPickupPackageDrawer('verify', $event)"
      />
      <PackageEdit
        v-else-if="pickupDrawerMode === 'edit'"
        :key="`pickup-edit-${pickupPackageId}`"
        :id="pickupPackageId"
        embedded
        @saved="handlePickupDrawerSaved"
        @cancel="closePickupPackageDrawer"
      />
      <Verify
        v-else-if="pickupDrawerMode === 'verify'"
        :key="`pickup-verify-${pickupPackageCode || 'empty'}`"
        :initial-pickup-code="pickupPackageCode"
        embedded
        @success="handlePickupDrawerVerified"
      />
    </el-drawer>

    <el-drawer v-model="detailVisible" size="min(1000px, 96vw)" destroy-on-close>
      <template #header>
        <div class="drawer-header">
          <span>加工计划详情</span>
          <el-dropdown @command="openPlanPrint">
            <el-button type="primary" size="small" :icon="Printer">打印标签</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="processing">打印加工标签</el-dropdown-item>
                <el-dropdown-item command="packaging">打印包装标签</el-dropdown-item>
                <el-dropdown-item command="pickup">打印取货标签</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </template>
      <div v-loading="detailLoading">
        <el-descriptions v-if="detailPlan" :column="2" border>
          <el-descriptions-item label="加工码">
            {{ detailPlan.planCode || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="当前工序">
            {{ workflowStageText(detailPlan.currentStage) }}
          </el-descriptions-item>
          <el-descriptions-item label="处方编号">
            {{ detailPlan.prescription?.prescriptionNo || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="顾客">
            {{ detailPlan.prescription?.customerName || '-' }}
            {{ detailPlan.prescription?.phone || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="医生">
            {{ detailPlan.prescription?.doctor?.name || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="门店">{{
            detailPlan.store?.name || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="批次">第 {{ detailPlan.batchNo }} 批</el-descriptions-item>
          <el-descriptions-item label="加工方式">
            {{ detailPlan.processType?.name || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="取货方式">
            {{ pickupMethodText(detailPlan.pickupMethod) }}
          </el-descriptions-item>
          <el-descriptions-item
            v-if="[1, 2].includes(Number(detailPlan.pickupMethod))"
            label="地址"
          >
            {{ detailPlan.expressAddress || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="剂数">{{ detailPlan.totalDose }} 剂</el-descriptions-item>
          <el-descriptions-item v-if="isDecoctionPlan(detailPlan)" label="袋数">
            {{ detailPlan.bagCount }} 袋
          </el-descriptions-item>
          <el-descriptions-item v-if="isDecoctionPlan(detailPlan)" label="毫升数">
            {{ detailPlan.volumeMl }} ml
          </el-descriptions-item>
          <el-descriptions-item label="服用方法" :span="2">
            {{ detailPlan.usageMethod || '遵医嘱' }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType[detailPlan.status] || 'info'">
              {{ statusMap[detailPlan.status] || detailPlan.status }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="优先级">
            <el-tag v-if="detailPlan.priority === PRIORITY.URGENT" type="danger" effect="dark">
              【加急】
            </el-tag>
            <span v-else>普通</span>
          </el-descriptions-item>
          <el-descriptions-item label="加工顺序">
            {{
              detailPlan.queueOrder == null ? '-' : String(detailPlan.queueOrder).padStart(3, '0')
            }}
          </el-descriptions-item>
          <el-descriptions-item label="调度方式">
            {{ detailPlan.scheduleType === SCHEDULE_TYPES.NOTICE ? '等待顾客通知' : '指定日期' }}
          </el-descriptions-item>
          <el-descriptions-item label="计划开工日期">
            {{ detailPlan.processDate ? String(detailPlan.processDate).slice(0, 10) : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="开始加工时间">
            {{ formatDate(detailPlan.startDate) }}
          </el-descriptions-item>
          <el-descriptions-item label="完成加工时间">
            {{ formatDate(detailPlan.finishDate) }}
          </el-descriptions-item>
          <el-descriptions-item label="提醒方式">
            {{ notifyTypeText(detailPlan.notifyType) }}
          </el-descriptions-item>
          <el-descriptions-item label="通知状态">
            {{ detailPlan.notifyStatus === NOTIFY_STATUS.NOTIFIED ? '已通知' : '未通知' }}
            <span v-if="detailPlan.notifyTime">（{{ formatDate(detailPlan.notifyTime) }}）</span>
          </el-descriptions-item>
          <el-descriptions-item label="收费状态">
            {{ detailPlan.paymentStatus === PAYMENT_STATUS.PAID ? '已收费' : '未收费' }}
          </el-descriptions-item>
          <el-descriptions-item label="取货码">
            {{ formatPickupCode(detailPlan.pickupCode || detailPlan.package?.pickupCode) || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="加工备注" :span="2">
            {{ detailPlan.processRemark || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="其它备注" :span="2">
            {{ detailPlan.remark || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="创建时间" :span="2">
            {{ formatDate(detailPlan.createdAt) }}
          </el-descriptions-item>
        </el-descriptions>

        <section v-if="detailPlan" class="workflow-detail-section">
          <div class="workflow-detail-heading">
            <h3>调配核对照片</h3>
            <div class="workflow-photo-tools">
              <span>{{ detailPlan.photos?.length || 0 }} 张</span>
              <el-upload
                v-if="canUploadDetailPhoto"
                ref="detailPhotoUploadRef"
                accept="image/jpeg,image/png,image/webp"
                :auto-upload="false"
                :show-file-list="false"
                :on-change="handleDetailPhotoUpload"
              >
                <el-button size="small" :icon="Upload" :loading="detailPhotoUploading">
                  上传照片
                </el-button>
              </el-upload>
            </div>
          </div>
          <div v-if="detailPhotoUrls.length" class="workflow-photo-grid">
            <div
              v-for="(photo, index) in detailPhotoUrls"
              :key="photo.id"
              class="workflow-photo-item"
            >
              <el-image
                :src="photo.url"
                :preview-src-list="detailPhotoUrls.map((item) => item.url)"
                :initial-index="index"
                fit="cover"
                preview-teleported
              />
              <el-tooltip v-if="canManageDetailPhotos" content="删除照片" placement="top">
                <el-button
                  class="workflow-photo-delete"
                  type="danger"
                  circle
                  size="small"
                  :icon="Delete"
                  :disabled="detailPhotoUploading"
                  @click="removeDetailPhoto(photo.id)"
                />
              </el-tooltip>
            </div>
          </div>
          <el-empty v-else :image-size="64" description="尚未上传调配照片" />
        </section>

        <section v-if="detailPlan?.isDecoction" class="workflow-detail-section">
          <div class="workflow-detail-heading">
            <h3>设备工序记录</h3>
            <div class="workflow-heading-actions">
              <span>浸泡、煎煮及打包扫码记录</span>
              <el-button
                v-if="Number(detailPlan.status) === PROCESSING_STATUS.PROCESSING"
                size="small"
                type="primary"
                plain
                @click="openManualUsage"
              >
                补录工序
              </el-button>
            </div>
          </div>
          <el-table
            v-if="detailPlan.equipmentUsages?.length"
            :data="detailPlan.equipmentUsages"
            border
          >
            <el-table-column label="工序" min-width="80">
              <template #default="{ row }">{{ usageStageText(row.stage) }}</template>
            </el-table-column>
            <el-table-column prop="portionNo" label="分组" width="82">
              <template #default="{ row }">第 {{ row.portionNo }} 组</template>
            </el-table-column>
            <el-table-column label="设备" min-width="160">
              <template #default="{ row }">
                {{ row.equipment?.equipmentNo }} · {{ row.equipment?.name }}
              </template>
            </el-table-column>
            <el-table-column label="操作人" min-width="100">
              <template #default="{ row }">
                {{ row.operator?.nickname || row.operator?.name || row.operator?.phone || '-' }}
              </template>
            </el-table-column>
            <el-table-column label="记录状态" min-width="110">
              <template #default="{ row }">
                <el-tag :type="usageStatusTag(row.status)" effect="plain">
                  {{ usageStatusText(row.status) }}
                </el-tag>
                <div class="secondary-text">{{ usageSourceText(row.source) }}</div>
              </template>
            </el-table-column>
            <el-table-column label="开始时间" min-width="155">
              <template #default="{ row }">{{ formatDate(row.startedAt) }}</template>
            </el-table-column>
            <el-table-column label="结束时间" min-width="155">
              <template #default="{ row }">{{
                row.endedAt ? formatDate(row.endedAt) : '进行中'
              }}</template>
            </el-table-column>
            <el-table-column label="用时" min-width="100">
              <template #default="{ row }">{{
                workflowDuration(row.startedAt, row.endedAt)
              }}</template>
            </el-table-column>
          </el-table>
          <el-empty v-else :image-size="64" description="尚无设备工序记录" />
        </section>

        <section v-if="detailPlan?.workflowExceptions?.length" class="workflow-detail-section">
          <div class="workflow-detail-heading">
            <h3>异常处理记录</h3>
            <span>{{ detailPlan.workflowExceptions.length }} 条</span>
          </div>
          <el-timeline>
            <el-timeline-item
              v-for="item in detailPlan.workflowExceptions"
              :key="item.id"
              :timestamp="formatDate(item.createdAt)"
              type="warning"
            >
              <strong>{{ workflowExceptionTypeText(item.type) }}</strong>
              <div>{{ item.reason }}</div>
              <div class="secondary-text">
                操作人：{{
                  item.creator?.nickname || item.creator?.name || item.creator?.phone || '-'
                }}
              </div>
            </el-timeline-item>
          </el-timeline>
        </section>
      </div>
    </el-drawer>

    <el-dialog
      v-model="manualUsageVisible"
      title="人工补录设备工序"
      width="min(560px, 94vw)"
      destroy-on-close
    >
      <el-alert
        title="补录会永久标记为人工记录，并写入异常处理日志。"
        type="warning"
        :closable="false"
        show-icon
      />
      <el-form class="manual-usage-form" :model="manualUsageForm" label-width="90px">
        <el-form-item label="工序" required>
          <el-select v-model="manualUsageForm.stage" @change="manualUsageForm.equipmentId = null">
            <el-option label="浸泡" :value="3" />
            <el-option label="煎煮" :value="4" />
            <el-option label="打包" :value="5" />
          </el-select>
        </el-form-item>
        <el-form-item label="分组" required>
          <el-input-number v-model="manualUsageForm.portionNo" :min="1" :max="99" />
        </el-form-item>
        <el-form-item label="设备" required>
          <el-select v-model="manualUsageForm.equipmentId" filterable placeholder="请选择设备">
            <el-option
              v-for="item in manualUsageEquipmentOptions"
              :key="item.id"
              :label="`${item.equipmentNo} · ${item.name}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="开始时间" required>
          <el-date-picker
            v-model="manualUsageForm.startedAt"
            type="datetime"
            placeholder="请选择开始时间"
          />
        </el-form-item>
        <el-form-item label="结束时间" required>
          <el-date-picker
            v-model="manualUsageForm.endedAt"
            type="datetime"
            placeholder="请选择结束时间"
          />
        </el-form-item>
        <el-form-item label="补录原因" required>
          <el-input
            v-model.trim="manualUsageForm.reason"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            placeholder="请说明漏扫原因"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="manualUsageVisible = false">取消</el-button>
        <el-button type="primary" :loading="manualUsageSaving" @click="submitManualUsage">
          确认补录
        </el-button>
      </template>
    </el-dialog>

    <ProcessingPrintDialog
      v-model="planPrintVisible"
      :plan-info="detailPlan"
      :template-type="planPrintType"
      @usage-method-saved="handleUsageMethodSaved"
    />

    <el-dialog
      v-model="finishDialogVisible"
      title="是否生成待取包裹"
      width="min(520px, 92vw)"
      :close-on-click-modal="!finishing"
      :close-on-press-escape="!finishing"
      :show-close="!finishing"
    >
      <p class="finish-dialog-text">
        确认“{{ finishingPlan?.prescription?.customerName || '该顾客' }}”的{{
          finishingPlan?.processType?.name || '加工计划'
        }}
        {{ finishingPlan?.totalDose || 0 }} 剂已经加工完成。是否立即生成待取包裹？
      </p>
      <p class="finish-dialog-tip">选择“不生成”仍会确认加工完成，之后可在操作栏点击“生成包裹”。</p>
      <template #footer>
        <el-button :disabled="finishing" @click="finishDialogVisible = false">取消</el-button>
        <el-button :loading="finishing" @click="completePlan(false)">不生成</el-button>
        <el-button type="primary" :loading="finishing" @click="completePlan(true)">
          生成包裹
        </el-button>
      </template>
    </el-dialog>

    <el-drawer
      v-model="formVisible"
      :title="editingId ? '编辑加工计划' : '新建加工任务'"
      direction="rtl"
      size="min(1180px, 96vw)"
      destroy-on-close
    >
      <el-form v-if="editingId" :model="form" label-width="100px">
        <el-form-item label="处方" required>
          <el-select
            v-model="form.prescriptionId"
            filterable
            remote
            reserve-keyword
            :remote-method="loadPrescriptionOptions"
            :loading="prescriptionLoading"
            :disabled="metadataOnlyEdit"
            style="width: 100%"
            placeholder="输入处方编号、姓名或手机号搜索"
            @visible-change="onPrescriptionVisibleChange"
          >
            <el-option
              v-for="item in prescriptions"
              :key="item.id"
              :label="`${item.prescriptionNo} · ${item.customerName} · ${item.phone || '未留手机号'}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="加工方式" required>
            <el-select
              v-model="form.processTypeId"
              :disabled="metadataOnlyEdit"
              placeholder="请选择"
            >
              <el-option
                v-for="item in processTypes"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="取货方式" required>
            <el-select
              v-model="form.pickupMethod"
              :disabled="metadataOnlyEdit"
              placeholder="请选择取货方式"
            >
              <el-option
                v-for="item in PICKUP_METHOD_OPTIONS"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item v-if="[1, 2].includes(Number(form.pickupMethod))" label="地址">
            <el-input
              v-model.trim="form.expressAddress"
              type="textarea"
              :rows="2"
              maxlength="500"
              show-word-limit
              placeholder="选填"
            />
          </el-form-item>
          <el-form-item label="批次号" required>
            <el-input-number
              v-model="form.batchNo"
              :min="1"
              :max="999"
              :disabled="metadataOnlyEdit"
            />
          </el-form-item>
          <el-form-item label="剂数" required>
            <el-input-number
              v-model="form.totalDose"
              :min="1"
              :max="999"
              :disabled="metadataOnlyEdit"
            />
          </el-form-item>
          <el-form-item v-if="formIsDecoction" label="袋数" required>
            <el-input-number
              v-model="form.bagCount"
              :min="1"
              :max="9999"
              :disabled="metadataOnlyEdit"
            />
          </el-form-item>
          <el-form-item v-if="formIsDecoction" label="毫升数" required>
            <el-input-number
              v-model="form.volumeMl"
              :min="1"
              :max="99999"
              :disabled="metadataOnlyEdit"
            />
          </el-form-item>
          <el-form-item label="服用方法" class="form-item-wide">
            <UsageMethodInput v-model="form.usageMethod" />
          </el-form-item>
          <el-form-item label="优先级">
            <el-switch
              v-model="form.priority"
              :active-value="PRIORITY.URGENT"
              :inactive-value="PRIORITY.NORMAL"
              :disabled="metadataOnlyEdit"
              active-text="加急"
            />
          </el-form-item>
          <el-form-item label="调度方式">
            <el-radio-group v-model="form.scheduleType" :disabled="metadataOnlyEdit">
              <el-radio :value="SCHEDULE_TYPES.DATE">指定日期</el-radio>
              <el-radio :value="SCHEDULE_TYPES.NOTICE">等待通知</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item
            v-if="form.scheduleType === SCHEDULE_TYPES.DATE"
            label="计划开工日期"
            required
          >
            <el-date-picker
              v-model="form.processDate"
              type="date"
              value-format="YYYY-MM-DD"
              :disabled="metadataOnlyEdit"
            />
          </el-form-item>
          <el-form-item label="提醒方式">
            <el-select v-model="form.notifyType">
              <el-option
                v-for="item in notifyTypes"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="通知状态">
            <el-radio-group v-model="form.notifyStatus">
              <el-radio :value="NOTIFY_STATUS.NOTIFIED">已通知</el-radio>
              <el-radio :value="NOTIFY_STATUS.PENDING">未通知</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="收费状态">
            <el-radio-group v-model="form.paymentStatus">
              <el-radio :value="PAYMENT_STATUS.PAID">已收费</el-radio>
              <el-radio :value="PAYMENT_STATUS.UNPAID">未收费</el-radio>
            </el-radio-group>
          </el-form-item>
        </div>
        <el-form-item label="加工备注">
          <el-input
            v-model="form.processRemark"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            :disabled="metadataOnlyEdit"
          />
        </el-form-item>
        <el-form-item label="其它备注">
          <el-input v-model="form.remark" maxlength="500" :disabled="metadataOnlyEdit" />
        </el-form-item>
      </el-form>
      <el-form v-else :model="batchForm" label-width="100px" class="batch-form">
        <el-card shadow="never" class="batch-section">
          <template #header>
            <div class="batch-section-header">
              <span>处方信息</span>
              <el-radio-group v-model="batchForm.prescriptionMode" size="small">
                <el-radio-button value="existing">选择现有处方</el-radio-button>
                <el-radio-button value="new">新建处方</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <template v-if="batchForm.prescriptionMode === 'existing'">
            <el-form-item label="处方" required>
              <el-select
                v-model="batchForm.prescriptionId"
                filterable
                remote
                reserve-keyword
                :remote-method="loadPrescriptionOptions"
                :loading="prescriptionLoading"
                style="width: 100%"
                placeholder="输入处方编号、姓名或手机号搜索"
                @visible-change="onPrescriptionVisibleChange"
              >
                <el-option
                  v-for="item in prescriptions"
                  :key="item.id"
                  :label="`${item.prescriptionNo} · ${item.customerName} · ${item.phone || '未留手机号'}`"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
          </template>
          <template v-else>
            <div class="form-grid">
              <el-form-item v-if="userStore.isSuperAdmin" label="所属门店" required>
                <el-select v-model="batchForm.prescription.storeId" placeholder="请选择门店">
                  <el-option
                    v-for="item in stores"
                    :key="item.id"
                    :label="item.name"
                    :value="item.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="顾客姓名" required>
                <el-input v-model.trim="batchForm.prescription.customerName" maxlength="64" />
              </el-form-item>
              <el-form-item label="手机号">
                <el-input v-model.trim="batchForm.prescription.phone" maxlength="20" />
              </el-form-item>
              <el-form-item label="医生" required>
                <el-select
                  v-model="batchForm.prescription.doctorId"
                  filterable
                  placeholder="请选择"
                >
                  <el-option
                    v-for="item in doctors"
                    :key="item.id"
                    :label="item.name"
                    :value="item.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="处方来源" required>
                <el-select v-model="batchForm.prescription.sourceId" placeholder="请选择">
                  <el-option
                    v-for="item in sources"
                    :key="item.id"
                    :label="item.name"
                    :value="item.id"
                  />
                </el-select>
              </el-form-item>
            </div>
            <el-form-item label="外方">
              <el-checkbox v-model="batchForm.prescription.isExternal">外方</el-checkbox>
            </el-form-item>
            <div v-if="batchForm.prescription.isExternal" class="form-grid">
              <el-form-item label="医院名称">
                <el-input v-model.trim="batchForm.prescription.externalHospital" maxlength="150" />
              </el-form-item>
              <el-form-item label="医生姓名">
                <el-input v-model.trim="batchForm.prescription.externalDoctor" maxlength="100" />
              </el-form-item>
            </div>
            <el-form-item v-if="batchForm.prescription.isExternal" label="外方备注">
              <el-input
                v-model="batchForm.prescription.externalRemark"
                type="textarea"
                :rows="2"
                maxlength="500"
                show-word-limit
              />
            </el-form-item>
            <el-form-item label="处方备注">
              <el-input
                v-model="batchForm.prescription.remark"
                type="textarea"
                :rows="2"
                maxlength="500"
                show-word-limit
              />
            </el-form-item>
          </template>
        </el-card>

        <el-card shadow="never" class="batch-section">
          <template #header>
            <div class="batch-section-header">
              <span>加工批次</span>
              <div class="batch-tools">
                <span class="batch-count-label">总剂数</span>
                <el-input-number
                  v-model="batchForm.totalDose"
                  :min="1"
                  :max="9999"
                  size="small"
                  :controls="false"
                  @change="generateBatchPlans"
                />
                <span class="batch-count-label">总批次</span>
                <el-input-number
                  v-model="batchForm.batchCount"
                  :min="1"
                  :max="100"
                  size="small"
                  :controls="false"
                  @change="generateBatchPlans"
                />
                <el-button size="small" @click="generateBatchPlans">生成批次</el-button>
                <el-button size="small" type="primary" plain :icon="Plus" @click="addBatchPlan">
                  添加批次
                </el-button>
              </div>
            </div>
          </template>
          <div class="batch-unified-settings">
            <div class="batch-unified-grid batch-unified-grid-main">
              <div class="batch-field">
                <span class="batch-field-label required">加工方式</span>
                <el-select
                  v-model="batchForm.processTypeId"
                  placeholder="请选择"
                  @change="syncBatchSettings"
                >
                  <el-option
                    v-for="item in processTypes"
                    :key="item.id"
                    :label="item.name"
                    :value="item.id"
                  />
                </el-select>
              </div>
              <div class="batch-field">
                <span class="batch-field-label required">取货方式</span>
                <el-select
                  v-model="batchForm.pickupMethod"
                  placeholder="请选择取货方式"
                  @change="syncBatchSettings"
                >
                  <el-option
                    v-for="item in PICKUP_METHOD_OPTIONS"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </div>
              <div v-if="batchFormIsDecoction" class="batch-field">
                <span class="batch-field-label required">每剂袋数</span>
                <el-input-number
                  v-model="batchForm.bagsPerDose"
                  :min="1"
                  :max="9999"
                  :controls="false"
                  @change="syncAllBatchBags"
                />
              </div>
              <div v-if="batchFormIsDecoction" class="batch-field">
                <span class="batch-field-label required">每袋毫升</span>
                <el-input-number
                  v-model="batchForm.volumeMl"
                  :min="1"
                  :max="99999"
                  :controls="false"
                  @change="syncBatchSettings"
                />
              </div>
              <div class="batch-field">
                <span class="batch-field-label">提醒方式</span>
                <el-select v-model="batchForm.notifyType" @change="syncBatchSettings">
                  <el-option
                    v-for="item in notifyTypes"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </div>
              <div class="batch-field">
                <span class="batch-field-label">收费状态</span>
                <el-select v-model="batchForm.paymentStatus" @change="syncBatchSettings">
                  <el-option label="已收费" :value="PAYMENT_STATUS.PAID" />
                  <el-option label="未收费" :value="PAYMENT_STATUS.UNPAID" />
                </el-select>
              </div>
            </div>
            <div class="batch-unified-grid batch-unified-grid-secondary">
              <div
                v-if="[1, 2].includes(Number(batchForm.pickupMethod))"
                class="batch-field"
              >
                <span class="batch-field-label">地址</span>
                <el-input
                  v-model.trim="batchForm.expressAddress"
                  maxlength="500"
                  placeholder="选填"
                  @change="syncBatchSettings"
                />
              </div>
              <div class="batch-field batch-field-wide">
                <span class="batch-field-label">服用方法</span>
                <UsageMethodInput v-model="batchForm.usageMethod" />
              </div>
            </div>
          </div>
          <div class="batch-plan-list">
            <section
              v-for="(row, batchIndex) in batchForm.plans"
              :key="batchIndex"
              class="batch-plan-item"
            >
              <div class="batch-plan-header">
                <div>
                  <strong>第 {{ row.batchNo || batchIndex + 1 }} 批</strong>
                  <span>共 {{ batchForm.plans.length }} 批</span>
                </div>
                <div class="batch-plan-actions">
                  <el-button link type="primary" @click="copyPreviousBatch(batchIndex)">
                    复制上批
                  </el-button>
                  <el-button
                    link
                    type="danger"
                    :disabled="batchForm.plans.length === 1"
                    @click="removeBatchPlan(batchIndex)"
                  >
                    删除
                  </el-button>
                </div>
              </div>

              <div class="batch-plan-grid">
                <div class="batch-field">
                  <span class="batch-field-label required">批次号</span>
                  <el-input-number v-model="row.batchNo" :min="1" :max="999" :controls="false" />
                </div>
                <div class="batch-field">
                  <span class="batch-field-label required">剂数</span>
                  <el-input-number
                    v-model="row.totalDose"
                    :min="1"
                    :max="999"
                    :controls="false"
                    @change="syncBatchPlan(row)"
                  />
                </div>
                <div v-if="isDecoctionProcessType(row.processTypeId)" class="batch-field">
                  <span class="batch-field-label required">本批袋数</span>
                  <el-input-number
                    :model-value="batchBagCount(row)"
                    :min="1"
                    :max="9999"
                    :controls="false"
                    disabled
                  />
                </div>
                <div class="batch-field">
                  <span class="batch-field-label">调度方式</span>
                  <el-radio-group v-model="row.scheduleType">
                    <el-radio-button :value="SCHEDULE_TYPES.DATE">指定日期</el-radio-button>
                    <el-radio-button :value="SCHEDULE_TYPES.NOTICE">等待通知</el-radio-button>
                  </el-radio-group>
                </div>
                <div class="batch-field">
                  <span
                    class="batch-field-label"
                    :class="{ required: row.scheduleType === SCHEDULE_TYPES.DATE }"
                  >
                    计划日期
                  </span>
                  <el-date-picker
                    v-if="row.scheduleType === SCHEDULE_TYPES.DATE"
                    v-model="row.processDate"
                    type="date"
                    value-format="YYYY-MM-DD"
                  />
                  <el-input v-else model-value="等待顾客通知" disabled />
                </div>
                <div class="batch-field">
                  <span class="batch-field-label">优先级</span>
                  <div class="switch-field">
                    <el-switch
                      v-model="row.priority"
                      :active-value="PRIORITY.URGENT"
                      :inactive-value="PRIORITY.NORMAL"
                      active-text="加急"
                      inactive-text="普通"
                    />
                  </div>
                </div>
                <div class="batch-field batch-field-wide">
                  <span class="batch-field-label">加工备注</span>
                  <el-input v-model="row.processRemark" maxlength="500" placeholder="如不要放糖" />
                </div>
                <div class="batch-field batch-field-wide">
                  <span class="batch-field-label">其它备注</span>
                  <el-input v-model="row.remark" maxlength="500" placeholder="选填" />
                </div>
              </div>
            </section>
          </div>
        </el-card>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="savePlan">保存</el-button>
      </template>
    </el-drawer>

    <el-dialog v-model="delayVisible" :title="scheduleDialogTitle" width="480px">
      <el-form label-width="110px">
        <el-form-item label="安排方式">
          <el-radio-group v-model="delayForm.scheduleType">
            <el-radio :value="SCHEDULE_TYPES.DATE">指定日期</el-radio>
            <el-radio :value="SCHEDULE_TYPES.NOTICE">等待顾客通知</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item
          v-if="delayForm.scheduleType === SCHEDULE_TYPES.DATE"
          label="计划开工"
          required
        >
          <el-date-picker v-model="delayForm.processDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="delayVisible = false">取消</el-button>
        <el-button type="primary" @click="submitDelay">保存安排</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="noticeVisible" title="收到顾客通知" width="480px">
      <el-form label-width="110px">
        <el-form-item label="开始加工日期">
          <el-radio-group v-model="noticePreset" @change="applyNoticePreset">
            <el-radio value="today">今天</el-radio>
            <el-radio value="tomorrow">明天</el-radio>
            <el-radio value="custom">指定日期</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="noticePreset === 'custom'" label="指定日期" required>
          <el-date-picker v-model="noticeDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="noticeVisible = false">取消</el-button>
        <el-button type="primary" @click="submitNotice">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="quickEditVisible"
      :title="quickEditTitle"
      width="min(420px, 92vw)"
      destroy-on-close
    >
      <el-form label-width="90px">
        <template v-if="quickEditType === 'notification'">
          <el-form-item label="通知状态">
            <el-radio-group v-model="quickEditForm.notifyStatus">
              <el-radio :value="NOTIFY_STATUS.NOTIFIED">已通知</el-radio>
              <el-radio :value="NOTIFY_STATUS.PENDING">未通知</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="提醒方式">
            <el-select v-model="quickEditForm.notifyType" placeholder="请选择提醒方式">
              <el-option
                v-for="item in notifyTypes"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </template>
        <el-form-item v-else-if="quickEditType === 'payment'" label="收费状态">
          <el-radio-group v-model="quickEditForm.paymentStatus">
            <el-radio :value="PAYMENT_STATUS.PAID">已收费</el-radio>
            <el-radio :value="PAYMENT_STATUS.UNPAID">未收费</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-else-if="quickEditType === 'pickup'" label="取货方式">
          <el-select v-model="quickEditForm.pickupMethod" placeholder="请选择取货方式">
            <el-option
              v-for="item in PICKUP_METHOD_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          v-if="quickEditType === 'pickup' && [1, 2].includes(Number(quickEditForm.pickupMethod))"
          label="地址"
        >
          <el-input
            v-model.trim="quickEditForm.expressAddress"
            type="textarea"
            :rows="2"
            maxlength="500"
            placeholder="选填"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="quickEditVisible = false">取消</el-button>
        <el-button type="primary" :loading="quickEditLoading" @click="submitQuickEdit">
          保存
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="statusQuickVisible"
      title="加工状态快捷操作"
      width="min(440px, 92vw)"
      destroy-on-close
    >
      <el-descriptions :column="1" border>
        <el-descriptions-item label="当前状态">
          <el-tag :type="statusType[statusQuickPlan?.status] || 'info'" effect="plain">
            {{ statusMap[statusQuickPlan?.status] || statusQuickPlan?.status || '-' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="下一步操作">
          {{ statusQuickInfo.label }}
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="statusQuickVisible = false">取消</el-button>
        <el-button :type="statusQuickInfo.type" @click="submitStatusQuick">
          {{ statusQuickInfo.confirmText }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import {
  computed,
  defineComponent,
  h,
  nextTick,
  onMounted,
  reactive,
  ref,
  resolveDirective,
  watch,
  withDirectives
} from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElButton } from 'element-plus/es/components/button/index.mjs';
import { ElInputNumber } from 'element-plus/es/components/input-number/index.mjs';
import { ElMessage } from 'element-plus/es/components/message/index.mjs';
import { ElMessageBox } from 'element-plus/es/components/message-box/index.mjs';
import { ElTable, ElTableColumn } from 'element-plus/es/components/table/index.mjs';
import { ElTag } from 'element-plus/es/components/tag/index.mjs';
import { ElTooltip } from 'element-plus/es/components/tooltip/index.mjs';
import {
  Delete,
  Plus,
  Printer,
  Refresh,
  RefreshRight,
  Search,
  Upload
} from '@element-plus/icons-vue';
import EmptyView from '@/components/EmptyView.vue';
import ProcessingPrintDialog from '@/components/ProcessingPrintDialog.vue';
import UsageMethodInput from '@/components/UsageMethodInput.vue';
import Pagination from '@/components/Pagination.vue';
import StatisticCard from '@/components/StatisticCard.vue';
import ReadyPickup from '@/views/admin/ReadyPickup.vue';
import PackageDetail from '@/views/admin/PackageDetail.vue';
import PackageEdit from '@/views/admin/PackageEdit.vue';
import Verify from '@/views/admin/Verify.vue';
import { getStats } from '@/api/package';
import { getPrescriptions } from '@/api/prescription';
import { getStores } from '@/api/store';
import {
  createProcessingPlan,
  createProcessingPlanBatch,
  createManualProcessingUsage,
  deleteProcessingPhoto,
  deleteProcessingPlan,
  getDictionaries,
  getDoctors,
  getProcessingCalendar,
  getProcessingPhoto,
  getProcessingPlans,
  getProcessingWorkflow,
  generateProcessingPlanPackage,
  receiveProcessingNotice,
  reorderProcessingQueue,
  restoreProcessingQueue,
  transitionProcessingPlan,
  uploadProcessingPhoto,
  updateProcessingPlan
} from '@/api/processing';
import { getProcessingEquipment } from '@/api/processingEquipment';
import { useUserStore } from '@/stores/user';
import { formatDate } from '@/utils/date';
import { compressImageForUpload } from '@/utils/imageUpload';
import { splitDoseBatches } from '@/utils/processingBatches';
import {
  NOTIFY_STATUS,
  PAYMENT_STATUS,
  PRIORITY,
  PROCESS_TYPE_CODES,
  PROCESSING_STATUS,
  PROCESSING_STATUS_OPTIONS,
  PROCESSING_STATUS_TAG,
  SCHEDULE_TYPES
} from '@/constants/processing';
import { isValidPhone } from '@/utils/phone';
import {
  formatPickupCode,
  PICKUP_METHOD_OPTIONS,
  pickupMethodTagType,
  pickupMethodText
} from '@/utils/status';

const statuses = PROCESSING_STATUS_OPTIONS;
const statusMap = Object.fromEntries(statuses.map((item) => [item.value, item.label]));
const statusType = PROCESSING_STATUS_TAG;

const workflowStageNames = {
  1: '调配中',
  2: '调配完成',
  3: '浸泡中',
  4: '煎煮中',
  5: '打包中',
  6: '打包完成',
  7: '加工完成'
};
const usageStageNames = { 3: '浸泡', 4: '煎煮', 5: '打包' };
const usageStatusNames = { 1: '进行中', 2: '已完成', 3: '已作废' };
const usageSourceNames = { 1: '扫码记录', 2: '人工补录', 3: '故障接续' };
const workflowExceptionTypeNames = { 1: '误扫撤销', 2: '设备故障换机', 3: '人工补录' };
const equipmentTypeByStage = { 3: 'SOAK_BUCKET', 4: 'DECOCTION_POT', 5: 'PACKAGING_MACHINE' };
const DETAIL_PHOTO_MAX_SIZE = 5 * 1024 * 1024;

async function prepareDetailPhoto(file) {
  try {
    return await compressImageForUpload(file, {
      maxBytes: DETAIL_PHOTO_MAX_SIZE,
      compressionThresholdBytes: DETAIL_PHOTO_MAX_SIZE,
      fallbackBaseName: '调配照片',
      strategies: [
        { quality: 0.9, maxEdge: 3000 },
        { quality: 0.82, maxEdge: 2400 },
        { quality: 0.75, maxEdge: 1920 }
      ]
    });
  } catch (error) {
    error.photoPreparationFailed = true;
    throw error;
  }
}

function workflowStageText(stage) {
  return workflowStageNames[Number(stage)] || '-';
}
function usageStageText(stage) {
  return usageStageNames[Number(stage)] || '-';
}
function usageStatusText(status) {
  return usageStatusNames[Number(status)] || '-';
}
function usageStatusTag(status) {
  return { 1: 'primary', 2: 'success', 3: 'info' }[Number(status)] || 'info';
}
function usageSourceText(source) {
  return usageSourceNames[Number(source)] || '-';
}
function workflowExceptionTypeText(type) {
  return workflowExceptionTypeNames[Number(type)] || '异常处理';
}
function workflowRequestId() {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 12)}`;
}
function workflowDuration(start, end) {
  if (!start) return '-';
  const minutes = Math.max(
    0,
    Math.floor(((end ? new Date(end) : new Date()).getTime() - new Date(start).getTime()) / 60000)
  );
  if (minutes < 60) return `${minutes} 分钟`;
  return `${Math.floor(minutes / 60)} 小时 ${minutes % 60} 分钟`;
}

function workflowProgress(row) {
  const decoction = isDecoctionPlan(row);
  const steps = decoction ? ['调配', '浸泡', '煎煮', '打包', '完成'] : ['调配', '加工', '完成'];
  const indexes = decoction
    ? {
        1: 0,
        2: 1,
        3: 1,
        4: 2,
        5: 3,
        6: 3,
        7: 4
      }
    : { 1: 0, 2: 1, 7: 2 };
  const stage = Number(row.currentStage);
  let activeIndex = indexes[stage];
  let currentLabel = workflowStageText(stage);
  if (activeIndex === undefined) {
    if (Number(row.status) === PROCESSING_STATUS.WAITING) {
      activeIndex = -1;
      currentLabel = '待开始';
    } else if (Number(row.status) === PROCESSING_STATUS.PROCESSING) {
      activeIndex = 0;
      currentLabel = '调配中';
    } else if (
      [
        PROCESSING_STATUS.FINISHED,
        PROCESSING_STATUS.READY_PICKUP,
        PROCESSING_STATUS.PICKED
      ].includes(Number(row.status))
    ) {
      activeIndex = steps.length - 1;
      currentLabel = '加工完成';
    } else {
      activeIndex = -1;
      currentLabel = '未开始';
    }
  }
  return { steps, activeIndex, currentLabel };
}

function workflowTooltip(progress) {
  return h('div', { style: { minWidth: '210px' } }, [
    h(
      'div',
      { style: { marginBottom: '8px', fontWeight: '600' } },
      `当前工序：${progress.currentLabel}`
    ),
    h(
      'div',
      { style: { display: 'flex', gap: '4px', width: '100%' } },
      progress.steps.map((label, index) =>
        h('span', {
          key: label,
          title: label,
          style: {
            flex: '1',
            height: '5px',
            borderRadius: '999px',
            backgroundColor:
              index < progress.activeIndex ||
              (index === progress.activeIndex && progress.activeIndex === progress.steps.length - 1)
                ? 'var(--el-color-success)'
                : index === progress.activeIndex
                  ? 'var(--el-color-primary)'
                  : 'var(--el-border-color-lighter)'
          }
        })
      )
    ),
    h(
      'div',
      {
        style: {
          display: 'flex',
          justifyContent: 'space-between',
          gap: '4px',
          marginTop: '4px',
          color: 'var(--el-text-color-secondary)',
          fontSize: '11px'
        }
      },
      progress.steps.map((label) => h('span', { key: label }, label))
    )
  ]);
}

function planStage(row, view) {
  if (
    row.status === PROCESSING_STATUS.WAITING &&
    row.scheduleType === SCHEDULE_TYPES.DATE &&
    row.processDate &&
    dateText(row.processDate) < todayText()
  ) {
    return { label: '逾期未开工', type: 'warning' };
  }
  if (view === 'today-all' && row.finishDate && dateText(row.finishDate) === todayText()) {
    return { label: '已完成', type: 'success' };
  }
  return {
    label: statusMap[row.status] || row.status,
    type: statusType[row.status] || 'info'
  };
}

const PlanTable = defineComponent({
  props: {
    rows: { type: Array, default: () => [] },
    loading: { type: Boolean, default: false },
    showStore: { type: Boolean, default: false },
    queueEditable: { type: Boolean, default: false },
    view: { type: String, default: '' }
  },
  emits: ['queue-change', 'action'],
  setup(props, { emit }) {
    const action = (name, row) => emit('action', name, row);
    const canQuickEdit = (row) =>
      [
        PROCESSING_STATUS.WAITING,
        PROCESSING_STATUS.PROCESSING,
        PROCESSING_STATUS.FINISHED,
        PROCESSING_STATUS.READY_PICKUP
      ].includes(row.status);
    const canQuickStatus = (row) =>
      [
        PROCESSING_STATUS.WAITING,
        PROCESSING_STATUS.PROCESSING,
        PROCESSING_STATUS.FINISHED
      ].includes(row.status) ||
      (row.status === PROCESSING_STATUS.READY_PICKUP && Boolean(row.package));
    const canCancelProcessing = (row) =>
      row.status === PROCESSING_STATUS.PROCESSING &&
      Number(row.currentStage) === 1 &&
      !row.dispensingCompletedAt;
    const quickTagProps = (row, name, enabled = canQuickEdit(row), title = '点击修改') =>
      enabled
        ? {
            class: 'quick-edit-tag',
            role: 'button',
            tabindex: 0,
            title,
            onClick: () => action(name, row),
            onKeydown: (event) => {
              if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                action(name, row);
              }
            }
          }
        : {};
    const loadingDirective = resolveDirective('loading');
    return () =>
      withDirectives(
        h(
          ElTable,
          {
            data: props.rows,
            border: true,
            'table-layout': 'auto',
            'row-key': 'id',
            class: 'plan-table'
          },
          {
            empty: () => h(EmptyView, { description: '暂无加工计划' }),
            default: () =>
              [
                !['notice', 'all'].includes(props.view)
                  ? h(
                      ElTableColumn,
                      { label: '顺序', align: 'center' },
                      {
                        default: ({ row }) =>
                          h('div', { class: 'queue-cell' }, [
                            props.queueEditable
                              ? h(ElInputNumber, {
                                  modelValue: row.queueOrder || 1,
                                  min: 1,
                                  controls: false,
                                  size: 'small',
                                  'onUpdate:modelValue': (value) => {
                                    row.queueOrder = value;
                                  },
                                  onChange: () => emit('queue-change', row)
                                })
                              : h(
                                  'strong',
                                  row.queueOrder == null
                                    ? '-'
                                    : String(row.queueOrder).padStart(3, '0')
                                ),
                            row.priority === PRIORITY.URGENT
                              ? h(
                                  ElTag,
                                  { type: 'danger', size: 'small', effect: 'dark' },
                                  () => '【加急】'
                                )
                              : null
                          ])
                      }
                    )
                  : null,
                h(
                  ElTableColumn,
                  { label: '顾客 / 医生', align: 'center' },
                  {
                    default: ({ row }) =>
                      h('div', [
                        h('strong', row.prescription?.customerName || '-'),
                        h(
                          'div',
                          { class: 'secondary-text' },
                          row.prescription?.doctor?.name || '-'
                        ),
                        ['notice', 'all'].includes(props.view) && row.priority === PRIORITY.URGENT
                          ? h(
                              ElTag,
                              { type: 'danger', size: 'small', effect: 'dark' },
                              () => '【加急】'
                            )
                          : null
                      ])
                  }
                ),
                props.showStore
                  ? h(
                      ElTableColumn,
                      { label: '门店', align: 'center' },
                      { default: ({ row }) => row.store?.name || '-' }
                    )
                  : null,
                h(
                  ElTableColumn,
                  { label: '加工方式', align: 'center' },
                  { default: ({ row }) => row.processType?.name || '-' }
                ),
                h(
                  ElTableColumn,
                  { label: '取货方式', align: 'center' },
                  {
                    default: ({ row }) =>
                      h(
                        ElTag,
                        {
                          type: pickupMethodTagType(row.pickupMethod),
                          effect: 'plain',
                          ...quickTagProps(row, 'quick-pickup')
                        },
                        () => pickupMethodText(row.pickupMethod)
                      )
                  }
                ),
                h(ElTableColumn, {
                  prop: 'totalDose',
                  label: '剂数',
                  align: 'center'
                }),
                h(ElTableColumn, {
                  prop: 'bagCount',
                  label: '袋数',
                  align: 'center',
                  formatter: (row) => row.bagCount || '-'
                }),
                h(ElTableColumn, {
                  prop: 'volumeMl',
                  label: '毫升数',
                  align: 'center',
                  formatter: (row) => row.volumeMl || '-'
                }),
                h(
                  ElTableColumn,
                  { label: '计划开工', align: 'center' },
                  {
                    default: ({ row }) => {
                      const text =
                        row.scheduleType === SCHEDULE_TYPES.NOTICE
                          ? '等待通知'
                          : String(row.processDate || '').slice(0, 10);
                      return row.status === PROCESSING_STATUS.WAITING
                        ? h(
                            ElButton,
                            {
                              link: true,
                              type: 'primary',
                              class: 'schedule-edit-button',
                              title: '点击修改计划开工',
                              onClick: () => action('quick-schedule', row)
                            },
                            () => text
                          )
                        : text;
                    }
                  }
                ),
                h(
                  ElTableColumn,
                  { label: '状态', align: 'center' },
                  {
                    default: ({ row }) => {
                      const stage = planStage(row, props.view);
                      const statusTag = h(
                        ElTag,
                        {
                          type: stage.type,
                          effect: 'plain',
                          ...quickTagProps(
                            row,
                            'quick-status',
                            canQuickStatus(row),
                            '点击进行状态操作'
                          )
                        },
                        () => stage.label
                      );
                      const progress = workflowProgress(row);
                      if (!progress) return statusTag;
                      return h(
                        ElTooltip,
                        {
                          placement: 'top',
                          effect: 'light',
                          showAfter: 120,
                          popperClass: 'workflow-progress-tooltip'
                        },
                        {
                          default: () => statusTag,
                          content: () => workflowTooltip(progress)
                        }
                      );
                    }
                  }
                ),
                h(
                  ElTableColumn,
                  { label: '收费状态', align: 'center' },
                  {
                    default: ({ row }) =>
                      h(
                        ElTag,
                        {
                          type: row.paymentStatus === PAYMENT_STATUS.PAID ? 'success' : 'warning',
                          effect: 'plain',
                          ...quickTagProps(row, 'quick-payment')
                        },
                        () => (row.paymentStatus === PAYMENT_STATUS.PAID ? '已收费' : '未收费')
                      )
                  }
                ),
                ...(['today-all', 'today-finished'].includes(props.view)
                  ? [
                      h(
                        ElTableColumn,
                        { label: '通知状态', align: 'center' },
                        {
                          default: ({ row }) =>
                            h(
                              ElTag,
                              {
                                type:
                                  Number(row.notifyStatus) === NOTIFY_STATUS.NOTIFIED
                                    ? 'success'
                                    : 'info',
                                effect: 'plain',
                                ...quickTagProps(row, 'quick-notification')
                              },
                              () =>
                                Number(row.notifyStatus) === NOTIFY_STATUS.NOTIFIED
                                  ? '已通知'
                                  : '未通知'
                            )
                        }
                      )
                    ]
                  : []),
                h(
                  ElTableColumn,
                  { label: '操作', align: 'center' },
                  {
                    default: ({ row }) =>
                      h('div', { class: 'table-actions' }, [
                        h(
                          ElButton,
                          { link: true, type: 'primary', onClick: () => action('detail', row) },
                          () => '详情'
                        ),
                        row.status === PROCESSING_STATUS.WAITING &&
                        row.scheduleType === SCHEDULE_TYPES.NOTICE
                          ? h(
                              ElButton,
                              { link: true, type: 'success', onClick: () => action('notice', row) },
                              () => '收到通知'
                            )
                          : null,
                        row.status === PROCESSING_STATUS.WAITING &&
                        row.scheduleType === SCHEDULE_TYPES.DATE
                          ? h(
                              ElButton,
                              { link: true, type: 'primary', onClick: () => action('start', row) },
                              () => '开始加工'
                            )
                          : null,
                        row.status === PROCESSING_STATUS.PROCESSING
                          ? h(
                              ElButton,
                              { link: true, type: 'success', onClick: () => action('finish', row) },
                              () => '加工完成'
                            )
                          : null,
                        canCancelProcessing(row)
                          ? h(
                              ElButton,
                              {
                                link: true,
                                type: 'danger',
                                onClick: () => action('cancel-processing', row)
                              },
                              () => '取消加工'
                            )
                          : null,
                        row.status === PROCESSING_STATUS.FINISHED && !row.package
                          ? h(
                              ElButton,
                              {
                                link: true,
                                type: 'success',
                                onClick: () => action('generate-package', row)
                              },
                              () => '生成包裹'
                            )
                          : null,
                        [
                          PROCESSING_STATUS.WAITING,
                          PROCESSING_STATUS.PROCESSING,
                          PROCESSING_STATUS.FINISHED,
                          PROCESSING_STATUS.READY_PICKUP
                        ].includes(row.status)
                          ? h(
                              ElButton,
                              {
                                link: true,
                                type: 'primary',
                                onClick: () => action('edit', row)
                              },
                              () => '编辑'
                            )
                          : null,
                        row.status === PROCESSING_STATUS.WAITING
                          ? h(
                              ElTooltip,
                              { content: '删除' },
                              {
                                default: () =>
                                  h(ElButton, {
                                    link: true,
                                    type: 'danger',
                                    icon: Delete,
                                    'aria-label': '删除',
                                    onClick: () => action('delete', row)
                                  })
                              }
                            )
                          : null
                      ])
                  }
                )
              ].filter(Boolean)
          }
        ),
        loadingDirective ? [[loadingDirective, props.loading]] : []
      );
  }
});

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const mode = ref(['calendar', 'pickup'].includes(route.query.mode) ? route.query.mode : 'list');
const listViews = [
  'today-all',
  'today-waiting',
  'overdue',
  'processing',
  'today-finished',
  'urgent',
  'notice',
  'tomorrow',
  'all'
];
const activeView = ref(listViews.includes(route.query.view) ? route.query.view : 'today-all');
const loading = ref(false);
const calendarLoading = ref(false);
const prescriptionLoading = ref(false);
const finishDialogVisible = ref(false);
const finishingPlan = ref(null);
const finishing = ref(false);
const saving = ref(false);
const list = ref([]);
const calendarList = ref([]);
const prescriptions = ref([]);
const processTypes = ref([]);
const doctors = ref([]);
const sources = ref([]);
const stores = ref([]);
const formVisible = ref(false);
const detailVisible = ref(false);
const detailPlan = ref(null);
const detailLoading = ref(false);
const detailPhotoUrls = ref([]);
const detailPhotoUploading = ref(false);
const detailPhotoUploadRef = ref(null);
const manualUsageVisible = ref(false);
const manualUsageSaving = ref(false);
const manualUsageEquipment = ref([]);
const manualUsageForm = reactive({
  stage: 3,
  portionNo: 1,
  equipmentId: null,
  startedAt: null,
  endedAt: null,
  reason: ''
});
const manualUsageEquipmentOptions = computed(() =>
  manualUsageEquipment.value.filter(
    (item) => item.type === equipmentTypeByStage[Number(manualUsageForm.stage)]
  )
);
const canManageDetailPhotos = computed(
  () =>
    Number(detailPlan.value?.status) === PROCESSING_STATUS.PROCESSING &&
    [1, 2].includes(Number(detailPlan.value?.currentStage))
);
const canUploadDetailPhoto = computed(
  () => canManageDetailPhotos.value && (detailPlan.value?.photos?.length || 0) < 3
);
const planPrintVisible = ref(false);
const planPrintType = ref('PROCESSING');
const pickupDrawerVisible = ref(false);
const pickupDrawerMode = ref('detail');
const pickupPackageId = ref(null);
const pickupPackageCode = ref('');
const pickupPackageDetailRef = ref(null);
const delayVisible = ref(false);
const noticeVisible = ref(false);
const quickEditVisible = ref(false);
const quickEditLoading = ref(false);
const quickEditType = ref('');
const quickEditPlan = ref(null);
const statusQuickVisible = ref(false);
const statusQuickPlan = ref(null);
const editingId = ref(null);
const selectedPlan = ref(null);
const calendarDate = ref(new Date());
const calendarCounts = ref({});
const noticePreset = ref('today');
const noticeDate = ref('');
const query = reactive({ keyword: '', status: '', processTypeId: '', doctorId: '', storeId: '' });

function releaseDetailPhotos() {
  detailPhotoUrls.value.forEach((item) => URL.revokeObjectURL(item.url));
  detailPhotoUrls.value = [];
}

async function loadDetailWorkflow(planId) {
  detailLoading.value = true;
  releaseDetailPhotos();
  try {
    const workflow = await getProcessingWorkflow(planId);
    detailPlan.value = workflow;
    const photos = await Promise.all(
      (workflow.photos || []).map(async (photo) => ({
        id: photo.id,
        url: URL.createObjectURL(await getProcessingPhoto(workflow.id, photo.id))
      }))
    );
    if (detailVisible.value && detailPlan.value?.id === planId) detailPhotoUrls.value = photos;
    else photos.forEach((item) => URL.revokeObjectURL(item.url));
  } finally {
    detailLoading.value = false;
  }
}

async function handleDetailPhotoUpload(uploadFile) {
  const file = uploadFile?.raw;
  if (!file || detailPhotoUploading.value || !detailPlan.value?.id) return;
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
    ElMessage.warning('仅支持 JPG、PNG 或 WEBP 图片');
    detailPhotoUploadRef.value?.clearFiles();
    return;
  }
  detailPhotoUploading.value = true;
  try {
    const preparedFile = await prepareDetailPhoto(file);
    await uploadProcessingPhoto(detailPlan.value.id, preparedFile);
    ElMessage.success('照片已上传');
    await loadDetailWorkflow(detailPlan.value.id);
  } catch (error) {
    if (error.photoPreparationFailed) ElMessage.error(error.message || '图片压缩失败');
  } finally {
    detailPhotoUploading.value = false;
    detailPhotoUploadRef.value?.clearFiles();
  }
}

async function removeDetailPhoto(photoId) {
  if (!detailPlan.value?.id || detailPhotoUploading.value) return;
  try {
    await ElMessageBox.confirm('确认删除这张调配照片？', '删除照片', { type: 'warning' });
  } catch {
    return;
  }
  detailPhotoUploading.value = true;
  try {
    await deleteProcessingPhoto(detailPlan.value.id, photoId);
    ElMessage.success('照片已删除');
    await loadDetailWorkflow(detailPlan.value.id);
  } finally {
    detailPhotoUploading.value = false;
  }
}

async function openManualUsage() {
  if (!detailPlan.value?.id) return;
  const endedAt = new Date();
  const startedAt = new Date(endedAt.getTime() - 30 * 60 * 1000);
  Object.assign(manualUsageForm, {
    stage: 3,
    portionNo: 1,
    equipmentId: null,
    startedAt,
    endedAt,
    reason: ''
  });
  const result = await getProcessingEquipment({
    page: 1,
    pageSize: 100,
    status: 1,
    storeId: detailPlan.value.storeId
  });
  manualUsageEquipment.value = result.list || [];
  manualUsageVisible.value = true;
}

async function submitManualUsage() {
  if (!detailPlan.value?.id || manualUsageSaving.value) return;
  if (!manualUsageForm.equipmentId) return ElMessage.warning('请选择设备');
  if (!manualUsageForm.startedAt || !manualUsageForm.endedAt)
    return ElMessage.warning('请选择开始和结束时间');
  if (!manualUsageForm.reason) return ElMessage.warning('请填写补录原因');
  manualUsageSaving.value = true;
  try {
    await createManualProcessingUsage(detailPlan.value.id, {
      stage: Number(manualUsageForm.stage),
      portionNo: Number(manualUsageForm.portionNo),
      equipmentId: Number(manualUsageForm.equipmentId),
      startedAt: manualUsageForm.startedAt,
      endedAt: manualUsageForm.endedAt,
      reason: manualUsageForm.reason,
      requestId: workflowRequestId()
    });
    manualUsageVisible.value = false;
    ElMessage.success('工序已补录');
    await loadDetailWorkflow(detailPlan.value.id);
  } finally {
    manualUsageSaving.value = false;
  }
}

watch(detailVisible, (visible) => {
  if (!visible) releaseDetailPhotos();
});
const pagination = reactive({ page: 1, pageSize: 20, total: 0 });
const stats = reactive({
  waitingCount: 0,
  overdueCount: 0,
  processingCount: 0,
  waitingNoticeCount: 0,
  urgentCount: 0,
  todayFinished: 0,
  tomorrowWaitingCount: 0,
  processingPlanTotalCount: 0
});
const todayAllCount = computed(
  () => stats.waitingCount + stats.processingCount + stats.todayFinished
);
const delayForm = reactive({ scheduleType: SCHEDULE_TYPES.DATE, processDate: '' });
const quickEditForm = reactive({
  notifyStatus: NOTIFY_STATUS.PENDING,
  notifyType: null,
  paymentStatus: PAYMENT_STATUS.PAID,
  pickupMethod: PICKUP_METHOD_OPTIONS[0].value,
  expressAddress: ''
});
const form = reactive({
  status: null,
  prescriptionId: null,
  processTypeId: null,
  batchNo: 1,
  totalDose: 1,
  bagCount: null,
  volumeMl: null,
  usageMethod: '',
  scheduleType: SCHEDULE_TYPES.DATE,
  processDate: todayText(),
  priority: PRIORITY.NORMAL,
  notifyType: null,
  notifyStatus: NOTIFY_STATUS.PENDING,
  paymentStatus: PAYMENT_STATUS.PAID,
  pickupMethod: PICKUP_METHOD_OPTIONS[0].value,
  expressAddress: '',
  processRemark: '',
  remark: ''
});
const formIsDecoction = computed(() => isDecoctionProcessType(form.processTypeId));
const batchFormIsDecoction = computed(() => isDecoctionProcessType(batchForm.processTypeId));
const metadataOnlyEdit = computed(() =>
  [PROCESSING_STATUS.FINISHED, PROCESSING_STATUS.READY_PICKUP].includes(form.status)
);
const batchForm = reactive({
  prescriptionMode: 'existing',
  prescriptionId: null,
  totalDose: 1,
  batchCount: 1,
  processTypeId: null,
  pickupMethod: PICKUP_METHOD_OPTIONS[0].value,
  expressAddress: '',
  bagsPerDose: 2,
  volumeMl: 200,
  usageMethod: '',
  notifyType: null,
  paymentStatus: PAYMENT_STATUS.PAID,
  prescription: {
    storeId: null,
    customerName: '',
    phone: '',
    doctorId: null,
    sourceId: null,
    isExternal: false,
    externalHospital: '',
    externalDoctor: '',
    externalRemark: '',
    remark: ''
  },
  plans: []
});

const notifyTypes = ref([]);
let prescriptionSearchSequence = 0;
const selectedDateText = computed(() => `${dateText(calendarDate.value)} 加工计划`);
const canEditQueue = computed(
  () =>
    mode.value === 'list' &&
    activeView.value === 'today-waiting' &&
    (!userStore.isSuperAdmin || Boolean(query.storeId))
);
const scheduleDialogTitle = '修改计划开工 / 延期';
const pickupDrawerTitle = computed(() => {
  const titles = { detail: '包裹详情', edit: '编辑包裹', verify: '包裹核销' };
  return titles[pickupDrawerMode.value] || '包裹';
});
const quickEditTitle = computed(() => {
  const titles = {
    notification: '修改通知状态',
    payment: '修改收费状态',
    pickup: '修改取货方式'
  };
  return titles[quickEditType.value] || '快捷修改';
});
const statusQuickInfo = computed(() => {
  const row = statusQuickPlan.value;
  if (!row) return { label: '-', type: 'primary', confirmText: '继续' };
  if (row.status === PROCESSING_STATUS.WAITING) {
    return row.scheduleType === SCHEDULE_TYPES.NOTICE
      ? { label: '收到顾客通知并安排开工日期', type: 'success', confirmText: '收到通知' }
      : { label: '开始加工', type: 'primary', confirmText: '开始加工' };
  }
  if (row.status === PROCESSING_STATUS.PROCESSING)
    return { label: '加工完成', type: 'success', confirmText: '加工完成' };
  if (row.status === PROCESSING_STATUS.FINISHED)
    return { label: '生成待领取包裹', type: 'warning', confirmText: '生成包裹' };
  if (row.status === PROCESSING_STATUS.READY_PICKUP)
    return { label: '核销关联包裹', type: 'warning', confirmText: '去核销' };
  return { label: '当前状态不可操作', type: 'info', confirmText: '确定' };
});

function dateText(value) {
  const date = new Date(value);
  const pad = (number) => String(number).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}
function todayText(offset = 0) {
  const date = new Date();
  date.setDate(date.getDate() + offset);
  return dateText(date);
}
function monthText(value) {
  return dateText(value).slice(0, 7);
}
function currentStoreId() {
  return userStore.isSuperAdmin ? query.storeId || undefined : undefined;
}
function notifyTypeText(value) {
  return notifyTypes.value.find((item) => item.value === value)?.label || value || '不提醒';
}
function isDecoctionProcessType(processTypeId) {
  return (
    processTypes.value.find((item) => Number(item.id) === Number(processTypeId))?.code ===
    PROCESS_TYPE_CODES.DECOCTION
  );
}
function isDecoctionPlan(plan) {
  return plan?.processType?.code === PROCESS_TYPE_CODES.DECOCTION;
}
function batchBagCount(plan) {
  if (!batchForm.plans.includes(plan)) return Number(plan?.bagCount || 0);
  return Number(plan?.totalDose || 0) * Number(batchForm.bagsPerDose || 0);
}
function syncBatchPlan(plan) {
  if (!batchFormIsDecoction.value) {
    plan.bagCount = null;
    plan.volumeMl = null;
    return;
  }
  plan.processTypeId = batchForm.processTypeId;
  plan.pickupMethod = batchForm.pickupMethod;
  plan.expressAddress = batchForm.expressAddress;
  plan.usageMethod = batchForm.usageMethod;
  plan.notifyType = batchForm.notifyType;
  plan.paymentStatus = batchForm.paymentStatus;
  plan.volumeMl = Number(batchForm.volumeMl) || null;
  plan.bagCount = batchBagCount(plan);
}
function syncAllBatchBags() {
  batchForm.plans.forEach((plan) => syncBatchPlan(plan));
}
function syncBatchSettings() {
  batchForm.plans.forEach((plan) => {
    plan.processTypeId = batchForm.processTypeId;
    plan.pickupMethod = batchForm.pickupMethod;
    plan.expressAddress = batchForm.expressAddress;
    plan.usageMethod = batchForm.usageMethod;
    plan.notifyType = batchForm.notifyType;
    plan.paymentStatus = batchForm.paymentStatus;
    plan.volumeMl = batchFormIsDecoction.value ? Number(batchForm.volumeMl) || null : null;
    plan.bagCount = batchFormIsDecoction.value ? batchBagCount(plan) : null;
  });
}
function decoctionFields(plan) {
  if (!isDecoctionProcessType(plan.processTypeId)) return { bagCount: null, volumeMl: null };
  return { bagCount: Number(plan.bagCount), volumeMl: Number(plan.volumeMl) };
}
function validateDecoctionFields(plan, prefix = '') {
  if (!isDecoctionProcessType(plan.processTypeId)) return '';
  const bagCount = batchBagCount(plan);
  if (!Number.isInteger(Number(bagCount)) || Number(bagCount) <= 0) {
    return `${prefix}袋数必须为正整数`;
  }
  if (!Number.isInteger(Number(plan.volumeMl)) || Number(plan.volumeMl) <= 0) {
    return `${prefix}毫升数必须为正整数`;
  }
  return '';
}
function createBatchPlan(batchNo, source = {}) {
  return {
    batchNo,
    processTypeId: source.processTypeId ?? null,
    totalDose: source.totalDose ?? 1,
    bagCount: source.bagCount ?? null,
    volumeMl: source.volumeMl ?? null,
    usageMethod: source.usageMethod || '',
    scheduleType: source.scheduleType || SCHEDULE_TYPES.DATE,
    processDate:
      source.scheduleType === SCHEDULE_TYPES.NOTICE ? '' : source.processDate || todayText(),
    priority: source.priority ?? PRIORITY.NORMAL,
    notifyType: source.notifyType ?? null,
    paymentStatus: source.paymentStatus ?? PAYMENT_STATUS.PAID,
    pickupMethod: source.pickupMethod ?? PICKUP_METHOD_OPTIONS[0].value,
    expressAddress: source.expressAddress || '',
    processRemark: source.processRemark || '',
    remark: source.remark || ''
  };
}
function resetBatchForm() {
  Object.assign(batchForm, {
    prescriptionMode: 'existing',
    prescriptionId: null,
    totalDose: 1,
    batchCount: 1,
    processTypeId: null,
    pickupMethod: PICKUP_METHOD_OPTIONS[0].value,
    expressAddress: '',
    bagsPerDose: 2,
    volumeMl: 200,
    usageMethod: '',
    notifyType: null,
    paymentStatus: PAYMENT_STATUS.PAID,
    prescription: {
      storeId: null,
      customerName: '',
      phone: '',
      doctorId: null,
      sourceId: null,
      isExternal: false,
      externalHospital: '',
      externalDoctor: '',
      externalRemark: '',
      remark: ''
    },
    plans: [createBatchPlan(1)]
  });
}
function generateBatchPlans() {
  const count = Math.min(Math.max(Number(batchForm.batchCount) || 1, 1), 100);
  const firstPlan = batchForm.plans[0] || createBatchPlan(1);
  const doseBatches = splitDoseBatches(batchForm.totalDose, count, todayText());
  batchForm.batchCount = count;
  batchForm.plans = doseBatches.map((doseBatch, index) => {
    const plan = createBatchPlan(index + 1, batchForm.plans[index] || firstPlan);
    plan.totalDose = doseBatch.totalDose;
    plan.processDate = doseBatch.processDate;
    plan.scheduleType = SCHEDULE_TYPES.DATE;
    return plan;
  });
  syncBatchSettings();
}
function addBatchPlan() {
  batchForm.batchCount = Math.min(batchForm.plans.length + 1, 100);
  generateBatchPlans();
}
function removeBatchPlan(index) {
  if (batchForm.plans.length === 1) return;
  batchForm.plans.splice(index, 1);
  batchForm.plans.forEach((plan, planIndex) => {
    plan.batchNo = planIndex + 1;
  });
  batchForm.batchCount = batchForm.plans.length;
  generateBatchPlans();
}
function copyPreviousBatch(index) {
  if (index <= 0) return ElMessage.warning('第一批没有上一批可复制');
  const currentBatchNo = batchForm.plans[index].batchNo;
  Object.assign(
    batchForm.plans[index],
    createBatchPlan(currentBatchNo, batchForm.plans[index - 1]),
    {
      batchNo: currentBatchNo
    }
  );
}

async function loadStats() {
  Object.assign(stats, await getStats({ storeId: currentStoreId() }));
}
async function load() {
  loading.value = true;
  try {
    const data = await getProcessingPlans({
      ...(activeView.value === 'all' ? query : { storeId: currentStoreId() }),
      view: activeView.value,
      page: pagination.page,
      pageSize: pagination.pageSize
    });
    list.value = data?.list || [];
    pagination.total = data?.pagination?.total || 0;
    await nextTick();
    bindDragRows();
  } finally {
    loading.value = false;
  }
}
async function loadCalendar() {
  calendarCounts.value = await getProcessingCalendar({
    month: monthText(calendarDate.value),
    storeId: currentStoreId()
  });
  await loadCalendarDay();
}
async function loadCalendarDay() {
  calendarLoading.value = true;
  try {
    const data = await getProcessingPlans({
      processDate: dateText(calendarDate.value),
      storeId: currentStoreId(),
      page: 1,
      pageSize: 100
    });
    calendarList.value = data?.list || [];
  } finally {
    calendarLoading.value = false;
  }
}
async function reloadAll() {
  const viewTask =
    mode.value === 'calendar' ? loadCalendar() : mode.value === 'list' ? load() : Promise.resolve();
  await Promise.all([loadStats(), viewTask]);
}
function changeMode(value) {
  router.replace({ query: value === 'list' ? {} : { mode: value } });
  if (value === 'calendar') loadCalendar();
  if (value === 'list') load();
}
function selectView(view) {
  mode.value = 'list';
  activeView.value = view;
  pagination.page = 1;
  load();
}
function search() {
  pagination.page = 1;
  reloadAll();
}
function resetFilters() {
  Object.assign(query, { keyword: '', status: '', processTypeId: '', doctorId: '', storeId: '' });
  search();
}

function resetForm() {
  Object.assign(form, {
    status: null,
    prescriptionId: null,
    processTypeId: null,
    batchNo: 1,
    totalDose: 1,
    bagCount: null,
    volumeMl: null,
    usageMethod: '',
    scheduleType: SCHEDULE_TYPES.DATE,
    processDate: todayText(),
    priority: PRIORITY.NORMAL,
    notifyType: null,
    notifyStatus: NOTIFY_STATUS.PENDING,
    paymentStatus: PAYMENT_STATUS.PAID,
    pickupMethod: PICKUP_METHOD_OPTIONS[0].value,
    expressAddress: '',
    processRemark: '',
    remark: ''
  });
}
async function loadPrescriptionOptions(keyword = '') {
  const sequence = ++prescriptionSearchSequence;
  prescriptionLoading.value = true;
  try {
    const normalizedKeyword = String(keyword || '').trim();
    const params = {
      page: 1,
      pageSize: 50,
      status: 0,
      keyword: normalizedKeyword || undefined,
      storeId: currentStoreId()
    };
    // Keep the default picker focused on today's active prescriptions; keyword search spans all dates.
    if (!normalizedKeyword) params.createdDate = todayText();
    const data = await getPrescriptions(params);
    if (sequence === prescriptionSearchSequence) prescriptions.value = data?.list || [];
  } finally {
    if (sequence === prescriptionSearchSequence) prescriptionLoading.value = false;
  }
}

function onPrescriptionVisibleChange(visible) {
  if (visible) loadPrescriptionOptions();
}

function openCreate() {
  editingId.value = null;
  resetForm();
  resetBatchForm();
  loadPrescriptionOptions();
  formVisible.value = true;
}
function openEdit(row) {
  editingId.value = row.id;
  Object.assign(form, {
    status: row.status,
    prescriptionId: row.prescriptionId,
    processTypeId: row.processTypeId,
    batchNo: row.batchNo,
    totalDose: row.totalDose,
    bagCount: row.bagCount ?? null,
    volumeMl: row.volumeMl ?? null,
    usageMethod: row.usageMethod || '',
    scheduleType: row.scheduleType,
    processDate: row.processDate ? String(row.processDate).slice(0, 10) : '',
    priority: row.priority ?? PRIORITY.NORMAL,
    notifyType: row.notifyType ?? null,
    notifyStatus: Number(row.notifyStatus ?? NOTIFY_STATUS.PENDING),
    paymentStatus: row.paymentStatus,
    pickupMethod: row.pickupMethod ?? PICKUP_METHOD_OPTIONS[0].value,
    expressAddress: row.expressAddress || '',
    processRemark: row.processRemark || '',
    remark: row.remark || ''
  });
  if (row.prescription && !prescriptions.value.some((item) => item.id === row.prescription.id)) {
    prescriptions.value = [row.prescription, ...prescriptions.value];
  }
  formVisible.value = true;
}
async function savePlan() {
  if (!editingId.value) return saveBatchPlan();
  if (!metadataOnlyEdit.value) {
    if (!form.prescriptionId || !form.processTypeId)
      return ElMessage.warning('请选择处方和加工方式');
    const decoctionError = validateDecoctionFields(form);
    if (decoctionError) return ElMessage.warning(decoctionError);
    if (form.scheduleType === SCHEDULE_TYPES.DATE && !form.processDate)
      return ElMessage.warning('请选择加工日期');
  }
  saving.value = true;
  try {
    const payload = metadataOnlyEdit.value
      ? {
          notifyType: form.notifyType,
          notifyStatus: form.notifyStatus,
          paymentStatus: form.paymentStatus,
          usageMethod: form.usageMethod,
          pickupMethod: form.pickupMethod,
          expressAddress: form.expressAddress
        }
      : {
          ...form,
          status: undefined,
          ...decoctionFields(form),
          processDate: form.scheduleType === SCHEDULE_TYPES.DATE ? form.processDate : null
        };
    editingId.value
      ? await updateProcessingPlan(editingId.value, payload)
      : await createProcessingPlan(payload);
    formVisible.value = false;
    ElMessage.success(editingId.value ? '加工计划已更新' : '加工计划已创建');
    await reloadAll();
  } finally {
    saving.value = false;
  }
}
async function saveBatchPlan() {
  if (batchForm.prescriptionMode === 'existing' && !batchForm.prescriptionId) {
    return ElMessage.warning('请选择处方');
  }
  if (batchForm.prescriptionMode === 'new') {
    const prescription = batchForm.prescription;
    if (userStore.isSuperAdmin && !prescription.storeId) return ElMessage.warning('请选择所属门店');
    if (!prescription.customerName) return ElMessage.warning('请输入顾客姓名');
    if (prescription.phone && !isValidPhone(prescription.phone)) {
      return ElMessage.warning('请输入正确的手机号');
    }
    if (!prescription.doctorId) return ElMessage.warning('请选择医生');
    if (!prescription.sourceId) return ElMessage.warning('请选择处方来源');
  }
  if (!batchForm.plans.length) return ElMessage.warning('请至少添加一个加工批次');
  if (!batchForm.processTypeId) return ElMessage.warning('请选择统一的加工方式');
  if (batchForm.pickupMethod == null || batchForm.pickupMethod === '') {
    return ElMessage.warning('请选择统一的取货方式');
  }
  syncBatchSettings();
  if (batchFormIsDecoction.value) {
    if (!Number.isInteger(Number(batchForm.bagsPerDose)) || Number(batchForm.bagsPerDose) <= 0) {
      return ElMessage.warning('每剂袋数必须为正整数');
    }
    if (!Number.isInteger(Number(batchForm.volumeMl)) || Number(batchForm.volumeMl) <= 0) {
      return ElMessage.warning('每袋毫升数必须为正整数');
    }
  }
  const totalDose = batchForm.plans.reduce((sum, plan) => sum + Number(plan.totalDose || 0), 0);
  if (totalDose !== Number(batchForm.totalDose)) {
    return ElMessage.warning(`批次剂数合计必须等于 ${batchForm.totalDose} 剂`);
  }

  const batchNos = new Set();
  for (const plan of batchForm.plans) {
    const batchNo = Number(plan.batchNo);
    if (!Number.isInteger(batchNo) || batchNo <= 0) return ElMessage.warning('批次号必须为正整数');
    if (batchNos.has(batchNo)) return ElMessage.warning(`第 ${batchNo} 批重复，请调整批次号`);
    batchNos.add(batchNo);
    if (!Number.isInteger(Number(plan.totalDose)) || Number(plan.totalDose) <= 0) {
      return ElMessage.warning(`请填写第 ${batchNo} 批剂数`);
    }
    const decoctionError = validateDecoctionFields(plan, `第 ${batchNo} 批`);
    if (decoctionError) return ElMessage.warning(decoctionError);
    if (plan.scheduleType === SCHEDULE_TYPES.DATE && !plan.processDate) {
      return ElMessage.warning(`请选择第 ${batchNo} 批计划日期`);
    }
  }

  saving.value = true;
  try {
    await createProcessingPlanBatch({
      prescriptionMode: batchForm.prescriptionMode,
      prescriptionId:
        batchForm.prescriptionMode === 'existing' ? batchForm.prescriptionId : undefined,
      prescription:
        batchForm.prescriptionMode === 'new'
          ? {
              ...batchForm.prescription,
              storeId: userStore.isSuperAdmin ? batchForm.prescription.storeId : undefined
            }
          : undefined,
      plans: batchForm.plans.map((plan) => ({
        ...plan,
        processTypeId: batchForm.processTypeId,
        pickupMethod: batchForm.pickupMethod,
        expressAddress: batchForm.expressAddress,
        usageMethod: batchForm.usageMethod,
        notifyType: batchForm.notifyType,
        paymentStatus: batchForm.paymentStatus,
        ...(batchFormIsDecoction.value
          ? { bagCount: batchBagCount(plan), volumeMl: Number(batchForm.volumeMl) }
          : { bagCount: null, volumeMl: null }),
        batchNo: Number(plan.batchNo),
        totalDose: Number(plan.totalDose),
        processDate: plan.scheduleType === SCHEDULE_TYPES.DATE ? plan.processDate : null
      }))
    });
    formVisible.value = false;
    ElMessage.success(`已创建 ${batchForm.plans.length} 个加工批次`);
    await reloadAll();
  } finally {
    saving.value = false;
  }
}

async function finishPlan(row) {
  finishingPlan.value = row;
  finishDialogVisible.value = true;
}

async function completePlan(createPackage) {
  const row = finishingPlan.value;
  if (!row || finishing.value) return;
  finishing.value = true;
  finishDialogVisible.value = false;
  await nextTick();
  let notified = false;
  try {
    if (row.notifyTypeDictionary?.code !== 'NONE') {
      try {
        await ElMessageBox.confirm('是否已通知顾客？', '加工完成', {
          confirmButtonText: '已通知',
          cancelButtonText: '稍后通知',
          distinguishCancelAndClose: true,
          type: 'info'
        });
        notified = true;
      } catch (action) {
        if (action !== 'cancel') return;
      }
    }
    await transitionProcessingPlan(row.id, PROCESSING_STATUS.FINISHED, {
      createPackage,
      notifyStatus: notified ? NOTIFY_STATUS.NOTIFIED : NOTIFY_STATUS.PENDING,
      notifyTime: notified ? new Date().toISOString() : null
    });
    ElMessage.success(
      createPackage ? '加工完成，已生成包裹并进入待领取' : '加工完成，暂未生成包裹'
    );
    finishingPlan.value = null;
    await reloadAll();
  } finally {
    finishing.value = false;
  }
}

async function generatePackage(row) {
  try {
    await ElMessageBox.confirm(
      `确认为“${row.prescription?.customerName || '该顾客'}”的${row.processType?.name || '加工计划'}生成待取包裹吗？`,
      '生成待取包裹',
      {
        confirmButtonText: '生成包裹',
        cancelButtonText: '取消',
        type: 'warning'
      }
    );
  } catch {
    return;
  }
  await generateProcessingPlanPackage(row.id);
  ElMessage.success('包裹已生成并进入待领取');
  await reloadAll();
}

function openPickupPackageDrawer(mode, row = null) {
  pickupDrawerMode.value = mode;
  pickupPackageId.value = row?.id || null;
  pickupPackageCode.value = row?.pickupCode || '';
  pickupDrawerVisible.value = true;
}

function closePickupPackageDrawer() {
  pickupDrawerVisible.value = false;
}

function printPickupPackageLabel() {
  pickupPackageDetailRef.value?.openPrint();
}

function handleUsageMethodSaved(usageMethod) {
  if (detailPlan.value) detailPlan.value.usageMethod = usageMethod;
}

function openPlanPrint(type) {
  planPrintType.value =
    type === 'pickup' ? 'PACKAGE_PICKUP' : type === 'packaging' ? 'PACKAGING' : 'PROCESSING';
  planPrintVisible.value = true;
}

function openQuickEdit(type, row) {
  if (
    ![
      PROCESSING_STATUS.WAITING,
      PROCESSING_STATUS.PROCESSING,
      PROCESSING_STATUS.FINISHED,
      PROCESSING_STATUS.READY_PICKUP
    ].includes(row.status)
  ) {
    ElMessage.warning('当前计划不能修改');
    return;
  }
  quickEditType.value = type;
  quickEditPlan.value = row;
  Object.assign(quickEditForm, {
    notifyStatus: Number(row.notifyStatus ?? NOTIFY_STATUS.PENDING),
    notifyType: row.notifyType ?? null,
    paymentStatus: Number(row.paymentStatus ?? PAYMENT_STATUS.PAID),
    pickupMethod: row.pickupMethod ?? PICKUP_METHOD_OPTIONS[0].value,
    expressAddress: row.expressAddress || ''
  });
  quickEditVisible.value = true;
}

function openStatusQuick(row) {
  if (
    ![
      PROCESSING_STATUS.WAITING,
      PROCESSING_STATUS.PROCESSING,
      PROCESSING_STATUS.FINISHED,
      PROCESSING_STATUS.READY_PICKUP
    ].includes(row.status) ||
    (row.status === PROCESSING_STATUS.READY_PICKUP && !row.package)
  ) {
    ElMessage.info('当前状态请使用对应业务操作');
    return;
  }
  statusQuickPlan.value = row;
  statusQuickVisible.value = true;
}

async function submitStatusQuick() {
  const row = statusQuickPlan.value;
  if (!row) return;
  statusQuickVisible.value = false;
  if (row.status === PROCESSING_STATUS.READY_PICKUP) {
    openPickupPackageDrawer('verify', row.package);
    return;
  }
  if (row.status === PROCESSING_STATUS.WAITING && row.scheduleType === SCHEDULE_TYPES.NOTICE) {
    await handleAction('notice', row);
    return;
  }
  if (row.status === PROCESSING_STATUS.WAITING) {
    await handleAction('start', row);
    return;
  }
  if (row.status === PROCESSING_STATUS.PROCESSING) {
    await handleAction('finish', row);
    return;
  }
  if (row.status === PROCESSING_STATUS.FINISHED) {
    await handleAction('generate-package', row);
  }
}

function openScheduleDialog(row) {
  if (row.status !== PROCESSING_STATUS.WAITING) {
    ElMessage.warning('只有待加工计划可以修改计划开工');
    return;
  }
  selectedPlan.value = row;
  Object.assign(delayForm, {
    scheduleType: row.scheduleType || SCHEDULE_TYPES.DATE,
    processDate: row.processDate ? String(row.processDate).slice(0, 10) : todayText()
  });
  delayVisible.value = true;
}

async function submitQuickEdit() {
  const row = quickEditPlan.value;
  if (!row || quickEditLoading.value) return;
  const payloadMap = {
    notification: {
      notifyStatus: quickEditForm.notifyStatus,
      notifyType: quickEditForm.notifyType
    },
    payment: {
      paymentStatus: quickEditForm.paymentStatus
    },
    pickup: {
      pickupMethod: quickEditForm.pickupMethod,
      expressAddress: quickEditForm.expressAddress
    }
  };
  const payload = payloadMap[quickEditType.value];
  if (!payload) return;
  quickEditLoading.value = true;
  try {
    await updateProcessingPlan(row.id, payload);
    quickEditVisible.value = false;
    ElMessage.success('修改已保存');
    await reloadAll();
  } finally {
    quickEditLoading.value = false;
  }
}

async function handlePickupDrawerSaved() {
  closePickupPackageDrawer();
  await reloadAll();
}

async function handlePickupDrawerVerified() {
  closePickupPackageDrawer();
  await reloadAll();
}

async function handleAction(name, row) {
  if (name === 'detail') {
    detailPlan.value = row;
    detailVisible.value = true;
    await loadDetailWorkflow(row.id);
    return;
  }
  if (name === 'quick-notification') return openQuickEdit('notification', row);
  if (name === 'quick-payment') return openQuickEdit('payment', row);
  if (name === 'quick-pickup') return openQuickEdit('pickup', row);
  if (name === 'quick-schedule') return openScheduleDialog(row);
  if (name === 'quick-status') return openStatusQuick(row);
  if (name === 'edit') return openEdit(row);
  if (name === 'finish') return finishPlan(row);
  if (name === 'generate-package') return generatePackage(row);
  if (name === 'cancel-processing') {
    if (
      row.status !== PROCESSING_STATUS.PROCESSING ||
      Number(row.currentStage) !== 1 ||
      row.dispensingCompletedAt
    ) {
      ElMessage.warning('只有尚未完成调配的加工计划可以取消');
      return;
    }
    try {
      await ElMessageBox.confirm(
        `确认取消“${row.prescription?.customerName || '该顾客'}”的${row.processType?.name || '加工计划'}吗？`,
        '取消加工',
        {
          confirmButtonText: '确认取消',
          cancelButtonText: '返回',
          type: 'warning'
        }
      );
    } catch {
      return;
    }
    await transitionProcessingPlan(row.id, PROCESSING_STATUS.CANCELLED);
    ElMessage.success('加工已取消');
    return reloadAll();
  }
  if (name === 'start') {
    try {
      await ElMessageBox.confirm(
        `确认开始加工“${row.prescription?.customerName || '该顾客'}”的${row.processType?.name || '加工计划'} ${row.totalDose}剂吗？`,
        '确认开始加工',
        {
          confirmButtonText: '确认开始',
          cancelButtonText: '取消',
          type: 'warning'
        }
      );
    } catch {
      return;
    }
    await transitionProcessingPlan(row.id, PROCESSING_STATUS.PROCESSING);
    ElMessage.success('已开始加工');
    return reloadAll();
  }
  if (name === 'delete') {
    await ElMessageBox.confirm('删除后历史记录仍会保留，确认继续？', '删除加工计划', {
      type: 'warning'
    });
    await deleteProcessingPlan(row.id);
    return reloadAll();
  }
  if (name === 'delay') {
    openScheduleDialog(row);
  }
  if (name === 'notice') {
    selectedPlan.value = row;
    noticePreset.value = 'today';
    noticeDate.value = todayText();
    noticeVisible.value = true;
  }
}
async function submitDelay() {
  if (delayForm.scheduleType === SCHEDULE_TYPES.DATE && !delayForm.processDate)
    return ElMessage.warning('请选择计划开工日期');
  const payload = {
    scheduleType: delayForm.scheduleType,
    processDate: delayForm.scheduleType === SCHEDULE_TYPES.DATE ? delayForm.processDate : null
  };
  await updateProcessingPlan(selectedPlan.value.id, payload);
  delayVisible.value = false;
  ElMessage.success('计划开工 / 延期安排已保存');
  await reloadAll();
}
function applyNoticePreset(value) {
  if (value === 'today') noticeDate.value = todayText();
  if (value === 'tomorrow') noticeDate.value = todayText(1);
  if (value === 'custom') noticeDate.value = '';
}
async function submitNotice() {
  if (!noticeDate.value) return ElMessage.warning('请选择开始加工日期');
  await receiveProcessingNotice(selectedPlan.value.id, { processDate: noticeDate.value });
  noticeVisible.value = false;
  ElMessage.success('已加入加工日程');
  await reloadAll();
}

async function saveManualQueue(row) {
  await reorderProcessingQueue({ id: row.id, queueOrder: row.queueOrder });
  await load();
}
async function restoreQueue() {
  await restoreProcessingQueue({ processDate: todayText(), storeId: currentStoreId() });
  ElMessage.success('已恢复默认排序');
  await load();
}
function bindDragRows() {
  if (!canEditQueue.value) return;
  const rows = document.querySelectorAll('.plan-table .el-table__body-wrapper tbody tr');
  rows.forEach((element, index) => {
    const plan = list.value[index];
    if (!plan || String(plan.processDate || '').slice(0, 10) !== todayText()) return;
    element.draggable = true;
    element.dataset.index = String(index);
    element.ondragstart = (event) => event.dataTransfer.setData('text/plain', String(index));
    element.ondragover = (event) => event.preventDefault();
    element.ondrop = async (event) => {
      event.preventDefault();
      const from = Number(event.dataTransfer.getData('text/plain'));
      const to = Number(element.dataset.index);
      if (from === to || !Number.isInteger(from)) return;
      const source = list.value[from];
      if (!source || String(source.processDate || '').slice(0, 10) !== todayText()) return;
      const rowsCopy = [...list.value];
      const [moved] = rowsCopy.splice(from, 1);
      rowsCopy.splice(to, 0, moved);
      list.value = rowsCopy;
      const todayIds = rowsCopy
        .filter((item) => String(item.processDate || '').slice(0, 10) === todayText())
        .map((item) => item.id);
      await reorderProcessingQueue({ ids: todayIds, startOrder: 1 });
      await load();
    };
  });
}

watch(() => [pagination.page, pagination.pageSize], load);
watch(calendarDate, (current, previous) => {
  monthText(current) === monthText(previous) ? loadCalendarDay() : loadCalendar();
});

onMounted(async () => {
  const tasks = [
    getDictionaries('ProcessType'),
    getDictionaries('NotifyType'),
    getDoctors(),
    getDictionaries('PrescriptionSource')
  ];
  if (userStore.isSuperAdmin) tasks.push(getStores({ page: 1, pageSize: 100 }));
  const [processData, notifyData, doctorData, sourceData, storeData] = await Promise.all(tasks);
  processTypes.value = processData || [];
  doctors.value = doctorData || [];
  sources.value = sourceData || [];
  stores.value = storeData?.list || [];
  if (userStore.isSuperAdmin && route.query.storeId) query.storeId = Number(route.query.storeId);
  const mappedNotifyTypes = (notifyData || []).map((item) => ({
    label: item.name,
    value: item.id,
    code: item.code
  }));
  if (mappedNotifyTypes.length) {
    notifyTypes.value = mappedNotifyTypes;
    const noneId = mappedNotifyTypes.find((item) => item.code === 'NONE')?.value ?? null;
    if (form.notifyType === null) form.notifyType = noneId;
    if (quickEditForm.notifyType === null) quickEditForm.notifyType = noneId;
  }
  await loadPrescriptionOptions();
  await reloadAll();
});
</script>

<style scoped>
.workflow-detail-section {
  margin-top: 24px;
}
.workflow-detail-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}
.workflow-detail-heading h3 {
  margin: 0;
  font-size: 16px;
}
.workflow-detail-heading span {
  color: var(--app-muted);
  font-size: 13px;
}
.workflow-heading-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.manual-usage-form {
  margin-top: 18px;
}
.manual-usage-form :deep(.el-select),
.manual-usage-form :deep(.el-date-editor) {
  width: 100%;
}
.workflow-photo-tools {
  display: flex;
  align-items: center;
  gap: 10px;
}
.workflow-photo-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}
.workflow-photo-item {
  position: relative;
  min-width: 0;
}
.workflow-photo-grid :deep(.el-image) {
  width: 100%;
  aspect-ratio: 4 / 3;
  border: 1px solid var(--app-border);
  border-radius: 6px;
  cursor: zoom-in;
}
.workflow-photo-delete {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 1;
}
.drawer-header {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 12px;
}

.page-actions,
.next-task-header,
.queue-cell,
.table-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.stat-grid {
  display: grid;
  grid-template-columns: repeat(9, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 12px;
}
.stat-action {
  cursor: pointer;
}
.stat-action:hover :deep(.el-card) {
  border-color: var(--el-color-primary-light-5);
}
.filters {
  margin-bottom: -18px;
}
.filters :deep(.el-input),
.filters :deep(.el-select) {
  width: 190px;
}
.queue-cell {
  justify-content: center;
}
.queue-cell :deep(.el-input-number) {
  width: 52px;
}
.secondary-text {
  margin-top: 3px;
  color: var(--app-muted);
  font-size: 12px;
}
.finish-dialog-text {
  margin: 0;
  color: var(--el-text-color-primary);
  line-height: 1.7;
}
.finish-dialog-tip {
  margin: 12px 0 0;
  color: var(--app-muted);
  font-size: 13px;
  line-height: 1.6;
}
.plan-table :deep(.el-table__row[draggable='true']) {
  cursor: grab;
}
.plan-table :deep(.cell) {
  min-width: 0;
}
.plan-table :deep(.el-table__cell) {
  white-space: normal;
}
.plan-table :deep(.quick-edit-tag) {
  cursor: pointer;
  transition:
    border-color 0.2s,
    box-shadow 0.2s;
}
.plan-table :deep(.quick-edit-tag:hover),
.plan-table :deep(.quick-edit-tag:focus-visible) {
  border-color: currentColor;
  box-shadow: 0 0 0 2px var(--el-color-primary-light-8);
  outline: none;
}
.plan-table :deep(.table-actions) {
  flex-wrap: wrap;
  justify-content: center;
  gap: 2px;
}
.plan-table :deep(.table-actions .el-button + .el-button) {
  margin-left: 0;
}
.calendar-layout {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.calendar-cell {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  height: 100%;
}
.calendar-workbench :deep(.el-calendar__body) {
  padding: 8px 0 0;
}
.calendar-workbench :deep(.el-calendar-table .el-calendar-day) {
  height: 36px;
  padding: 6px;
}
.calendar-workbench :deep(.el-calendar-table td) {
  border-color: var(--app-border);
}
.calendar-cell strong {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
  height: 24px;
  border-radius: 4px;
  color: #ffffff;
  background: #2563eb;
  font-size: 12px;
}
.calendar-day-plans {
  min-width: 0;
  border-top: 1px solid var(--app-border);
  padding-top: 20px;
}
.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.section-heading h2 {
  margin: 0 0 4px;
  font-size: 18px;
}
.section-heading span {
  color: var(--app-muted);
  font-size: 13px;
}
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  column-gap: 16px;
}
.form-item-wide {
  grid-column: 1 / -1;
}
.form-grid :deep(.el-select),
.form-grid :deep(.el-date-editor) {
  width: 100%;
}
.batch-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.batch-section {
  border-radius: 8px;
}
.batch-section-header,
.batch-tools {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.batch-tools {
  justify-content: flex-end;
  flex-wrap: wrap;
}
.batch-unified-settings {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-bottom: 16px;
  padding: 16px;
  border: 1px solid var(--app-border);
  border-radius: 6px;
  background: var(--el-fill-color-lighter);
}
.batch-unified-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}
.batch-unified-grid-main {
  grid-template-columns: repeat(6, minmax(120px, 150px));
}
.batch-unified-grid-secondary {
  grid-template-columns: minmax(0, 1fr) repeat(2, minmax(0, 2fr));
}
.batch-count-label {
  color: var(--app-muted);
  font-size: 13px;
}
.batch-plan-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.batch-plan-item {
  overflow: hidden;
  border: 1px solid var(--app-border);
  border-radius: 6px;
  background: var(--el-fill-color-blank);
}
.batch-plan-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 44px;
  padding: 0 16px;
  border-bottom: 1px solid var(--app-border);
  background: var(--el-fill-color-light);
}
.batch-plan-header > div,
.batch-plan-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.batch-plan-header span {
  color: var(--app-muted);
  font-size: 12px;
}
.batch-plan-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
.batch-plan-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  padding: 16px;
}
.batch-field {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 8px;
}
.batch-field-wide {
  grid-column: span 2;
}
.batch-field-label {
  color: var(--el-text-color-regular);
  font-size: 14px;
  line-height: 22px;
}
.batch-field-label.required::before {
  margin-right: 4px;
  color: var(--el-color-danger);
  content: '*';
}
.batch-field :deep(.el-input-number),
.batch-field :deep(.el-date-editor),
.batch-field :deep(.el-select) {
  width: 100%;
}
.switch-field {
  display: flex;
  align-items: center;
  min-height: 32px;
}
@media (max-width: 1200px) {
  .stat-grid {
    grid-template-columns: repeat(6, minmax(0, 1fr));
  }

  .calendar-day-plans {
    padding-top: 20px;
  }
}
@media (max-width: 1024px) {
  .stat-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .batch-plan-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .batch-unified-grid,
  .batch-unified-grid-secondary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .batch-unified-grid-main {
    grid-template-columns: repeat(3, minmax(120px, 150px));
  }
}
@media (max-width: 768px) {
  .page-actions,
  .form-grid {
    grid-template-columns: 1fr;
  }
  .batch-section-header {
    align-items: stretch;
    flex-direction: column;
  }
  .batch-plan-grid {
    grid-template-columns: 1fr;
  }
  .batch-unified-grid,
  .batch-unified-grid-secondary {
    grid-template-columns: 1fr;
  }
  .batch-unified-grid-main {
    grid-template-columns: 1fr;
  }
  .batch-field-wide {
    grid-column: auto;
  }
  .page-actions {
    align-items: stretch;
    flex-direction: column;
  }
  .stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .filters,
  .filters :deep(.el-form-item),
  .filters :deep(.el-input),
  .filters :deep(.el-select) {
    width: 100%;
  }
}
</style>
