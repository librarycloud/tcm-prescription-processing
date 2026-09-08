import { marked } from 'marked';
import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { spawn } from 'node:child_process';
import path from 'node:path';

const projectRoot = path.resolve('.');
const sopPath = path.join(projectRoot, 'docs', '使用说明.md');
const outputPath = path.join(projectRoot, 'docs', '中药处方加工与取药管理系统_业务介绍与操作手册.pdf');
const tempHtmlPath = path.join(projectRoot, 'docs', '_combined_doc_temp.html');

console.log('Reading SOP markdown from:', sopPath);
const sopContent = readFileSync(sopPath, 'utf8');

// Build Part 1 (Function Overview for Leaders & Colleagues - No Technical Jargon)
const part1Markdown = `
# 第一部分：系统定位与功能全貌

## 1. 系统概述与建设目标

中药调剂与煎药服务是中医药医疗与新零售体系的核心支撑环节。但在传统运营模式下，连锁中药房普遍面临以下核心痛点：
- **纸质流转易错漏**：处方依赖纸质单据传递，存在漏煎、错配、药筐混淆风险。
- **分批煎煮追溯难**：单张处方拆分为“急用先行”与“常规批次”时，缺乏全链路批次级追溯与状态看板。
- **取药通知脱节**：药剂完成加工后全靠人工打电话通知，顾客到店取药排队拥堵，取货凭据不统一。
- **百子柜找药耗时**：调剂员依赖经验记忆斗谱，新人培训周期长，换斗调迁混乱。
- **库存盘点繁重**：药品正库存与实物差异难以及时预警，跨门店借调缺少规范流转凭据。

针对上述难题，本项目打造了**面向连锁中药房的一体化处方流转、分批加工、智能斗谱、取药通知与核销管理系统**。系统通过全链路数字化闭环，实现自处方接入、智能分批、调配拍照、流水打卡、包裹封装、多通道通知、到店自提/快递配送至最终核销归档的全生命周期规范化管理。

---

## 2. 终端生态全景架构

系统构建了覆盖 PC 桌面管控、移动手持作业、顾客端查验以及第三方 ERP 自动化同步的完整应用生态：

\`\`\`mermaid
flowchart TD
    E6[(外部 E6 诊所 / 药店 ERP)] -- 只读安全同步 --> E6Sync[E6 数据同步工具]
    E6Sync -- 待确认审核池 --> API

    WebAdmin[Web 管理控制台 PC 端<br/>全局配置 / 调度看板 / 斗谱盘点] --> API
    MiniProgram[微信小程序 移动端<br/>员工作业工作台 / 顾客查验取件] --> API
    Android[Android 药房助手手持端<br/>PDA 离线扫码 / 调配拍照 / 工序打卡] --> API
    WebUser[Web 顾客查询端 PC<br/>自助查验进度 / 提货凭证] --> API

    API((处方流转与加工协同中枢服务))
    
    API --> Notif[多渠道即时通知矩阵<br/>短信 SMS / 邮件 / 企微钉钉飞书机器人]
    API --> Storage[(调剂拍照凭证 / 处方影像存证)]
\`\`\`

### 各终端功能定位与核心场景

| 终端类别 | 适配设备 | 核心定位与适用人群 | 核心业务场景 |
| --- | --- | --- | --- |
| **Web 管理控制台** | PC 电脑（Chrome / Edge） | 综合管理枢纽<br/>（公司管理层、店长、库管） | 处方全量审阅、加工排队调度看板、智能斗谱物理建模、商品盘点初复盘比对、跨门店借调台账、热敏打印模板配置、全量审计日志。 |
| **微信小程序** | 手机（微信生态） | 移动作业与顾客查验<br/>（员工与顾客） | **员工端**：掌上查验看板指标、斗谱拼音极速查位、工序流水打卡、摄像头扫码极速核销包裹。<br/>**顾客端**：实时查验本人处方制作流转进度、获取 6 位提货码与自提二维码。 |
| **Android 药房助手** | 门店手持工业 PDA / 平板 | 现场工业化高频作业<br/>（调剂员、煎药员） | 连续离线 OCR 扫描药盒 SKU、调剂抓配高清拍照留存合规凭据、煎药设备机号扫描与工序计时、柜台快速扫码核销。 |
| **E6 ERP 同步工具** | Windows 工作站 / 服务器 | 数据集成中台<br/>（实施人员 / 自动化） | 单向只读提取外部浪潮佳软 E6 系统的门诊处方与商品库存，自动汇入待审核池，杜绝二次手工录入。 |
| **多通道通知矩阵** | 云端服务 | 智能通知网关<br/>（全自动触发） | 支持多服务商短信（阿里云/腾讯云/火山引擎）、SMTP 邮件，以及企业微信、钉钉、飞书群机器人，多事件异步自动提醒。 |

---

## 3. 核心业务全景闭环

系统贯穿中药调剂全流程，形成了七大闭环阶段：

\`\`\`mermaid
flowchart TD
    A[1. 处方录入 / E6 同步接入] --> B[2. 门店管理员审核确认]
    B --> C[3. 拆分为 1~N 个加工批次]
    C --> D[4. 系统预生成永久 6 位唯一取货码]
    D --> E[5. 工作台排队看板 / 加急调度]
    E --> F[6. 调配抓方完成拍照存证]
    F --> G[7. 工序流转: 浸泡 -> 煎煮 -> 浓缩 -> 打包]
    G --> H[8. 加工完工 -> 生成待取包裹]
    H --> I[9. 沿用 6 位取货码并生成核销二维码]
    I --> J[10. 多渠道下发通知: 短信 / 邮件 / 群机器人]
    J --> K{11. 顾客履约取货方式}
    K -->|到店自提| L[核验 6 位取货码 / 扫二维码出库]
    K -->|同城跑腿| M[核验跑腿配送信息出库]
    K -->|快递物流| N[扫描录入快递单号出库]
    L --> O[包裹状态变更为已领取]
    M --> O
    N --> O
    O --> P{该处方所有拆分批次均已领取?}
    P -->|是| Q[处方全流程终结, 自动完结归档]
    P -->|否| R[保持进行中状态, 等待后续批次交付]
\`\`\`

---

## 4. 六大核心业务功能矩阵

### 4.1 处方全流程与加工工作台
- **全生命周期管理**：支持门店录入自建处方、登记院外外方（支持外方就诊机构、外院医生登记与原方照片影像留档）；支持按门店、医生、剂数、状态多维检索。
- **灵活分批计划**：单张处方支持自由拆分为多个加工批次（例如先煎 3 剂应急自提，余下 11 剂常规模水煎快递送达）。各批次独立指定加工工艺、取货方式、计划完工时间与优先级。
- **排队调度看板**：直观展示今日待办、逾期任务、加工中与加急任务；支持鼠标拖拽调整排队顺序，支持一键恢复加急置顶默认规则。
- **工序流水线作业**：细化支持**调配 ➔ 浸泡 ➔ 煎煮 ➔ 浓缩 ➔ 打包**工序打卡；支持设备机号录入与运行计时；支持设备突发故障工序紧急迁移与作废留档。

### 4.2 智能斗谱与库位可视化
- **物理空间建模**：对百子柜（斗架）、大柜、冷藏冰箱、后仓备料库等不同存药区域进行网格化建模，精准定义抽屉层数与列数。
- **药材绑定与格内序**：支持一斗多药，可精确设置药材在斗内的位置（前/后/左/右）；支持按药材名、拼音简码、药材编码与物理坐标秒级检索。
- **批量迁移与调迁**：提供标准 Excel 模板，支持全店药材全量导出、批量导入，以及整柜药材大调迁的一键导入更新。

### 4.3 包裹生命周期与取药核销
- **取货码终身唯一绑定**：每个加工批次在创建时即生成唯一固定的 6 位数字取货码（展示为 \`XXX-XXX\`），后续加工标签、待取包裹、提货短信及核销凭证全程复用该码，彻底避免二次改单换码导致的拿错药现象。
- **多模式极速核销**：支持扫码枪扫二维码、人工键入 6 位取货码、手持终端摄像头识别核销；覆盖**到店自提**、**同城跑腿**与**快递配送**（出库强制扫码校验快递单号）。
- **防重复与状态自愈**：严格防重复出库拦截；多批次处方在最后一个包裹出库后自动触发处方状态为“已完成”，实现全自动化闭环。

### 4.4 药店商品盘点与库存管理
- **初盘 + 复盘两级工作流**：支持自定义圈选盘点范围，操作员录入初盘数量后系统自动比对账面正库存，自动标识差异商品并推入复盘队列，由复核员复盘确认后经管理员终审调平。
- **库存差异台账**：支持期初历史差异建账、日常合理损耗微调与差异核销，所有核销支持反向撤销以备财务审计。
- **跨门店借调规范**：规范连锁门店间药材调剂互借，完整记录调出门店、调入门店、借调数量、预计归还日期；支持出库确认、分批归还与逾期自动报警。

### 4.5 多通道通知矩阵与标签打印
- **多渠道提货通知**：内置阿里云、腾讯云、火山引擎短信及邮件网关，支持针对加工完成、逾期未取等业务事件自动向顾客推送包含取药门店、地址、取货码的提货信息。
- **企业协作群机器人**：支持企业微信、钉钉、飞书群机器人实时推报，方便店员第一时间获知加急任务与上架包裹。
- **热敏标签可视化排版**：提供加工标签（贴药盘）、包装标签（贴药袋）、取货标签（贴提手外袋）三类模板，自适应 80×50、60×40 等常见热敏规格。

### 4.6 E6 ERP 综合对接与数据中台
- **单向只读安全同步**：直接从外部浪潮佳软 E6 数据库提取处方与商品库存，数据首先入驻「E6 导入审核池」，由药房管理员审核确认后方可转为系统处方，杜绝未经审查直接越权排产。
- **智能合并与映射**：支持同一就诊人在短时间内的多张单据一键合并处方；支持外部医师工号与系统医生档案智能绑定。
- **防重复冲突保护**：采用门店编码与外部订单号联合指纹校验，已排产处方发生外部变更时自动预警并阻断覆盖。

---

## 5. 角色权责与安全隔离矩阵

系统根据登录账号实施基于角色的访问控制（RBAC），严格实现多门店数据物理隔离：

\`\`\`mermaid
flowchart TD
    subgraph S1 ["管理与作业层级（内部系统）"]
        Role0["<b>全局管理员 (Role 0)</b><br/>• 全部门店数据视角与超级控制<br/>• 系统基础字典、通知凭据配置、打印模板<br/>• 全局审计日志查阅与 APP 发布管理"]
        Role2["<b>门店管理员 (Role 2)</b><br/>• 所属门店处方 / 加工 / 包裹 / 斗谱完全控制<br/>• 门店调拨业务审核、盘点与差异台账审批<br/>• 本门店员工账号分配与排班管理"]
        Role3["<b>门店员工 (Role 3)</b><br/>• 现场日常作业：排队开工、调配拍照、工序流水打卡、包裹核销、斗谱查药<br/>• 调拨业务放行：跨门店调拨全流程（发起申请/调入确认/出库扫码/入库打卡）<br/>• 审批隔离保护：E6 处方导入池只读、库存差异台账只读（无权审核审批）"]

        Role0 -->|"下发配置 / 门店管辖"| Role2
        Role2 -->|"管理与排班作业"| Role3
    end

    subgraph S2 ["客户端访问层（外部顾客）"]
        Role1["<b>普通用户 / 顾客 (Role 1)</b><br/>• 用户端 Web / 小程序顾客入口<br/>• 查验本人处方制作流转进度<br/>• 获取取货码与自提二维码核销凭证"]
    end
\`\`\`

### 权限与数据范围对照表

| 角色标识 | 角色名称 | 核心权限与数据管理范围 |
| :---: | :---: | --- |
| **\`0\`** | **全局管理员** | 拥有全系统最高权限。可跨门店查看所有处方与库存，管理全部门店档案、系统基础字典、通知渠道凭据、打印模板及全局审计日志。 |
| **\`2\`** | **门店管理员** | 拥有**所辖单个门店**的完全业务管理权限。负责本门店处方维护、加工计划拆分、包裹管理、斗谱空间建模、商品盘点审批、调拨审批、E6导入审核以及本门店员工账号管理。 |
| **\`3\`** | **门店员工** | 药房一线作业人员（调剂员/煎药员/核销员）。负责排队开工、工序打卡、调配拍照、柜台核销、斗谱查药、商品初盘录入及跨门店借调日常作业；对 E6 处方导入池与库存差异台账具备**只读查阅**权限，无权修改系统配置。 |
| **\`1\`** | **普通用户** | 顾客身份。仅能通过用户端 Web 或小程序查看与本人手机号关联的处方进度、待取包裹、6 位取货码及自提二维码核销凭据。 |

- **多门店数据隔离原则**：门店管理员与门店员工登录后，系统强制锁定其所属门店，无法查看或修改其他门店的任何处方、顾客信息或库存数据；全局管理员可在顶部随时切换查看单店或全部门店汇总。
`;

