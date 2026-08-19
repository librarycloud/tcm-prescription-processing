import ExcelJS from "exceljs";
import { AppError } from "../utils/appError.js";
import { isSuperAdmin } from "../constants/roles.js";
import { assertManager, resolveBusinessStoreId } from "./permissionService.js";
import { recordOperation } from "./operationLogService.js";

const LOCATION_TYPES = new Set(["D", "G", "F", "C"]);
const IMPORT_HEADERS = ["位置编号", "药材编码", "药材名称", "规格"];
const EXPORT_HEADERS = ["位置", "药材编码", "药材名称", "规格"];
const MOVE_IMPORT_HEADERS = ["原位置", "新位置", "药材编码", "药材名称"];
const EXPORT_SHEETS = [
  ["D", "斗"],
  ["G", "柜"],
  ["F", "冰箱"],
  ["C", "仓库"],
];
const DEFAULT_DRAWER_UNIT_COUNT = 5;
const DEFAULT_DRAWER_LAYER_COLUMNS = [6, 6, 6, 6, 6, 6, 6, 3];
const DEFAULT_LAYOUT = {
  drawerUnitCount: DEFAULT_DRAWER_UNIT_COUNT,
  drawerLayerCount: 8,
  drawerLayerColumns: JSON.stringify(DEFAULT_DRAWER_LAYER_COLUMNS),
  drawerTopColumnCount: 6,
  bigCabinetUnitCount: 5,
  bigCabinetLayerCount: 3,
};

function positiveDigit(value, label) {
  const number = Number(value);
  if (!Number.isInteger(number) || number < 1 || number > 9) {
    throw new AppError(`${label}必须为1到9的一位数字`, 400);
  }
  return number;
}

function positiveLayerNumber(value, label = "层数") {
  const number = Number(value);
  if (!Number.isInteger(number) || number < 1 || number > 32767) {
    throw new AppError(`${label}必须为1到32767的正整数`, 400);
  }
  return number;
}

function layerDigit(value) {
  const number = Number(value);
  if (!Number.isInteger(number) || number < 0 || number > 9) {
    throw new AppError("药斗层数必须为0到9的一位数字，0表示顶层", 400);
  }
  return number;
}

function defaultDrawerCabinetColumns(
  unitCount,
  layerCount,
  topColumnCount = 6,
) {
  return Array.from({ length: unitCount }, () => [
    topColumnCount,
    ...Array.from({ length: layerCount }, (_, index) =>
      index === layerCount - 1 ? 3 : 6,
    ),
  ]);
}

function validColumnCount(value) {
  const number = Number(value);
  return Number.isInteger(number) && number >= 1 && number <= 9;
}

function readDrawerCabinetColumns(layout = DEFAULT_LAYOUT) {
  const unitCount =
    Number(layout.drawerUnitCount) || DEFAULT_LAYOUT.drawerUnitCount;
  const layerCount =
    Number(layout.drawerLayerCount) || DEFAULT_LAYOUT.drawerLayerCount;
  const topColumnCount =
    Number(layout.drawerTopColumnCount) || DEFAULT_LAYOUT.drawerTopColumnCount;
  const fallback = defaultDrawerCabinetColumns(
    unitCount,
    layerCount,
    topColumnCount,
  );
  try {
    const values =
      typeof layout.drawerLayerColumns === "string"
        ? JSON.parse(layout.drawerLayerColumns)
        : layout.drawerLayerColumns;
    if (
      Array.isArray(values) &&
      values.length === unitCount &&
      values.every(
        (cabinet) =>
          Array.isArray(cabinet) &&
          cabinet.length === layerCount + 1 &&
          cabinet.every(validColumnCount),
      )
    ) {
      return values.map((cabinet) => cabinet.map(Number));
    }
    if (
      Array.isArray(values) &&
      values.length === layerCount &&
      values.every(validColumnCount)
    ) {
      const legacyColumns = values.map(Number);
      return Array.from({ length: unitCount }, () => [
        topColumnCount,
        ...legacyColumns,
      ]);
    }
  } catch {
    // Layouts created before cabinet-specific columns use the standard layout.
  }
  return fallback;
}

function publicLayout(layout) {
  return { ...layout, drawerLayerColumns: readDrawerCabinetColumns(layout) };
}

export function parseLocationCode(value, layout = DEFAULT_LAYOUT) {
  const code = String(value || "")
    .trim()
    .toUpperCase();
  const compact = code.replaceAll("-", "");
  let parts;
  if (code.includes("-")) {
    parts = code.split("-");
  } else {
    const match = compact.match(/^([DGFC])(\d+)$/);
    const typeDigits = match?.[2] || "";
    if (match?.[1] === "D" && [3, 4].includes(typeDigits.length)) {
      parts = [match[1], ...typeDigits.split("")];
    } else if (match && typeDigits.length >= 2) {
      parts = [match[1], typeDigits[0], typeDigits.slice(1)];
    } else {
      parts = [];
    }
  }
  const type = parts[0];
  if (!LOCATION_TYPES.has(type)) {
    throw new AppError("位置编号必须以 D、G、F 或 C 开头", 400);
  }

  if (type === "D") {
    if (![4, 5].includes(parts.length))
      throw new AppError(
        "药斗位置格式应为 D-柜号-层-列，可加第5段格内序号",
        400,
      );
    const unitNo = positiveDigit(parts[1], "药斗柜号");
    const layerNo = layerDigit(parts[2]);
    const columnNo = positiveDigit(parts[3], "药斗列数");
    const slotNo =
      parts.length === 5 ? positiveDigit(parts[4], "格内序号") : null;
    const unitCount =
      Number(layout.drawerUnitCount) || DEFAULT_LAYOUT.drawerUnitCount;
    const layerCount =
      Number(layout.drawerLayerCount) || DEFAULT_LAYOUT.drawerLayerCount;
    if (unitNo > unitCount || layerNo > layerCount) {
      throw new AppError(
        `药斗位置超出 ${unitCount} 柜、顶层及 ${layerCount} 个编号层的范围`,
        400,
      );
    }
    const layerColumnCount =
      readDrawerCabinetColumns(layout)[unitNo - 1][layerNo];
    if (columnNo > layerColumnCount) {
      throw new AppError(
        `药斗 ${unitNo} 号柜${layerNo === 0 ? "顶层" : `第 ${layerNo} 层`}只有 ${layerColumnCount} 列`,
        400,
      );
    }
    return {
      locationCode: `D-${unitNo}-${layerNo}-${columnNo}`,
      locationType: type,
      unitNo,
      layerNo,
      columnNo,
      slotNo,
      medicineCapacity: 3,
    };
  }

  if (parts.length !== 3) {
    throw new AppError(`${type} 类位置格式应为 ${type}-编号-层数`, 400);
  }
  const unitNo = positiveDigit(
    parts[1],
    type === "F" ? "冰箱号" : type === "C" ? "仓库架号" : "大柜柜号",
  );
  const layerNo = ["F", "G"].includes(type)
    ? positiveLayerNumber(parts[2], type === "F" ? "冰箱层数" : "柜层数")
    : positiveDigit(parts[2], "层数");
  return {
    locationCode: `${type}-${unitNo}-${layerNo}`,
    locationType: type,
    unitNo,
    layerNo,
    columnNo: null,
    medicineCapacity: null,
  };
}

