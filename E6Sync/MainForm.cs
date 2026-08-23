using System;
using System.Collections.Generic;
using System.Drawing;
using System.IO;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;
using E6Sync.Models;
using E6Sync.Services;

namespace E6Sync
{
    public sealed class MainForm : Form
    {
        private readonly ConfigService configService;
        private readonly LogService log;
        private AppConfig config;
        private E6SyncService syncService;
        private ApiService apiService;
        private readonly System.Windows.Forms.Timer timer = new System.Windows.Forms.Timer();
        private readonly NotifyIcon trayIcon = new NotifyIcon();
        private readonly CancellationTokenSource cancellation = new CancellationTokenSource();

        private Label automaticValue;
        private CheckBox automaticEnabledCheckBox;
        private CheckBox pharmacySyncCheckBox;
        private NumericUpDown intervalNumeric;
        private NumericUpDown pharmacyIntervalNumeric;
        private Label lastSyncValue;
        private Label nextSyncValue;
        private Label pharmacyAutomaticValue;
        private Label pharmacyLastSyncValue;
        private Label pharmacyNextSyncValue;
        private DateTimePicker startDatePicker;
        private DateTimePicker endDatePicker;
        private Button manualSyncButton;
        private Button manualPharmacySyncButton;
        private Button testSqlButton;
        private Button testPharmacySqlButton;
        private ToolStripMenuItem toggleAutomaticMenuItem;
        private ToolStripMenuItem togglePharmacyMenuItem;
        private Label queryCountValue;
        private Label successCountValue;
        private Label duplicateCountValue;
        private Label failureCountValue;
        private ProgressBar progressBar;
        private TextBox logTextBox;
        private bool automaticEnabled = true;
        private bool pharmacyAutomaticEnabled;
        private bool loadingConfiguration;
        private bool isExiting;
        private DateTime nextSyncAt;
        private DateTime pharmacyNextSyncAt;

        public MainForm()
        {
            configService = new ConfigService();
            log = new LogService(configService.BaseDirectory);
            InitializeComponent();
            log.MessageLogged += OnLogMessage;
            Load += MainForm_Load;
            FormClosing += MainForm_FormClosing;
        }