// Build Part 2: Extract from docs/使用说明.md (removing the top header to fit merged structure)
let cleanSopMarkdown = sopContent
  .replace(/^# 中药处方加工与取药管理系统操作使用手册（SOP）[\s\S]*?## 目录[\s\S]*?---\n/m, '')
  .replace(/^## 1\./m, '## 1.') // keep headings

const fullCombinedMarkdown = `
<div class="cover-page">
  <div class="cover-badge">企业级内部文档 · 业务与作业规程</div>
  <div class="cover-title">中药处方加工与取药管理系统</div>
  <div class="cover-subtitle">系统业务全貌与标准操作规程（SOP）手册</div>
  
  <div class="cover-divider"></div>

  <div class="cover-meta">
    <div class="meta-row"><span class="meta-label">适用对象：</span><span class="meta-value">公司管理层、各门店店长、执业中药师及药房一线作业人员</span></div>
    <div class="meta-row"><span class="meta-label">系统版本：</span><span class="meta-value">V1.0 数字化中药调剂全闭环体系</span></div>
    <div class="meta-row"><span class="meta-label">编撰部门：</span><span class="meta-value">数字化中药房联合项目组</span></div>
    <div class="meta-row"><span class="meta-label">编制日期：</span><span class="meta-value">2026 年 9 月</span></div>
    <div class="meta-row"><span class="meta-label">密级声明：</span><span class="meta-value">内部培训与业务参考专供 · 请勿外传</span></div>
  </div>
</div>

<div class="page-break"></div>

<div class="toc-wrapper">
  <div class="toc-title">文档导读与目录索引</div>
  <div class="toc-desc">本文档由两大部分组成：<strong>第一部分</strong>介绍系统定位、终端生态、全景闭环与六大功能矩阵，供各级管理层与业务骨干全面了解系统全貌；<strong>第二部分</strong>为一线日常实操的标准作业规程（SOP），供门店管理员与药房调剂、煎药、核销人员规范执行。</div>

  <div class="toc-section">
    <div class="toc-part-title">第一部分：系统定位与功能全貌</div>
    <ul class="toc-list">
      <li><span>1. 系统概述与建设目标（行业痛点剖析与建设价值）</span></li>
      <li><span>2. 终端生态全景架构（PC 端、微信小程序、Android PDA、E6 同步）</span></li>
      <li><span>3. 核心业务全景闭环（从处方接入到最终取货归档的 11 步流转）</span></li>
      <li><span>4. 六大核心业务功能矩阵（处方工作台、智能斗谱、包裹核销、盘点借调、通知标签、E6 对接）</span></li>
      <li><span>5. 角色权责与安全隔离矩阵（四大角色定义、权限范围与多门店数据隔离）</span></li>
    </ul>
  </div>

  <div class="toc-section">
    <div class="toc-part-title">第二部分：中药调剂标准操作规程（SOP）</div>
    <ul class="toc-list">
      <li><span>1. 系统入口与权限体系（三大终端对比与账号安全守则）</span></li>
      <li><span>2. 系统初始化配置（新店上线 7 步标准化冷启动指引）</span></li>
      <li><span>3. 处方与外方管理 SOP（自建处方、院外处方拍照存证与修改作废规则）</span></li>
      <li><span>4. E6 诊所处方导入与审核池（状态码对照、审核确认与合并处方）</span></li>
      <li><span>5. 加工工作台与工序流水线 SOP（批次拆分、加急调度、调配拍照、工序打卡、暂缓生成包裹机制）</span></li>
      <li><span>6. 热敏标签设计与打印规范（加工标签、包装标签、取货标签打印要素与校准）</span></li>
      <li><span>7. 取药通知与包裹核销闭环 SOP（短信/机器人通知机制、自提/跑腿/快递三模式核销）</span></li>
      <li><span>8. 智能斗谱与库位管理 SOP（网格建模、药材绑定、可视化找药与 Excel 调迁）</span></li>
      <li><span>9. 商品盘点、库存差异与门店调拨（初盘复盘比对、差异台账、跨门店借调规范）</span></li>
      <li><span>10. 移动端专属实操指南（小程序快捷操作与 Android 药房助手离线 OCR 扫码）</span></li>
      <li><span>11. 一线高频问题排错指南（FAQ 常见问题与应急处置）</span></li>
      <li><span>12. 中药调剂与数据安全合规准则（四查十对、隐私脱敏与专人专号审计）</span></li>
    </ul>
  </div>
</div>

<div class="page-break"></div>

${part1Markdown}

<div class="page-break"></div>

# 第二部分：中药调剂标准操作规程（SOP）

${cleanSopMarkdown}
`;

// Markdown custom alert blockquote support
const renderer = new marked.Renderer();
const originalBlockquote = renderer.blockquote.bind(renderer);
renderer.blockquote = function (token) {
  const text = token.text || '';
  if (text.includes('[!NOTE]')) {
    const cleanText = text.replace(/\[!NOTE\]\s*/g, '');
    return `<div class="alert alert-note"><div class="alert-title">说明 / 背景</div><p>${cleanText}</p></div>`;
  }
  if (text.includes('[!TIP]')) {
    const cleanText = text.replace(/\[!TIP\]\s*/g, '');
    return `<div class="alert alert-tip"><div class="alert-title">提示 / 建议</div><p>${cleanText}</p></div>`;
  }
  if (text.includes('[!IMPORTANT]')) {
    const cleanText = text.replace(/\[!IMPORTANT\]\s*/g, '');
    return `<div class="alert alert-important"><div class="alert-title">重点须知</div><p>${cleanText}</p></div>`;
  }
  if (text.includes('[!WARNING]')) {
    const cleanText = text.replace(/\[!WARNING\]\s*/g, '');
    return `<div class="alert alert-warning"><div class="alert-title">注意事项 / 告警</div><p>${cleanText}</p></div>`;
  }
  return originalBlockquote(token);
};

// Render code blocks: if mermaid, render as <div class="mermaid">
const originalCode = renderer.code.bind(renderer);
renderer.code = function ({ text, lang }) {
  if (lang === 'mermaid') {
    return `<div class="mermaid-container"><div class="mermaid">\n${text}\n</div></div>`;
  }
  return originalCode({ text, lang });
};

marked.use({ renderer });

console.log('Parsing markdown to HTML...');
const parsedContent = marked.parse(fullCombinedMarkdown);

const htmlTemplate = `<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <title>中药处方加工与取药管理系统 - 业务全貌与操作使用手册</title>
  <script src="https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js"></script>
  <style>
    @page {
      size: A4 portrait;
      margin: 18mm 16mm 20mm 16mm;
    }

    * {
      box-sizing: border-box;
      -webkit-print-color-adjust: exact !important;
      print-color-adjust: exact !important;
    }

    body {
      font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", "WenQuanYi Micro Hei", sans-serif;
      font-size: 13px;
      line-height: 1.7;
      color: #2D3748;
      background-color: #FFFFFF;
      margin: 0;
      padding: 0;
    }

    /* Page Break Utility */
    .page-break {
      page-break-before: always;
      break-before: page;
    }

    /* Cover Page */
    .cover-page {
      min-height: 250mm;
      display: flex;
      flex-direction: column;
      justify-content: center;
      padding: 30mm 10mm 10mm 10mm;
    }

    .cover-badge {
      display: inline-block;
      align-self: flex-start;
      background-color: #E8F5E9;
      color: #1B4D3E;
      font-size: 13px;
      font-weight: 600;
      padding: 6px 14px;
      border-radius: 20px;
      border: 1px solid #A3D9A5;
      margin-bottom: 24px;
      letter-spacing: 1px;
    }

    .cover-title {
      font-size: 34px;
      font-weight: 800;
      color: #1B4D3E;
      line-height: 1.25;
      margin-bottom: 12px;
      letter-spacing: 0.5px;
    }

    .cover-subtitle {
      font-size: 18px;
      font-weight: 500;
      color: #4A5568;
      margin-bottom: 30px;
      line-height: 1.5;
    }

    .cover-divider {
      width: 100%;
      height: 4px;
      background: linear-gradient(90deg, #1B4D3E 0%, #2D6A4F 60%, #81C784 100%);
      border-radius: 2px;
      margin-bottom: 40px;
    }

    .cover-meta {
      background-color: #F8FAF9;
      border: 1px solid #E2E8F0;
      border-radius: 12px;
      padding: 24px 28px;
      margin-top: 20px;
    }

    .meta-row {
      display: flex;
      margin-bottom: 12px;
      font-size: 13.5px;
      line-height: 1.6;
    }

    .meta-row:last-child {
      margin-bottom: 0;
    }

    .meta-label {
      width: 100px;
      font-weight: 600;
      color: #718096;
      flex-shrink: 0;
    }

    .meta-value {
      color: #1A202C;
      font-weight: 500;
    }

    /* TOC */
    .toc-wrapper {
      padding: 10mm 5mm;
    }

    .toc-title {
      font-size: 24px;
      font-weight: 700;
      color: #1B4D3E;
      border-bottom: 2px solid #1B4D3E;
      padding-bottom: 10px;
      margin-bottom: 16px;
    }

    .toc-desc {
      font-size: 13px;
      color: #4A5568;
      background-color: #F8FAF9;
      border-left: 4px solid #2D6A4F;
      padding: 12px 16px;
      border-radius: 4px;
      margin-bottom: 24px;
      line-height: 1.6;
    }

    .toc-section {
      margin-bottom: 24px;
    }

    .toc-part-title {
      font-size: 16px;
      font-weight: 700;
      color: #1B4D3E;
      background-color: #E8F5E9;
      padding: 8px 14px;
      border-radius: 6px;
      margin-bottom: 12px;
    }

    .toc-list {
      list-style-type: none;
      padding-left: 8px;
      margin: 0;
    }

    .toc-list li {
      padding: 6px 0;
      font-size: 13.5px;
      color: #2D3748;
      border-bottom: 1px dashed #EDF2F7;
    }

    .toc-list li span {
      display: inline-block;
    }

    /* Typography */
    h1 {
      font-size: 22px;
      font-weight: 700;
      color: #1B4D3E;
      border-bottom: 2px solid #E2E8F0;
      padding-bottom: 8px;
      margin-top: 30px;
      margin-bottom: 16px;
      page-break-after: avoid;
    }

    h2 {
      font-size: 16.5px;
      font-weight: 700;
      color: #1B4D3E;
      border-left: 4.5px solid #2D6A4F;
      padding-left: 10px;
      margin-top: 24px;
      margin-bottom: 12px;
      page-break-after: avoid;
    }

    h3 {
      font-size: 14.5px;
      font-weight: 600;
      color: #2B2D42;
      margin-top: 18px;
      margin-bottom: 8px;
      page-break-after: avoid;
    }

    p {
      margin-top: 0;
      margin-bottom: 10px;
      text-align: justify;
    }

    ul, ol {
      margin-top: 0;
      margin-bottom: 12px;
      padding-left: 20px;
    }

    li {
      margin-bottom: 4px;
    }

    /* Tables */
    table {
      width: 100%;
      border-collapse: collapse;
      margin: 16px 0;
      font-size: 12px;
      page-break-inside: avoid;
    }

    th {
      background-color: #1B4D3E;
      color: #FFFFFF;
      font-weight: 600;
      text-align: left;
      padding: 8px 10px;
      border: 1px solid #1B4D3E;
    }

    td {
      padding: 7px 10px;
      border: 1px solid #E2E8F0;
      vertical-align: top;
    }

    tr:nth-child(even) td {
      background-color: #F8FAF9;
    }

    /* Mermaid Container */
    .mermaid-container {
      margin: 18px 0;
      padding: 16px;
      background-color: #FAFCFA;
      border: 1px solid #E0ECE4;
      border-radius: 8px;
      text-align: center;
      page-break-inside: avoid;
      box-shadow: 0 1px 4px rgba(0, 0, 0, 0.02);
    }

    .mermaid {
      display: inline-block;
      margin: 0 auto;
    }

    .mermaid svg {
      max-width: 100% !important;
      height: auto !important;
    }

    /* Code and Pre */
    code {
      font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
      background-color: #EDF2F7;
      color: #C53030;
      padding: 2px 5px;
      border-radius: 4px;
      font-size: 12px;
    }

    pre {
      background-color: #F7FAFC;
      border: 1px solid #E2E8F0;
      border-radius: 6px;
      padding: 12px;
      overflow-x: auto;
      font-size: 11.5px;
      line-height: 1.5;
      margin: 12px 0;
      page-break-inside: avoid;
    }

    pre code {
      background: none;
      color: #2D3748;
      padding: 0;
    }

    /* Alerts */
    .alert {
      margin: 14px 0;
      padding: 12px 16px;
      border-radius: 6px;
      border-left: 4px solid;
      page-break-inside: avoid;
    }

    .alert-title {
      font-weight: 700;
      font-size: 13px;
      margin-bottom: 4px;
    }

    .alert-note {
      background-color: #EBF8FF;
      border-color: #3182CE;
      color: #2B6CB0;
    }

    .alert-tip {
      background-color: #F0FFF4;
      border-color: #38A169;
      color: #276749;
    }

    .alert-important {
      background-color: #FAF5FF;
      border-color: #805AD5;
      color: #6B46C1;
    }

    .alert-warning {
      background-color: #FFFAF0;
      border-color: #DD6B20;
      color: #C05621;
    }

    hr {
      border: none;
      border-top: 1px solid #E2E8F0;
      margin: 24px 0;
    }
  </style>
</head>
<body>
  ${parsedContent}

  <script>
    mermaid.initialize({
      startOnLoad: false,
      theme: 'base',
      themeVariables: {
        primaryColor: '#E8F5E9',
        primaryTextColor: '#1B4D3E',
        primaryBorderColor: '#2D6A4F',
        lineColor: '#2D6A4F',
        secondaryColor: '#F1F8E9',
        tertiaryColor: '#FFFFFF',
        fontFamily: '-apple-system, BlinkMacSystemFont, "PingFang SC", "Microsoft YaHei", sans-serif',
        fontSize: '12px'
      }
    });

    window.addEventListener('load', async () => {
      try {
        await mermaid.run();
        console.log('Mermaid rendering completed.');
      } catch (e) {
        console.error('Mermaid render error:', e);
      }
      window.mermaidReady = true;
    });
  </script>
</body>
</html>`;

writeFileSync(tempHtmlPath, htmlTemplate, 'utf8');
console.log('Generated temp HTML at:', tempHtmlPath);

// Convert HTML to PDF via Headless Chrome & CDP
async function convertHtmlToPdf() {
  console.log('Launching headless Chrome...');
  const chrome = spawn('/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', [
    '--headless=new',
    '--remote-debugging-port=9222',
    '--disable-gpu',
    '--no-sandbox'
  ]);

  try {
    await new Promise(r => setTimeout(r, 1200));

    console.log('Connecting to Chrome DevTools Protocol...');
    const res = await fetch('http://127.0.0.1:9222/json/new?about:blank', { method: 'PUT' });
    const target = await res.json();
    const ws = new WebSocket(target.webSocketDebuggerUrl);

    await new Promise((resolve, reject) => {
      ws.onopen = resolve;
      ws.onerror = reject;
    });

    let id = 1;
    function send(method, params = {}) {
      return new Promise((resolve, reject) => {
        const msgId = id++;
        const handler = (event) => {
          const data = JSON.parse(event.data);
          if (data.id === msgId) {
            ws.removeEventListener('message', handler);
            if (data.error) reject(data.error);
            else resolve(data.result);
          }
        };
        ws.addEventListener('message', handler);
        ws.send(JSON.stringify({ id: msgId, method, params }));
      });
    }

    await send('Page.enable');
    const fileUrl = 'file://' + tempHtmlPath;
    console.log('Navigating to:', fileUrl);
    await send('Page.navigate', { url: fileUrl });

    console.log('Waiting for Mermaid rendering to complete...');
    await send('Runtime.evaluate', {
      expression: `new Promise((resolve) => {
        if (window.mermaidReady) return resolve(true);
        const timer = setInterval(() => {
          if (window.mermaidReady) {
            clearInterval(timer);
            resolve(true);
          }
        }, 100);
        setTimeout(() => resolve(false), 12000);
      })`,
      awaitPromise: true
    });

    // Give DOM an extra second to stabilize layout
    await new Promise(r => setTimeout(r, 1500));

    console.log('Printing to PDF with running headers and page numbers...');
    const headerHtml = `
      <div style="font-size:8px; width:100%; display:flex; justify-content:space-between; padding:0 16mm; color:#718096; font-family:-apple-system, 'PingFang SC', sans-serif;">
        <span>中药处方加工与取药管理系统</span>
        <span>业务功能全貌与操作使用手册（SOP）</span>
      </div>
    `;

    const footerHtml = `
      <div style="font-size:8px; width:100%; display:flex; justify-content:space-between; padding:0 16mm; color:#718096; font-family:-apple-system, 'PingFang SC', sans-serif;">
        <span>内部业务资料 · 请勿外传</span>
        <span>第 <span class="pageNumber"></span> 页 / 共 <span class="totalPages"></span> 页</span>
      </div>
    `;

    const pdfData = await send('Page.printToPDF', {
      displayHeaderFooter: true,
      headerTemplate: headerHtml,
      footerTemplate: footerHtml,
      printBackground: true,
      marginTop: 0.6,
      marginBottom: 0.6,
      marginLeft: 0.5,
      marginRight: 0.5,
      preferCSSPageSize: true
    });

    const buffer = Buffer.from(pdfData.data, 'base64');
    writeFileSync(outputPath, buffer);
    console.log('PDF generated successfully!');
    console.log('Output location:', outputPath);
    console.log('File size:', (buffer.length / 1024 / 1024).toFixed(2), 'MB');

    ws.close();
    chrome.kill();
  } catch (err) {
    console.error('Error during PDF generation:', err);
    chrome.kill();
    process.exit(1);
  }
}

convertHtmlToPdf();