function compactLocationCode(value) {
  return String(value || "").replaceAll("-", "");
}

function defaultLocations(layout = DEFAULT_LAYOUT) {
  const locations = [];
  const drawerCabinetColumns = readDrawerCabinetColumns(layout);
  const unitCount =
    Number(layout.drawerUnitCount) || DEFAULT_LAYOUT.drawerUnitCount;
  for (let unitNo = 1; unitNo <= unitCount; unitNo += 1) {
    const cabinetColumns = drawerCabinetColumns[unitNo - 1];
    for (let columnNo = 1; columnNo <= cabinetColumns[0]; columnNo += 1) {
      locations.push({
        storeId: 0,
        locationCode: `D-${unitNo}-0-${columnNo}`,
        locationType: "D",
        unitNo,
        layerNo: 0,
        columnNo,
        medicineCapacity: 3,
      });
    }
    for (let layerNo = 1; layerNo <= layout.drawerLayerCount; layerNo += 1) {
      for (
        let columnNo = 1;
        columnNo <= cabinetColumns[layerNo];
        columnNo += 1
      ) {
        locations.push({
          storeId: 0,
          locationCode: `D-${unitNo}-${layerNo}-${columnNo}`,
          locationType: "D",
          unitNo,
          layerNo,
          columnNo,
          medicineCapacity: 3,
        });
      }
    }
  }
  const bigCabinetUnitCount =
    Number(layout.bigCabinetUnitCount) || DEFAULT_LAYOUT.bigCabinetUnitCount;
  const bigCabinetLayerCount =
    Number(layout.bigCabinetLayerCount) || DEFAULT_LAYOUT.bigCabinetLayerCount;
  for (let unitNo = 1; unitNo <= bigCabinetUnitCount; unitNo += 1) {
    for (let layerNo = 1; layerNo <= bigCabinetLayerCount; layerNo += 1) {
      locations.push({
        storeId: 0,
        locationCode: `G-${unitNo}-${layerNo}`,
        locationType: "G",
        unitNo,
        layerNo,
        columnNo: null,
        medicineCapacity: null,
      });
    }
  }
  return locations;
}

async function getStoreLayout(prisma, storeId) {
  const store = await prisma.store.findFirst({
    where: { id: storeId, deletedAt: null },
    select: {
      drawerUnitCount: true,
      drawerLayerCount: true,
      drawerLayerColumns: true,
      drawerTopColumnCount: true,
      bigCabinetUnitCount: true,
      bigCabinetLayerCount: true,
    },
  });
  if (!store) throw new AppError("门店不存在", 404);
  return store;
}

async function ensureDefaultLocations(prisma, storeId, layout = null) {
  const currentLayout = layout || (await getStoreLayout(prisma, storeId));
  await prisma.herbLocation.createMany({
    data: defaultLocations(currentLayout).map((location) => ({
      ...location,
      storeId,
    })),
    skipDuplicates: true,
  });
  return currentLayout;
}

function publicLocation(location) {
  const assignments = [...(location.assignments || [])].sort((left, right) => {
    const leftSlot = left.slotNo ?? Number.MAX_SAFE_INTEGER;
    const rightSlot = right.slotNo ?? Number.MAX_SAFE_INTEGER;
    return leftSlot - rightSlot || left.id - right.id;
  });
  return {
    id: location.id,
    code: location.locationCode,
    type: location.locationType,
    unitNo: location.unitNo,
    layerNo: location.layerNo,
    columnNo: location.columnNo,
    medicineCapacity: location.medicineCapacity,
    herbs: assignments.map((assignment) => ({
      assignmentId: assignment.id,
      slotNo: assignment.slotNo,
      id: assignment.herb.id,
      code: assignment.herb.code,
      name: assignment.herb.name,
      specification: assignment.herb.specification,
    })),
  };
}

function publicHerb(herb) {
  return {
    id: herb.id,
    code: herb.code,
    name: herb.name,
    specification: herb.specification,
  };
}

async function getStore(prisma, storeId) {
  const store = await prisma.store.findFirst({
    where: { id: storeId, deletedAt: null },
    select: {
      id: true,
      name: true,
      code: true,
      address: true,
      phone: true,
      status: true,
    },
  });
  if (!store) throw new AppError("门店不存在", 404);
  return store;
}