        private void InitializeComponent()
        {
            SuspendLayout();
            AutoScaleMode = AutoScaleMode.Dpi;
            AutoScaleDimensions = new SizeF(96F, 96F);
            Text = "E6 处方同步";
            StartPosition = FormStartPosition.CenterScreen;
            MinimumSize = new Size(760, 620);
            ClientSize = new Size(900, 680);
            Font = new Font("Microsoft YaHei UI", 9F);

            var rootLayout = new TableLayoutPanel
            {
                Dock = DockStyle.Fill,
                ColumnCount = 1,
                RowCount = 4,
                Padding = new Padding(10)
            };
            rootLayout.RowStyles.Add(new RowStyle(SizeType.Absolute, 172F));
            rootLayout.RowStyles.Add(new RowStyle(SizeType.Absolute, 112F));
            rootLayout.RowStyles.Add(new RowStyle(SizeType.Absolute, 96F));
            rootLayout.RowStyles.Add(new RowStyle(SizeType.Percent, 100F));

            var statusGroup = new GroupBox { Text = "自动同步", Dock = DockStyle.Fill, Padding = new Padding(12) };
            var statusLayout = new TableLayoutPanel { Dock = DockStyle.Fill, ColumnCount = 3, RowCount = 6 };
            statusLayout.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 116F));
            statusLayout.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100F));
            statusLayout.ColumnStyles.Add(new ColumnStyle(SizeType.AutoSize));
            for (var statusRow = 0; statusRow < 6; statusRow++) statusLayout.RowStyles.Add(new RowStyle(SizeType.Percent, 16.67F));
            automaticEnabledCheckBox = new CheckBox { Text = "诊所自动同步", AutoSize = true, Anchor = AnchorStyles.Right, Checked = true };
            automaticEnabledCheckBox.CheckedChanged += AutomaticEnabledCheckBox_CheckedChanged;
            pharmacySyncCheckBox = new CheckBox { Text = "药店自动同步", AutoSize = true, Anchor = AnchorStyles.Right, Checked = false };
            pharmacySyncCheckBox.CheckedChanged += PharmacySyncCheckBox_CheckedChanged;
            automaticValue = AddStatusRow(statusLayout, "状态：", "启动中", 0);
            lastSyncValue = AddStatusRow(statusLayout, "上次自动同步：", "未执行", 1);
            nextSyncValue = AddStatusRow(statusLayout, "下次诊所同步：", "--", 2);
            pharmacyAutomaticValue = AddStatusRow(statusLayout, "药店状态：", "已停止", 3);
            pharmacyLastSyncValue = AddStatusRow(statusLayout, "上次药店同步：", "未执行", 4);
            pharmacyNextSyncValue = AddStatusRow(statusLayout, "下次药店同步：", "--", 5);
            intervalNumeric = CreateIntervalControl();
            pharmacyIntervalNumeric = CreateIntervalControl();
            statusLayout.Controls.Add(CreateSyncOptionsPanel(automaticEnabledCheckBox, intervalNumeric), 2, 0);
            statusLayout.Controls.Add(CreateSyncOptionsPanel(pharmacySyncCheckBox, pharmacyIntervalNumeric), 2, 3);
            intervalNumeric.ValueChanged += IntervalNumeric_ValueChanged;
            pharmacyIntervalNumeric.ValueChanged += PharmacyIntervalNumeric_ValueChanged;
            statusGroup.Controls.Add(statusLayout);

            var manualGroup = new GroupBox { Text = "手动同步", Dock = DockStyle.Fill, Padding = new Padding(12) };
            var manualLayout = new FlowLayoutPanel
            {
                Dock = DockStyle.Fill,
                FlowDirection = FlowDirection.LeftToRight,
                WrapContents = true,
                AutoScroll = true,
                Padding = new Padding(2)
            };
            var startLabel = new Label { Text = "开始日期：", AutoSize = true, Margin = new Padding(3, 7, 2, 3) };
            startDatePicker = new DateTimePicker { Format = DateTimePickerFormat.Short, Width = 132, Value = DateTime.Today, Margin = new Padding(2, 3, 12, 3) };
            var endLabel = new Label { Text = "结束日期：", AutoSize = true, Margin = new Padding(3, 7, 2, 3) };
            endDatePicker = new DateTimePicker { Format = DateTimePickerFormat.Short, Width = 132, Value = DateTime.Today, Margin = new Padding(2, 3, 12, 3) };
            manualSyncButton = new Button { Text = "诊所处方同步", AutoSize = true, AutoSizeMode = AutoSizeMode.GrowAndShrink, Padding = new Padding(10, 2, 10, 2), Margin = new Padding(2, 3, 8, 3) };
            manualSyncButton.Click += ManualSyncButton_Click;
            manualPharmacySyncButton = new Button { Text = "药店全量同步", AutoSize = true, AutoSizeMode = AutoSizeMode.GrowAndShrink, Padding = new Padding(10, 2, 10, 2), Margin = new Padding(2, 3, 8, 3) };
            manualPharmacySyncButton.Click += ManualPharmacySyncButton_Click;
            testSqlButton = new Button { Text = "测试 SQL 连接", AutoSize = true, AutoSizeMode = AutoSizeMode.GrowAndShrink, Padding = new Padding(10, 2, 10, 2), Margin = new Padding(2, 3, 3, 3) };
            testSqlButton.Click += TestSqlButton_Click;
            testPharmacySqlButton = new Button { Text = "测试药店 SQL", AutoSize = true, AutoSizeMode = AutoSizeMode.GrowAndShrink, Padding = new Padding(10, 2, 10, 2), Margin = new Padding(2, 3, 3, 3) };
            testPharmacySqlButton.Click += TestPharmacySqlButton_Click;
            manualLayout.Controls.AddRange(new Control[] { startLabel, startDatePicker, endLabel, endDatePicker, manualSyncButton, manualPharmacySyncButton, testSqlButton, testPharmacySqlButton });
            manualGroup.Controls.Add(manualLayout);

            var statsGroup = new GroupBox { Text = "本轮统计", Dock = DockStyle.Fill, Padding = new Padding(12) };
            var statsLayout = new TableLayoutPanel { Dock = DockStyle.Fill, ColumnCount = 4, RowCount = 2 };
            for (var index = 0; index < 4; index++) statsLayout.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 25F));
            statsLayout.RowStyles.Add(new RowStyle(SizeType.Percent, 52F));
            statsLayout.RowStyles.Add(new RowStyle(SizeType.Percent, 48F));
            queryCountValue = AddStatCell(statsLayout, "查询数量", 0);
            successCountValue = AddStatCell(statsLayout, "成功", 1);
            duplicateCountValue = AddStatCell(statsLayout, "重复", 2);
            failureCountValue = AddStatCell(statsLayout, "失败", 3);
            progressBar = new ProgressBar { Dock = DockStyle.Fill, Minimum = 0, Maximum = 1, Margin = new Padding(3, 3, 3, 0) };
            statsLayout.Controls.Add(progressBar, 0, 1);
            statsLayout.SetColumnSpan(progressBar, 4);
            statsGroup.Controls.Add(statsLayout);

            var logGroup = new GroupBox { Text = "运行日志", Dock = DockStyle.Fill, Padding = new Padding(12) };
            logTextBox = new TextBox { Multiline = true, ReadOnly = true, ScrollBars = ScrollBars.Vertical, Dock = DockStyle.Fill, BackColor = Color.White, Font = new Font("Consolas", 9F) };
            logGroup.Controls.Add(logTextBox);

            rootLayout.Controls.Add(statusGroup, 0, 0);
            rootLayout.Controls.Add(manualGroup, 0, 1);
            rootLayout.Controls.Add(statsGroup, 0, 2);
            rootLayout.Controls.Add(logGroup, 0, 3);
            Controls.Add(rootLayout);

            var trayMenu = new ContextMenuStrip();
            trayMenu.Items.Add("打开", null, delegate { ShowFromTray(); });
            trayMenu.Items.Add("立即同步诊所", null, async delegate { await RunAutomaticNowAsync(); });
            trayMenu.Items.Add("立即同步药店", null, async delegate { await RunPharmacyManualNowAsync(); });
            toggleAutomaticMenuItem = new ToolStripMenuItem("暂停自动同步", null, delegate { ToggleAutomatic(); });
            trayMenu.Items.Add(toggleAutomaticMenuItem);
            togglePharmacyMenuItem = new ToolStripMenuItem("恢复药店自动同步", null, delegate { TogglePharmacyAutomatic(); });
            trayMenu.Items.Add(togglePharmacyMenuItem);
            trayMenu.Items.Add(new ToolStripSeparator());
            trayMenu.Items.Add("退出", null, delegate { ExitApplication(); });
            trayIcon.Text = "E6 处方同步";
            trayIcon.Icon = SystemIcons.Application;
            trayIcon.ContextMenuStrip = trayMenu;
            trayIcon.DoubleClick += delegate { ShowFromTray(); };

            timer.Interval = 1000;
            timer.Tick += Timer_Tick;
            ResumeLayout(false);
        }

        private static Label AddStatusRow(TableLayoutPanel parent, string caption, string value, int row)
        {
            parent.Controls.Add(new Label { Text = caption, AutoSize = true, Anchor = AnchorStyles.Left }, 0, row);
            var label = new Label { Text = value, Dock = DockStyle.Fill, TextAlign = ContentAlignment.MiddleLeft, AutoEllipsis = true };
            parent.Controls.Add(label, 1, row);
            return label;
        }

        private static NumericUpDown CreateIntervalControl()
        {
            return new NumericUpDown { Minimum = 1, Maximum = 86400, DecimalPlaces = 0, Width = 78, Anchor = AnchorStyles.Right, Value = 60 };
        }

        private static Control CreateIntervalPanel(string caption, NumericUpDown input)
        {
            var panel = new FlowLayoutPanel { AutoSize = true, AutoSizeMode = AutoSizeMode.GrowAndShrink, WrapContents = false, Anchor = AnchorStyles.Right, Margin = new Padding(0) };
            panel.Controls.Add(new Label { Text = caption, AutoSize = true, Margin = new Padding(0, 4, 4, 0) });
            panel.Controls.Add(input);
            return panel;
        }

        private static Control CreateSyncOptionsPanel(CheckBox enabled, NumericUpDown interval)
        {
            var panel = new FlowLayoutPanel { AutoSize = true, AutoSizeMode = AutoSizeMode.GrowAndShrink, WrapContents = false, Anchor = AnchorStyles.Right, Margin = new Padding(0) };
            enabled.Margin = new Padding(0, 3, 8, 0);
            panel.Controls.Add(enabled);
            panel.Controls.Add(CreateIntervalPanel("间隔(秒)：", interval));
            return panel;
        }

        private static Label AddStatCell(TableLayoutPanel parent, string caption, int column)
        {
            var panel = new FlowLayoutPanel { AutoSize = true, AutoSizeMode = AutoSizeMode.GrowAndShrink, Anchor = AnchorStyles.Left, WrapContents = false, Margin = new Padding(3, 0, 3, 0) };
            panel.Controls.Add(new Label { Text = caption + "：", AutoSize = true, Margin = new Padding(0, 4, 2, 0) });
            var label = new Label { Text = "0", AutoSize = true, Font = new Font("Microsoft YaHei UI", 10F, FontStyle.Bold), Margin = new Padding(0, 1, 0, 0) };
            panel.Controls.Add(label);
            parent.Controls.Add(panel, column, 0);
            return label;
        }

        private void MainForm_Load(object sender, EventArgs e)
        {
            try
            {
                bool created;
                config = configService.LoadOrCreate(out created);
                loadingConfiguration = true;
                automaticEnabled = config.Sync.AutoSyncEnabled;
                pharmacyAutomaticEnabled = config.Sync.PharmacySyncEnabled;
                pharmacySyncCheckBox.Checked = config.Sync.PharmacySyncEnabled;
                automaticEnabledCheckBox.Checked = automaticEnabled;
                intervalNumeric.Value = ClampInterval(config.Sync.IntervalSeconds);
                pharmacyIntervalNumeric.Value = ClampInterval(config.Sync.PharmacyIntervalSeconds);
                loadingConfiguration = false;
                if (created) log.Warn("未找到 config.json，已创建模板：" + configService.ConfigPath);
                apiService = new ApiService(config.Api, log);
                syncService = new E6SyncService(config, configService, new E6DatabaseService(config, log), apiService, log);
                syncService.ProgressChanged += OnSyncProgress;
                UpdateLastSyncLabel();
                UpdatePharmacyLastSyncLabel();
                var errors = configService.Validate(config);
                if (errors.Count > 0)
                {
                    automaticEnabled = false;
                    automaticEnabledCheckBox.Checked = false;
                    automaticEnabledCheckBox.Enabled = false;
                    pharmacySyncCheckBox.Enabled = false;
                    automaticValue.Text = "配置错误";
                    pharmacyAutomaticValue.Text = "配置错误";
                    manualSyncButton.Enabled = false;
                    manualPharmacySyncButton.Enabled = false;
                    foreach (var error in errors) log.Error("配置检查：" + error);
                    log.Warn("请在 EXE 同目录编辑 config.json 后重新启动程序。");
                }
                else
                {
                    log.Info("E6 处方同步已启动");
                    ScheduleNextSync(1);
                    ScheduleNextPharmacySync(1);
                }
            }
            catch (Exception ex)
            {
                automaticEnabled = false;
                automaticValue.Text = "启动失败";
                log.Error("启动异常：" + ex.Message);
            }
            trayIcon.Visible = true;
            timer.Start();
        }

        private async void Timer_Tick(object sender, EventArgs e)
        {
            if (syncService == null) return;
            var tasks = new List<Task>();
            if (automaticEnabled && !syncService.IsClinicBusy && DateTime.Now >= nextSyncAt) tasks.Add(RunAutomaticNowAsync());
            if (pharmacyAutomaticEnabled && !syncService.IsPharmacyBusy && DateTime.Now >= pharmacyNextSyncAt) tasks.Add(RunPharmacyNowAsync());
            UpdateNextSyncLabels();
            if (tasks.Count > 0) await Task.WhenAll(tasks);
        }

        private async Task RunAutomaticNowAsync()
        {
            if (!automaticEnabled || syncService == null || syncService.IsClinicBusy) return;
            manualSyncButton.Enabled = false;
            automaticValue.Text = "运行中";
            ResetStats();
            try
            {
                await syncService.RunAutomaticAsync(cancellation.Token);
                UpdateLastSyncLabel();
            }
            catch (Exception ex)
            {
                log.Error("自动同步异常：" + ex.Message);
            }
            finally
            {
                manualSyncButton.Enabled = true;
                if (automaticEnabled) ScheduleNextSync(config.Sync.IntervalSeconds);
            }
        }

        private async Task RunPharmacyNowAsync()
        {
            if (!pharmacyAutomaticEnabled || syncService == null || syncService.IsPharmacyBusy) return;
            manualPharmacySyncButton.Enabled = false;
            pharmacyAutomaticValue.Text = "运行中";
            try
            {
                var stats = await syncService.RunPharmacyAutomaticAsync(cancellation.Token);
                SetStats(stats);
                UpdatePharmacyLastSyncLabel();
            }
            catch (Exception ex) { log.Error("药店自动同步异常：" + ex.Message); }
            finally
            {
                manualPharmacySyncButton.Enabled = true;
                if (pharmacyAutomaticEnabled) ScheduleNextPharmacySync(config.Sync.PharmacyIntervalSeconds);
            }
        }

        private async Task RunPharmacyManualNowAsync()
        {
            if (syncService == null || syncService.IsPharmacyBusy) return;
            manualPharmacySyncButton.Enabled = false;
            try
            {
                var stats = await syncService.RunPharmacyManualAsync(cancellation.Token);
                SetStats(stats);
                UpdatePharmacyLastSyncLabel();
            }
            catch (Exception ex) { log.Error("药店手动同步异常：" + ex.Message); }
            finally { manualPharmacySyncButton.Enabled = true; }
        }

        private async void ManualSyncButton_Click(object sender, EventArgs e)
        {
            if (syncService == null) return;
            if (startDatePicker.Value.Date > endDatePicker.Value.Date)
            {
                MessageBox.Show(this, "开始日期不能晚于结束日期。", "E6 处方同步", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                return;
            }
            if (syncService.IsClinicBusy)
            {
                log.Warn("自动同步正在执行，手动同步未启动");
                return;
            }
            manualSyncButton.Enabled = false;
            ResetStats();
            try
            {
                await syncService.RunManualAsync(startDatePicker.Value, endDatePicker.Value, cancellation.Token);
            }
            catch (Exception ex)
            {
                log.Error("手动同步异常：" + ex.Message);
                MessageBox.Show(this, "手动同步失败，请查看运行日志。", "E6 处方同步", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
            finally { manualSyncButton.Enabled = true; }
        }

        private async void ManualPharmacySyncButton_Click(object sender, EventArgs e)
        {
            if (syncService == null || syncService.IsPharmacyBusy) return;
            manualPharmacySyncButton.Enabled = false;
            ResetStats();
            try
            {
                var stats = await syncService.RunPharmacyManualAsync(cancellation.Token);
                SetStats(stats);
                UpdatePharmacyLastSyncLabel();
            }
            catch (Exception ex)
            {
                log.Error("药店手动同步异常：" + ex.Message);
                MessageBox.Show(this, "药店手动同步失败，请查看运行日志。", "E6同步", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
            finally { manualPharmacySyncButton.Enabled = true; }
        }

        private async void TestSqlButton_Click(object sender, EventArgs e)
        {
            if (syncService == null || syncService.IsClinicBusy) return;
            testSqlButton.Enabled = false;
            try
            {
                await Task.Run(() => new E6DatabaseService(config, log).TestConnection());
                log.Info("SQL Server 连接测试成功");
                MessageBox.Show(this, "SQL Server 连接成功。", "E6 处方同步", MessageBoxButtons.OK, MessageBoxIcon.Information);
            }
            catch (Exception ex)
            {
                log.Error("SQL Server 连接测试失败：" + ex.Message);
                MessageBox.Show(this, "SQL Server 连接失败，请查看日志并检查 config.json。", "E6 处方同步", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
            finally { testSqlButton.Enabled = true; }
        }

        private async void TestPharmacySqlButton_Click(object sender, EventArgs e)
        {
            if (syncService == null || syncService.IsPharmacyBusy) return;
            testPharmacySqlButton.Enabled = false;
            try { await Task.Run(() => new E6DatabaseService(config, log).TestPharmacyConnection()); log.Info("药店 SQL Server 连接测试成功"); MessageBox.Show(this, "药店 SQL Server 连接成功。", "E6同步", MessageBoxButtons.OK, MessageBoxIcon.Information); }
            catch (Exception ex) { log.Error("药店 SQL Server 连接测试失败：" + ex.Message); MessageBox.Show(this, "药店 SQL Server 连接失败，请查看日志并检查 pharmacyE6 配置。", "E6同步", MessageBoxButtons.OK, MessageBoxIcon.Error); }
            finally { testPharmacySqlButton.Enabled = true; }
        }

        private void OnSyncProgress(SyncProgress progress)
        {
            if (InvokeRequired) { BeginInvoke(new Action<SyncProgress>(OnSyncProgress), progress); return; }
            progressBar.Maximum = Math.Max(1, progress.Total);
            progressBar.Value = Math.Min(progress.Current, progressBar.Maximum);
            SetStats(progress.Stats);
        }

        private void OnLogMessage(string message)
        {
            if (IsDisposed) return;
            if (InvokeRequired) { BeginInvoke(new Action<string>(OnLogMessage), message); return; }
            logTextBox.AppendText(message + Environment.NewLine);
        }

        private void ResetStats()
        {
            progressBar.Maximum = 1;
            progressBar.Value = 0;
            SetStats(new SyncStats());
        }

        private void SetStats(SyncStats stats)
        {
            queryCountValue.Text = stats.QueryCount.ToString();
            successCountValue.Text = stats.SuccessCount.ToString();
            duplicateCountValue.Text = stats.DuplicateCount.ToString();
            failureCountValue.Text = stats.FailureCount.ToString();
        }

        private void ScheduleNextSync(int seconds)
        {
            nextSyncAt = DateTime.Now.AddSeconds(Math.Max(1, seconds));
            automaticValue.Text = "运行中";
        }

        private void ScheduleNextPharmacySync(int seconds)
        {
            pharmacyNextSyncAt = DateTime.Now.AddSeconds(Math.Max(1, seconds));
            pharmacyAutomaticValue.Text = "运行中";
        }

        private void UpdateNextSyncLabels()
        {
            nextSyncValue.Text = GetNextSyncText(automaticEnabled, nextSyncAt, syncService != null && syncService.IsClinicBusy);
            pharmacyNextSyncValue.Text = GetNextSyncText(pharmacyAutomaticEnabled, pharmacyNextSyncAt, syncService != null && syncService.IsPharmacyBusy);
        }

        private static string GetNextSyncText(bool enabled, DateTime nextAt, bool busy)
        {
            if (!enabled) return "已暂停";
            if (busy) return "同步中";
            var seconds = (int)Math.Ceiling((nextAt - DateTime.Now).TotalSeconds);
            return seconds > 0 ? seconds + " 秒后" : "即将执行";
        }

        private static decimal ClampInterval(int value)
        {
            return Math.Max(1, Math.Min(86400, value));
        }

        private void UpdateLastSyncLabel()
        {
            lastSyncValue.Text = string.IsNullOrWhiteSpace(config.Sync.LastSyncTime) ? "未执行（请先手动同步）" : config.Sync.LastSyncTime;
        }

        private void UpdatePharmacyLastSyncLabel()
        {
            pharmacyLastSyncValue.Text = string.IsNullOrWhiteSpace(config.Sync.LastPharmacySyncTime) ? "未执行（请先手动同步）" : config.Sync.LastPharmacySyncTime;
        }

        private void ToggleAutomatic()
        {
            SetAutomaticEnabled(!automaticEnabled, true);
        }

        private void AutomaticEnabledCheckBox_CheckedChanged(object sender, EventArgs e)
        {
            if (loadingConfiguration) return;
            SetAutomaticEnabled(automaticEnabledCheckBox.Checked, true);
        }

        private void PharmacySyncCheckBox_CheckedChanged(object sender, EventArgs e)
        {
            if (loadingConfiguration || config == null) return;
            pharmacyAutomaticEnabled = pharmacySyncCheckBox.Checked;
            config.Sync.PharmacySyncEnabled = pharmacyAutomaticEnabled;
            try { configService.Save(config); } catch (Exception ex) { log.Error("保存药店同步设置失败：" + ex.Message); }
            if (pharmacyAutomaticEnabled) { ScheduleNextPharmacySync(config.Sync.PharmacyIntervalSeconds); log.Info("药店自动同步已恢复"); }
            else { pharmacyAutomaticValue.Text = "已停止"; pharmacyNextSyncValue.Text = "已暂停"; log.Info("药店自动同步已暂停"); }
        }

        private void IntervalNumeric_ValueChanged(object sender, EventArgs e)
        {
            if (loadingConfiguration || config == null) return;
            config.Sync.IntervalSeconds = (int)intervalNumeric.Value;
            try { configService.Save(config); } catch (Exception ex) { log.Error("保存诊所同步间隔失败：" + ex.Message); }
            if (automaticEnabled) ScheduleNextSync(config.Sync.IntervalSeconds);
        }

        private void PharmacyIntervalNumeric_ValueChanged(object sender, EventArgs e)
        {
            if (loadingConfiguration || config == null) return;
            config.Sync.PharmacyIntervalSeconds = (int)pharmacyIntervalNumeric.Value;
            try { configService.Save(config); } catch (Exception ex) { log.Error("保存药店同步间隔失败：" + ex.Message); }
            if (pharmacyAutomaticEnabled) ScheduleNextPharmacySync(config.Sync.PharmacyIntervalSeconds);
        }

        private void SetAutomaticEnabled(bool enabled, bool save)
        {
            automaticEnabled = enabled;
            if (config != null && save)
            {
                config.Sync.AutoSyncEnabled = enabled;
                try { configService.Save(config); }
                catch (Exception ex) { log.Error("保存自动同步设置失败：" + ex.Message); }
            }
            if (automaticEnabledCheckBox != null && automaticEnabledCheckBox.Checked != enabled)
            {
                loadingConfiguration = true;
                automaticEnabledCheckBox.Checked = enabled;
                loadingConfiguration = false;
            }
            if (toggleAutomaticMenuItem != null)
                toggleAutomaticMenuItem.Text = automaticEnabled ? "暂停自动同步" : "恢复自动同步";
            if (automaticEnabled)
            {
                ScheduleNextSync(config == null ? 60 : config.Sync.IntervalSeconds);
                log.Info("自动同步已恢复");
            }
            else
            {
                automaticValue.Text = "已停止";
                nextSyncValue.Text = "已暂停";
                log.Info("自动同步已暂停");
            }
        }

        private void TogglePharmacyAutomatic()
        {
            loadingConfiguration = true;
            pharmacySyncCheckBox.Checked = !pharmacyAutomaticEnabled;
            loadingConfiguration = false;
            PharmacySyncCheckBox_CheckedChanged(pharmacySyncCheckBox, EventArgs.Empty);
        }

        private void ShowFromTray()
        {
            Show();
            WindowState = FormWindowState.Normal;
            Activate();
        }

        private void MainForm_FormClosing(object sender, FormClosingEventArgs e)
        {
            if (isExiting) return;
            e.Cancel = true;
            Hide();
            trayIcon.ShowBalloonTip(1500, "E6 处方同步", "程序正在系统托盘中运行。", ToolTipIcon.Info);
        }

        private void ExitApplication()
        {
            isExiting = true;
            automaticEnabled = false;
            pharmacyAutomaticEnabled = false;
            timer.Stop();
            cancellation.Cancel();
            log.Info("E6 处方同步已停止");
            trayIcon.Visible = false;
            apiService?.Dispose();
            Close();
        }

        protected override void Dispose(bool disposing)
        {
            if (disposing)
            {
                timer.Dispose();
                trayIcon.Dispose();
                cancellation.Dispose();
            }
            base.Dispose(disposing);
        }
    }
}