export async function getHerbLocationLayout(prisma, actor, query = {}) {
  const storeId = await resolveBusinessStoreId(prisma, actor, query.storeId);
  const [store, layout] = await Promise.all([
    getStore(prisma, storeId),
    getStoreLayout(prisma, storeId),
  ]);
  return { store, layout: publicLayout(layout) };
}

export async function listHerbLocationStores(prisma, actor) {
  assertManager(actor);
  const where = {
    deletedAt: null,
    status: 1,
    ...(isSuperAdmin(actor) ? {} : { id: Number(actor.storeId) }),
  };
  return prisma.store.findMany({
    where,
    select: { id: true, name: true, code: true },
    orderBy: { id: "asc" },
  });
}

export async function listHerbLocations(prisma, actor, query = {}) {
  const storeId = await resolveBusinessStoreId(prisma, actor, query.storeId);
  const layout = await ensureDefaultLocations(prisma, storeId);
  const [store, locations, herbs] = await Promise.all([
    getStore(prisma, storeId),
    prisma.herbLocation.findMany({
      where: { storeId, status: 1 },
      include: {
        assignments: {
          include: {
            herb: {
              select: { id: true, code: true, name: true, specification: true },
            },
          },
          orderBy: [{ slotNo: "asc" }, { id: "asc" }],
        },
      },
      orderBy: [
        { locationType: "asc" },
        { unitNo: "asc" },
        { layerNo: "asc" },
        { columnNo: "asc" },
      ],
    }),
    prisma.herb.findMany({
      where: { storeId, status: 1 },
      select: { id: true, code: true, name: true, specification: true },
      orderBy: [{ name: "asc" }, { id: "asc" }],
    }),
  ]);
  return {
    store,
    layout: publicLayout(layout),
    locations: locations.map(publicLocation),
    herbs: herbs.map(publicHerb),
  };
}

function normalizeHerb(payload) {
  const name = String(payload.name || "").trim();
  const code = String(payload.code || "")
    .trim()
    .toUpperCase();
  const specification = String(payload.specification || "").trim();
  if (!name || name.length > 100)
    throw new AppError("药材名称不能为空且不能超过100个字符", 400);
  if (code.length > 64) throw new AppError("药材编码不能超过64个字符", 400);
  if (specification.length > 100)
    throw new AppError("规格不能超过100个字符", 400);
  return { name, code: code || null, specification: specification || null };
}

async function findOrCreateHerb(tx, storeId, payload, actor, options = {}) {
  const { updateExistingByName = false } = options;
  if (payload.herbId) {
    const herb = await tx.herb.findFirst({
      where: { id: Number(payload.herbId), storeId, status: 1 },
    });
    if (!herb) throw new AppError("药材不存在或不属于当前门店", 404);
    return { herb, created: false, updated: false };
  }
  const data = normalizeHerb(payload);
  const where = data.code
    ? { storeId, code: data.code }
    : { storeId, name: data.name, specification: data.specification };
  let existing = await tx.herb.findFirst({ where });
  if (!existing && updateExistingByName) {
    const candidates = await tx.herb.findMany({
      where: { storeId, name: data.name, status: 1 },
      take: 2,
    });
    if (candidates.length > 1) {
      throw new AppError(
        `药材“${data.name}”存在多个记录，无法按名称补全，请先使用药材编码区分`,
        409,
      );
    }
    existing = candidates[0] || null;
    if (existing?.code && data.code && existing.code !== data.code) {
      throw new AppError(`药材“${data.name}”已有其他编码，请检查导入数据`, 409);
    }
  }
  if (existing) {
    if (updateExistingByName) {
      const updates = {};
      if (data.name !== existing.name) updates.name = data.name;
      if (data.code && existing.code !== data.code) updates.code = data.code;
      if (data.specification && existing.specification !== data.specification)
        updates.specification = data.specification;
      if (Object.keys(updates).length) {
        const herb = await tx.herb.update({
          where: { id: existing.id },
          data: { ...updates, updatedBy: Number(actor.id) },
        });
        return { herb, created: false, updated: true };
      }
    }
    return { herb: existing, created: false, updated: false };
  }
  const herb = await tx.herb.create({
    data: {
      ...data,
      storeId,
      createdBy: Number(actor.id),
      updatedBy: Number(actor.id),
    },
  });
  return { herb, created: true, updated: false };
}

async function findOrCreateLocation(tx, storeId, locationCode, actor, layout) {
  const parsed = parseLocationCode(locationCode, layout);
  const { slotNo, ...locationData } = parsed;
  const existing = await tx.herbLocation.findUnique({
    where: {
      storeId_locationCode: { storeId, locationCode: parsed.locationCode },
    },
  });
  if (existing) return { location: existing, slotNo };
  const created = await tx.herbLocation.create({
    data: {
      ...locationData,
      storeId,
      createdBy: Number(actor.id),
      updatedBy: Number(actor.id),
    },
  });
  return { location: existing || created, slotNo };
}

async function removeEmptyDynamicLocation(tx, location) {
  if (!location || !["G", "F", "C"].includes(location.locationType)) return;
  await tx.herbLocation.deleteMany({
    where: {
      id: location.id,
      assignments: { none: {} },
    },
  });
}

async function createAssignment(
  tx,
  storeId,
  locationCode,
  herbPayload,
  actor,
  layout,
  options = {},
) {
  const [locationResult, herbResult] = await Promise.all([
    findOrCreateLocation(tx, storeId, locationCode, actor, layout),
    findOrCreateHerb(tx, storeId, herbPayload, actor, options),
  ]);
  const { herb } = herbResult;
  const location = locationResult.location;
  const existing = await tx.herbLocationAssignment.findUnique({
    where: { locationId_herbId: { locationId: location.id, herbId: herb.id } },
  });
  if (existing)
    return {
      assignment: existing,
      created: false,
      herb,
      herbCreated: herbResult.created,
      herbUpdated: herbResult.updated,
    };

  const slotNo =
    locationResult.slotNo ??
    (herbPayload.slotNo == null || String(herbPayload.slotNo).trim() === ""
      ? null
      : positiveDigit(herbPayload.slotNo, "格内序号"));
  if (slotNo !== null) {
    const occupied = await tx.herbLocationAssignment.findFirst({
      where: { locationId: location.id, slotNo },
    });
    if (occupied)
      throw new AppError(
        `位置 ${location.locationCode} 的第 ${slotNo} 个格内序号已被使用`,
        409,
      );
  }
  const assignment = await tx.herbLocationAssignment.create({
    data: {
      locationId: location.id,
      herbId: herb.id,
      slotNo,
      createdBy: Number(actor.id),
    },
  });
  return {
    assignment,
    created: true,
    herb,
    herbCreated: herbResult.created,
    herbUpdated: herbResult.updated,
  };
}

export async function assignHerbLocation(prisma, actor, payload = {}) {
  const storeId = await resolveBusinessStoreId(prisma, actor, payload.storeId);
  const layout = await ensureDefaultLocations(prisma, storeId);
  const locationCode = String(payload.locationCode || "").trim();
  if (!locationCode) throw new AppError("请选择或输入位置编号", 400);
  const result = await prisma.$transaction((tx) =>
    createAssignment(tx, storeId, locationCode, payload, actor, layout),
  );
  if (result.created) {
    await recordOperation(prisma, actor, {
      module: "herb-location",
      action: "assign",
      targetId: result.assignment.id,
      storeId,
      description: `药材「${result.herb.name}」配置到位置 ${parseLocationCode(locationCode, layout).locationCode}`,
    });
  }
  return { id: result.assignment.id, created: result.created };
}

export async function updateHerb(prisma, actor, herbId, payload = {}) {
  const id = Number(herbId);
  if (!Number.isInteger(id) || id <= 0)
    throw new AppError("药材记录不正确", 400);
  const current = await prisma.herb.findFirst({ where: { id, status: 1 } });
  if (!current) throw new AppError("药材不存在", 404);
  const storeId = await resolveBusinessStoreId(
    prisma,
    actor,
    payload.storeId ?? current.storeId,
  );
  if (storeId !== current.storeId)
    throw new AppError("无权编辑该门店药材", 403);

  const data = normalizeHerb(payload);
  const duplicateWhere = data.code
    ? { storeId, code: data.code, id: { not: id } }
    : {
        storeId,
        name: data.name,
        specification: data.specification,
        id: { not: id },
      };
  const duplicate = await prisma.herb.findFirst({ where: duplicateWhere });
  if (duplicate)
    throw new AppError("该门店已存在相同的药材编码或名称规格", 409);

  const herb = await prisma.herb.update({
    where: { id },
    data: { ...data, updatedBy: Number(actor.id) },
  });
  await recordOperation(prisma, actor, {
    module: "herb-location",
    action: "herb-update",
    targetId: id,
    storeId,
    description: `药材「${current.name}」修改为「${herb.name}」`,
  });
  return publicHerb(herb);
}

export async function updateHerbLocationAssignment(
  prisma,
  actor,
  assignmentId,
  payload = {},
) {
  const id = Number(assignmentId);
  if (!Number.isInteger(id) || id <= 0)
    throw new AppError("关联记录不正确", 400);
  const locationCode = String(payload.locationCode || "").trim();
  if (!locationCode) throw new AppError("请填写位置编号", 400);

  const current = await prisma.herbLocationAssignment.findUnique({
    where: { id },
    include: { location: true, herb: true },
  });
  if (!current) throw new AppError("药材位置关联不存在", 404);
  const storeId = await resolveBusinessStoreId(
    prisma,
    actor,
    current.location.storeId,
  );
  if (storeId !== current.location.storeId)
    throw new AppError("无权操作该门店斗谱", 403);
  const layout = await getStoreLayout(prisma, storeId);

  const destination = await prisma.$transaction(async (tx) => {
    const result = await findOrCreateLocation(
      tx,
      storeId,
      locationCode,
      actor,
      layout,
    );
    const slotNo = result.slotNo ?? null;
    const duplicate = await tx.herbLocationAssignment.findFirst({
      where: {
        id: { not: id },
        locationId: result.location.id,
        herbId: current.herbId,
      },
    });
    if (duplicate) throw new AppError("该药材已配置在目标格子中", 409);
    if (slotNo !== null) {
      const occupied = await tx.herbLocationAssignment.findFirst({
        where: { id: { not: id }, locationId: result.location.id, slotNo },
      });
      if (occupied) {
        if (
          result.location.id !== current.locationId ||
          current.slotNo === null
        ) {
          throw new AppError(`目标格子的第 ${slotNo} 个位置已被使用`, 409);
        }
        await tx.herbLocationAssignment.update({
          where: { id },
          data: { slotNo: null },
        });
        await tx.herbLocationAssignment.update({
          where: { id: occupied.id },
          data: { slotNo: current.slotNo },
        });
      }
    }
    await tx.herbLocationAssignment.update({
      where: { id },
      data: { locationId: result.location.id, slotNo },
    });
    await removeEmptyDynamicLocation(tx, current.location);
    return result.location;
  });

  await recordOperation(prisma, actor, {
    module: "herb-location",
    action: "assignment-update",
    targetId: id,
    storeId,
    description: `药材「${current.herb.name}」位置由 ${compactLocationCode(current.location.locationCode)}${current.slotNo || ""} 调整为 ${compactLocationCode(destination.locationCode)}${parseLocationCode(locationCode, layout).slotNo || ""}`,
  });
  return { id, locationCode: destination.locationCode };
}

function normalizeDrawerCabinetColumns(
  value,
  unitCount,
  layerCount,
  currentLayout,
  legacyTopColumnCount,
) {
  let values = value;
  if (typeof values === "string") {
    try {
      values = JSON.parse(values);
    } catch {
      throw new AppError("各斗柜列数格式不正确", 400);
    }
  }
  if (
    Array.isArray(values) &&
    values.length === layerCount &&
    values.every(validColumnCount)
  ) {
    const topColumnCount = positiveDigit(
      legacyTopColumnCount ?? currentLayout.drawerTopColumnCount,
      "药斗顶层列数",
    );
    values = Array.from({ length: unitCount }, () => [
      topColumnCount,
      ...values,
    ]);
  } else if (!Array.isArray(values)) {
    values = readDrawerCabinetColumns(currentLayout).map((cabinet) => {
      const columns = cabinet.slice(0, layerCount + 1);
      while (columns.length < layerCount + 1) columns.push(3);
      return columns;
    });
    while (values.length < unitCount) {
      values.push(defaultDrawerCabinetColumns(1, layerCount)[0]);
    }
    values = values.slice(0, unitCount);
  }
  if (values.length !== unitCount)
    throw new AppError(`必须配置 ${unitCount} 个斗柜`, 400);
  return values.map((cabinet, unitIndex) => {
    if (!Array.isArray(cabinet) || cabinet.length !== layerCount + 1) {
      throw new AppError(
        `${unitIndex + 1} 号斗柜必须配置顶层及 ${layerCount} 个编号层`,
        400,
      );
    }
    return cabinet.map((columnCount, layerIndex) =>
      positiveDigit(
        columnCount,
        `${unitIndex + 1} 号斗柜${layerIndex === 0 ? "顶层" : `第 ${layerIndex} 层`}列数`,
      ),
    );
  });
}

export async function updateHerbLocationLayout(prisma, actor, payload = {}) {
  const storeId = await resolveBusinessStoreId(prisma, actor, payload.storeId);
  let nextLayout;

  await prisma.$transaction(async (tx) => {
    const currentLayout = await getStoreLayout(tx, storeId);
    const nextDrawerUnitCount = positiveDigit(
      payload.drawerUnitCount ?? currentLayout.drawerUnitCount,
      "斗柜数量",
    );
    const nextLayerCount = positiveDigit(
      payload.drawerLayerCount ?? currentLayout.drawerLayerCount,
      "药斗层数",
    );
    const nextBigCabinetUnitCount = positiveDigit(
      payload.bigCabinetUnitCount ?? currentLayout.bigCabinetUnitCount,
      "柜数",
    );
    const nextBigCabinetLayerCount = positiveDigit(
      payload.bigCabinetLayerCount ?? currentLayout.bigCabinetLayerCount,
      "柜层数",
    );
    const nextDrawerCabinetColumns = normalizeDrawerCabinetColumns(
      payload.drawerLayerColumns,
      nextDrawerUnitCount,
      nextLayerCount,
      currentLayout,
      payload.drawerTopColumnCount,
    );
    const currentDrawerCabinetColumns = readDrawerCabinetColumns(currentLayout);
    const drawerRemovalRules = [];
    if (nextDrawerUnitCount < currentLayout.drawerUnitCount)
      drawerRemovalRules.push({ unitNo: { gt: nextDrawerUnitCount } });
    if (nextLayerCount < currentLayout.drawerLayerCount)
      drawerRemovalRules.push({ layerNo: { gt: nextLayerCount } });
    for (
      let unitIndex = 0;
      unitIndex <
      Math.min(nextDrawerUnitCount, currentDrawerCabinetColumns.length);
      unitIndex += 1
    ) {
      for (let layerNo = 0; layerNo <= nextLayerCount; layerNo += 1) {
        if (
          nextDrawerCabinetColumns[unitIndex][layerNo] <
          currentDrawerCabinetColumns[unitIndex][layerNo]
        ) {
          drawerRemovalRules.push({
            unitNo: unitIndex + 1,
            layerNo,
            columnNo: { gt: nextDrawerCabinetColumns[unitIndex][layerNo] },
          });
        }
      }
    }
    const removedDrawerWhere = {
      storeId,
      locationType: "D",
      OR: drawerRemovalRules,
    };
    const bigCabinetRemovalRules = [];
    if (nextBigCabinetUnitCount < currentLayout.bigCabinetUnitCount) {
      bigCabinetRemovalRules.push({ unitNo: { gt: nextBigCabinetUnitCount } });
    }
    if (nextBigCabinetLayerCount < currentLayout.bigCabinetLayerCount) {
      bigCabinetRemovalRules.push({ layerNo: { gt: nextBigCabinetLayerCount } });
    }
    if (drawerRemovalRules.length) {
      const assignedLocationCount = await tx.herbLocation.count({
        where: { ...removedDrawerWhere, assignments: { some: {} } },
      });
      if (assignedLocationCount) {
        throw new AppError(
          "待删除的斗柜、层或列中仍有药材，请先移除或调整这些药材的位置",
          409,
        );
      }
      await tx.herbLocation.deleteMany({ where: removedDrawerWhere });
    }
    if (bigCabinetRemovalRules.length) {
      await tx.herbLocation.deleteMany({
        where: {
          storeId,
          locationType: "G",
          OR: bigCabinetRemovalRules,
          assignments: { none: {} },
        },
      });
    }
    nextLayout = {
      drawerUnitCount: nextDrawerUnitCount,
      drawerLayerCount: nextLayerCount,
      drawerLayerColumns: JSON.stringify(nextDrawerCabinetColumns),
      drawerTopColumnCount: nextDrawerCabinetColumns[0][0],
      bigCabinetUnitCount: nextBigCabinetUnitCount,
      bigCabinetLayerCount: nextBigCabinetLayerCount,
    };
    await tx.store.update({
      where: { id: storeId },
      data: { ...nextLayout, updatedBy: Number(actor.id) },
    });
    await ensureDefaultLocations(tx, storeId, nextLayout);
  });

  await recordOperation(prisma, actor, {
    module: "herb-location",
    action: "layout-update",
    storeId,
    description: `药斗布局调整为 ${nextLayout.drawerUnitCount} 个斗柜、${nextLayout.drawerLayerCount} 个编号层，柜 ${nextLayout.bigCabinetUnitCount} 个、每柜 ${nextLayout.bigCabinetLayerCount} 层`,
  });
  return publicLayout(nextLayout);
}

export async function removeHerbLocationAssignment(
  prisma,
  actor,
  assignmentId,
) {
  const id = Number(assignmentId);
  if (!Number.isInteger(id) || id <= 0)
    throw new AppError("关联记录不正确", 400);
  const current = await prisma.herbLocationAssignment.findUnique({
    where: { id },
    include: { location: true, herb: true },
  });
  if (!current) throw new AppError("药材位置关联不存在", 404);
  const storeId = await resolveBusinessStoreId(
    prisma,
    actor,
    current.location.storeId,
  );
  if (storeId !== current.location.storeId)
    throw new AppError("无权操作该门店斗谱", 403);
  await prisma.$transaction(async (tx) => {
    await tx.herbLocationAssignment.delete({ where: { id } });
    await removeEmptyDynamicLocation(tx, current.location);
  });
  await recordOperation(prisma, actor, {
    module: "herb-location",
    action: "remove",
    targetId: id,
    storeId,
    description: `药材「${current.herb.name}」从位置 ${current.location.locationCode} 移除`,
  });
  return { id };
}

function worksheetRows(locations) {
  return locations.flatMap((location) => {
    const herbs = [...(location.assignments || [])].sort((left, right) => {
      const leftSlot = left.slotNo ?? Number.MAX_SAFE_INTEGER;
      const rightSlot = right.slotNo ?? Number.MAX_SAFE_INTEGER;
      return leftSlot - rightSlot || left.id - right.id;
    });
    const common = [compactLocationCode(location.locationCode)];
    if (!herbs.length) return [];
    return herbs.map((assignment) => [
      ...common,
      assignment.herb.code || "",
      assignment.herb.name,
      assignment.herb.specification || "",
    ]);
  });
}

async function getWorkbookData(prisma, storeId) {
  await ensureDefaultLocations(prisma, storeId);
  return prisma.herbLocation.findMany({
    where: { storeId, status: 1 },
    include: {
      assignments: {
        include: { herb: true },
        orderBy: [{ slotNo: "asc" }, { id: "asc" }],
      },
    },
    orderBy: [
      { locationType: "asc" },
      { unitNo: "asc" },
      { layerNo: "asc" },
      { columnNo: "asc" },
    ],
  });
}

async function buildWorkbook(prisma, storeId, template = false) {
  const locations = await getWorkbookData(prisma, storeId);
  const workbook = new ExcelJS.Workbook();
  const worksheetConfigs = template
    ? [["斗谱导入模板", locations]]
    : EXPORT_SHEETS.map(([type, name]) => [
        name,
        locations.filter((location) => location.locationType === type),
      ]);

  for (const [name, sheetLocations] of worksheetConfigs) {
    const sheet = workbook.addWorksheet(name);
    sheet.addRow(template ? IMPORT_HEADERS : EXPORT_HEADERS);
    const rows = template
      ? sheetLocations.map((location) => [
          compactLocationCode(location.locationCode),
          "",
          "",
          "",
        ])
      : worksheetRows(sheetLocations);
    rows.forEach((row) => sheet.addRow(row));
    sheet.getRow(1).font = { bold: true, color: { argb: "FFFFFFFF" } };
    sheet.getRow(1).fill = {
      type: "pattern",
      pattern: "solid",
      fgColor: { argb: "FF2563EB" },
    };
    sheet.columns = template
      ? [{ width: 18 }, { width: 18 }, { width: 20 }, { width: 18 }]
      : [{ width: 16 }, { width: 18 }, { width: 20 }, { width: 18 }];
    sheet.views = [{ state: "frozen", ySplit: 1 }];
  }
  return workbook.xlsx.writeBuffer();
}

export async function exportHerbLocations(
  prisma,
  actor,
  query = {},
  template = false,
) {
  const storeId = await resolveBusinessStoreId(prisma, actor, query.storeId);
  const buffer = await buildWorkbook(prisma, storeId, template);
  return { buffer, filename: template ? "斗谱导入模板.xlsx" : "斗谱表.xlsx" };
}

function fullAssignmentPosition(location, assignment) {
  const code = compactLocationCode(location.locationCode);
  return location.locationType === "D" && assignment.slotNo != null
    ? `${code}${assignment.slotNo}`
    : code;
}

async function buildMoveWorkbook(prisma, storeId) {
  const locations = await getWorkbookData(prisma, storeId);
  const workbook = new ExcelJS.Workbook();
  const sheet = workbook.addWorksheet("批量修改位置");
  sheet.addRow(MOVE_IMPORT_HEADERS);
  for (const location of locations) {
    for (const assignment of location.assignments || []) {
      const position = fullAssignmentPosition(location, assignment);
      sheet.addRow([
        position,
        position,
        assignment.herb.code || "",
        assignment.herb.name,
      ]);
    }
  }
  sheet.getRow(1).font = { bold: true, color: { argb: "FFFFFFFF" } };
  sheet.getRow(1).fill = {
    type: "pattern",
    pattern: "solid",
    fgColor: { argb: "FF2563EB" },
  };
  sheet.columns = [
    { width: 18 },
    { width: 18 },
    { width: 18 },
    { width: 20 },
  ];
  sheet.views = [{ state: "frozen", ySplit: 1 }];
  return workbook.xlsx.writeBuffer();
}

export async function exportHerbLocationMoveTemplate(
  prisma,
  actor,
  query = {},
) {
  const storeId = await resolveBusinessStoreId(prisma, actor, query.storeId);
  return {
    buffer: await buildMoveWorkbook(prisma, storeId),
    filename: "斗谱批量修改位置.xlsx",
  };
}

function cellText(row, index) {
  if (!index) return "";
  const cell = row.getCell(index);
  return String(cell.text || cell.value || "").trim();
}

async function findMoveHerb(tx, storeId, row) {
  const code = String(row.code || "")
    .trim()
    .toUpperCase();
  const name = String(row.name || "").trim();
  const candidates = await tx.herb.findMany({
    where: {
      storeId,
      status: 1,
      ...(code ? { code } : { name }),
    },
    take: 2,
  });
  if (!candidates.length)
    throw new AppError(
      code ? `找不到药材编码“${code}”` : `找不到药材“${name}”`,
      404,
    );
  if (candidates.length > 1)
    throw new AppError(
      code
        ? `药材编码“${code}”存在多条记录`
        : `药材“${name}”存在多条记录，请填写药材编码`,
      409,
    );
  return candidates[0];
}

async function resolveMoveRow(tx, storeId, row, actor, layout) {
  const sourceParsed = parseLocationCode(row.sourceLocationCode, layout);
  const sourceLocation = await tx.herbLocation.findUnique({
    where: {
      storeId_locationCode: {
        storeId,
        locationCode: sourceParsed.locationCode,
      },
    },
  });
  if (!sourceLocation)
    throw new AppError(`原位置 ${compactLocationCode(sourceParsed.locationCode)} 不存在`, 404);
  const herb = await findMoveHerb(tx, storeId, row);
  const assignment = await tx.herbLocationAssignment.findUnique({
    where: {
      locationId_herbId: {
        locationId: sourceLocation.id,
        herbId: herb.id,
      },
    },
  });
  if (!assignment)
    throw new AppError(
      `药材“${herb.name}”不在原位置 ${compactLocationCode(sourceLocation.locationCode)}`,
      404,
    );
  if (sourceParsed.slotNo != null && sourceParsed.slotNo !== assignment.slotNo)
    throw new AppError(
      `药材“${herb.name}”不在原位置 ${compactLocationCode(sourceLocation.locationCode)}${sourceParsed.slotNo}`,
      404,
    );
  const destinationResult = await findOrCreateLocation(
    tx,
    storeId,
    row.targetLocationCode,
    actor,
    layout,
  );
  return {
    ...row,
    herb,
    assignment,
    sourceLocation,
    destination: destinationResult.location,
    targetSlotNo: destinationResult.slotNo ?? null,
  };
}

export async function importHerbLocationMoves(
  prisma,
  actor,
  storeIdValue,
  file,
) {
  const storeId = await resolveBusinessStoreId(prisma, actor, storeIdValue);
  if (!file?.buffer?.length) throw new AppError("请选择 Excel 文件", 400);
  const workbook = new ExcelJS.Workbook();
  try {
    await workbook.xlsx.load(file.buffer);
  } catch {
    throw new AppError("无法读取 Excel 文件，请使用批量修改位置模板", 400);
  }
  const sheet = workbook.worksheets[0];
  if (!sheet) throw new AppError("Excel 文件中没有工作表", 400);
  const headers = new Map();
  sheet
    .getRow(1)
    .eachCell((cell, index) =>
      headers.set(String(cell.text || "").trim(), index),
    );
  const sourceColumn = headers.get("原位置");
  const targetColumn = headers.get("新位置");
  const codeColumn = headers.get("药材编码");
  const nameColumn = headers.get("药材名称") || headers.get("药材");
  if (!sourceColumn || !targetColumn || (!codeColumn && !nameColumn))
    throw new AppError(
      "缺少导入列：原位置、新位置，以及药材编码或药材名称",
      400,
    );
  if (sheet.rowCount > 1001) throw new AppError("单次最多导入1000行", 400);

  const rows = [];
  for (let index = 2; index <= sheet.rowCount; index += 1) {
    const row = sheet.getRow(index);
    const sourceLocationCode = cellText(row, sourceColumn);
    const targetLocationCode = cellText(row, targetColumn);
    const code = cellText(row, codeColumn);
    const name = cellText(row, nameColumn);
    if (!sourceLocationCode && !targetLocationCode && !code && !name) continue;
    if (!sourceLocationCode || !targetLocationCode || (!code && !name))
      throw new AppError(
        `第${index}行必须填写原位置、新位置，以及药材编码或药材名称`,
        400,
      );
    rows.push({
      rowNumber: index,
      sourceLocationCode,
      targetLocationCode,
      code,
      name,
    });
  }

  const layout = await getStoreLayout(prisma, storeId);
  for (const row of rows) {
    try {
      parseLocationCode(row.sourceLocationCode, layout);
      parseLocationCode(row.targetLocationCode, layout);
    } catch (error) {
      if (error instanceof AppError)
        error.message = `第${row.rowNumber}行：${error.message}`;
      throw error;
    }
  }

  let moved = 0;
  let skipped = 0;
  await prisma.$transaction(
    async (tx) => {
      await ensureDefaultLocations(tx, storeId, layout);
      const resolvedRows = [];
      for (const row of rows) {
        try {
          resolvedRows.push(
            await resolveMoveRow(tx, storeId, row, actor, layout),
          );
        } catch (error) {
          if (error instanceof AppError)
            error.message = `第${row.rowNumber}行：${error.message}`;
          throw error;
        }
      }

      const sourceRows = new Map();
      for (const row of resolvedRows) {
        const duplicate = sourceRows.get(row.assignment.id);
        if (duplicate)
          throw new AppError(
            `第${row.rowNumber}行与第${duplicate.rowNumber}行重复修改同一条药材位置`,
            400,
          );
        sourceRows.set(row.assignment.id, row);
      }

      const activeRows = resolvedRows.filter((row) => {
        const unchanged =
          row.assignment.locationId === row.destination.id &&
          row.assignment.slotNo === row.targetSlotNo;
        if (unchanged) skipped += 1;
        return !unchanged;
      });
      const movingIds = new Set(activeRows.map((row) => row.assignment.id));
      const targetHerbs = new Map();
      const targetSlots = new Map();

      for (const row of activeRows) {
        const herbKey = `${row.destination.id}:${row.herb.id}`;
        const duplicateHerbRow = targetHerbs.get(herbKey);
        if (duplicateHerbRow)
          throw new AppError(
            `第${row.rowNumber}行与第${duplicateHerbRow.rowNumber}行将同一药材移到同一位置`,
            400,
          );
        targetHerbs.set(herbKey, row);

        const existingHerb = await tx.herbLocationAssignment.findUnique({
          where: {
            locationId_herbId: {
              locationId: row.destination.id,
              herbId: row.herb.id,
            },
          },
        });
        if (existingHerb && existingHerb.id !== row.assignment.id)
          throw new AppError(
            `第${row.rowNumber}行：药材“${row.herb.name}”已在目标位置中`,
            409,
          );

        if (row.targetSlotNo != null) {
          const slotKey = `${row.destination.id}:${row.targetSlotNo}`;
          const duplicateSlotRow = targetSlots.get(slotKey);
          if (duplicateSlotRow)
            throw new AppError(
              `第${row.rowNumber}行与第${duplicateSlotRow.rowNumber}行使用了同一目标格内位置`,
              400,
            );
          targetSlots.set(slotKey, row);
          const occupied = await tx.herbLocationAssignment.findFirst({
            where: {
              locationId: row.destination.id,
              slotNo: row.targetSlotNo,
            },
          });
          if (occupied && !movingIds.has(occupied.id))
            throw new AppError(
              `第${row.rowNumber}行：目标位置 ${compactLocationCode(row.destination.locationCode)}${row.targetSlotNo} 已被使用`,
              409,
            );
        }
      }

      for (const row of activeRows) {
        if (row.assignment.slotNo != null) {
          await tx.herbLocationAssignment.update({
            where: { id: row.assignment.id },
            data: { slotNo: null },
          });
        }
      }
      for (const row of activeRows) {
        await tx.herbLocationAssignment.update({
          where: { id: row.assignment.id },
          data: { locationId: row.destination.id, slotNo: null },
        });
      }
      for (const row of activeRows) {
        if (row.targetSlotNo != null) {
          await tx.herbLocationAssignment.update({
            where: { id: row.assignment.id },
            data: { slotNo: row.targetSlotNo },
          });
        }
      }
      moved = activeRows.length;
      await recordOperation(tx, actor, {
        module: "herb-location",
        action: "move-import",
        storeId,
        description: `批量修改斗谱位置 ${rows.length} 行，移动 ${moved} 条，跳过 ${skipped} 条`,
      });
    },
    { maxWait: 5000, timeout: 60000 },
  );
  return { total: rows.length, moved, skipped };
}

export async function importHerbLocations(prisma, actor, storeIdValue, file) {
  const storeId = await resolveBusinessStoreId(prisma, actor, storeIdValue);
  if (!file?.buffer?.length) throw new AppError("请选择 Excel 文件", 400);
  const workbook = new ExcelJS.Workbook();
  try {
    await workbook.xlsx.load(file.buffer);
  } catch {
    throw new AppError("无法读取 Excel 文件，请使用导入模板", 400);
  }
  const sheet = workbook.worksheets[0];
  if (!sheet) throw new AppError("Excel 文件中没有工作表", 400);
  const headers = new Map();
  sheet
    .getRow(1)
    .eachCell((cell, index) =>
      headers.set(String(cell.text || "").trim(), index),
    );
  const locationColumn = headers.get("位置编号") || headers.get("位置");
  const nameColumn = headers.get("药材名称") || headers.get("药材");
  if (!locationColumn || !nameColumn)
    throw new AppError("缺少导入列：位置编号、药材名称", 400);
  if (sheet.rowCount > 1001) throw new AppError("单次最多导入1000行", 400);

  const rows = [];
  for (let index = 2; index <= sheet.rowCount; index += 1) {
    const row = sheet.getRow(index);
    const locationCode = cellText(row, locationColumn);
    const code = cellText(row, headers.get("药材编码"));
    const name = cellText(row, nameColumn);
    const specification = cellText(row, headers.get("规格"));
    if (!locationCode && !code && !name && !specification) continue;
    if (!locationCode || !name) {
      throw new AppError(`第${index}行必须填写位置编号和药材名称`, 400);
    }
    rows.push({ rowNumber: index, locationCode, code, name, specification });
  }

  const layout = await getStoreLayout(prisma, storeId);
  for (const row of rows) {
    try {
      parseLocationCode(row.locationCode, layout);
      normalizeHerb(row);
    } catch (error) {
      if (error instanceof AppError)
        error.message = `第${row.rowNumber}行：${error.message}`;
      throw error;
    }
  }

  let added = 0;
  let updated = 0;
  let skipped = 0;
  await prisma.$transaction(
    async (tx) => {
      await ensureDefaultLocations(tx, storeId, layout);
      for (const row of rows) {
        try {
          const result = await createAssignment(
            tx,
            storeId,
            row.locationCode,
            row,
            actor,
            layout,
            {
              updateExistingByName: true,
            },
          );
          if (result.created) added += 1;
          if (result.herbUpdated) updated += 1;
          if (!result.created && !result.herbUpdated) skipped += 1;
        } catch (error) {
          if (error instanceof AppError)
            error.message = `第${row.rowNumber}行：${error.message}`;
          throw error;
        }
      }
      await recordOperation(tx, actor, {
        module: "herb-location",
        action: "import",
        storeId,
        description: `导入斗谱 ${rows.length} 行，新增 ${added} 条位置关联，更新 ${updated} 条药材资料`,
      });
    },
    { maxWait: 5000, timeout: 60000 },
  );
  return {
    total: rows.length,
    added,
    updated,
    skipped,
  };
}
