import SwiftUI

@MainActor
public struct InventoryDetailView: View {
    public let item: InventoryItem
    
    public init(item: InventoryItem) {
        self.item = item
    }
    
    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                // Section Header
                Text("商品信息")
                    .font(.system(size: (14) * ThemeManager.shared.fontScale, weight: .bold))
                    .foregroundColor(.ink)
                    .padding(.top, 8)
                
                // 基本信息卡片
                AppCard(padding: 16) {
                    VStack(alignment: .leading, spacing: 12) {
                        HStack(alignment: .top) {
                            Text(item.name)
                                .font(.system(size: (16) * ThemeManager.shared.fontScale, weight: .bold))
                                .foregroundColor(.ink)
                            Spacer()
                            if let price = item.retailPrice, price > 0 {
                                Text("¥\(String(format: "%.2f", price))")
                                    .font(.system(size: (16) * ThemeManager.shared.fontScale, weight: .bold))
                                    .foregroundColor(.danger)
                            }
                        }
                        
                        Divider().foregroundColor(Color.cardBorder)
                        
                        InfoRowItem(label: "商品编码", value: item.productCode?.isEmpty == false ? item.productCode! : "-")
                        InfoRowItem(label: "商品条码", value: item.barcode?.isEmpty == false ? item.barcode! : "无条码")
                        
                        HStack {
                            Text("规格：\(item.specification?.isEmpty == false ? item.specification! : "-")")
                                .font(.system(size: (13) * ThemeManager.shared.fontScale))
                                .foregroundColor(.muted)
                            Spacer()
                            Text("单位：\(item.unit?.isEmpty == false ? item.unit! : "-")")
                                .font(.system(size: (13) * ThemeManager.shared.fontScale))
                                .foregroundColor(.muted)
                        }
                        
                        HStack {
                            Text("生产厂商")
                                .font(.system(size: (13) * ThemeManager.shared.fontScale))
                                .foregroundColor(.muted)
                            Spacer()
                            Text(item.manufacturer?.isEmpty == false ? item.manufacturer! : "-")
                                .font(.system(size: (13) * ThemeManager.shared.fontScale))
                                .foregroundColor(.muted)
                        }
                        
                        // 高亮总库存 Banner
                        HStack {
                            Text("总库存：")
                                .font(.system(size: (13) * ThemeManager.shared.fontScale))
                                .foregroundColor(.ink)
                            Text("\(String(format: "%g", item.displayStock))")
                                .font(.system(size: (16) * ThemeManager.shared.fontScale, weight: .bold))
                                .foregroundColor(item.displayStock > 0 ? .appPrimary : .danger)
                            Text(" \(item.displayUnit)")
                                .font(.system(size: (13) * ThemeManager.shared.fontScale))
                                .foregroundColor(.ink)
                            
                            Spacer()
                            
                            Text("共 ")
                                .font(.system(size: (13) * ThemeManager.shared.fontScale))
                                .foregroundColor(.ink)
                            Text("\(item.inventories?.count ?? 0)")
                                .font(.system(size: (16) * ThemeManager.shared.fontScale, weight: .bold))
                                .foregroundColor(.appPrimary)
                            Text(" 个库存批次")
                                .font(.system(size: (13) * ThemeManager.shared.fontScale))
                                .foregroundColor(.ink)
                        }
                        .padding(.vertical, 10)
                        .padding(.horizontal, 12)
                        .background(Color.appPrimary.opacity(0.1))
                        .cornerRadius(8)
                    }
                }
                
                // 批次信息
                Text("库存批次明细")
                    .font(.system(size: (14) * ThemeManager.shared.fontScale, weight: .bold))
                    .foregroundColor(.ink)
                    .padding(.top, 8)
                
                if let batches = item.inventories, !batches.isEmpty {
                    VStack(spacing: 12) {
                        ForEach(batches) { batch in
                            AppCard(padding: 16) {
                                VStack(alignment: .leading, spacing: 8) {
                                    HStack(alignment: .top) {
                                        VStack(alignment: .leading, spacing: 6) {
                                            Text("批号：\(batch.batchNo?.isEmpty == false ? batch.batchNo! : "-")")
                                                .font(.system(size: (14) * ThemeManager.shared.fontScale, weight: .semibold))
                                                .foregroundColor(.ink)
                                            
                                            HStack(spacing: 0) {
                                                Text("货位：")
                                                    .font(.system(size: (14) * ThemeManager.shared.fontScale))
                                                    .foregroundColor(.muted)
                                                
                                                let loc = batch.locationName ?? ""
                                                Text(loc.isEmpty ? "未分配" : loc)
                                                    .font(.system(size: (16) * ThemeManager.shared.fontScale, weight: .black))
                                                    .foregroundColor(loc.isEmpty ? .muted : .appPrimaryDark)
                                                    .padding(.horizontal, loc.isEmpty ? 0 : 6)
                                                    .padding(.vertical, loc.isEmpty ? 0 : 2)
                                                    .background(loc.isEmpty ? Color.clear : Color.appPrimarySoft)
                                                    .cornerRadius(4)
                                            }
                                        }
                                        
                                        Spacer()
                                        
                                        VStack(alignment: .trailing, spacing: 4) {
                                            HStack(alignment: .firstTextBaseline, spacing: 2) {
                                                let qty = batch.quantity ?? 0.0
                                                Text(String(format: "%g", qty))
                                                    .font(.system(size: (15) * ThemeManager.shared.fontScale, weight: .bold))
                                                    .foregroundColor(qty <= 0 ? .danger : .appPrimaryDark)
                                                Text(item.displayUnit)
                                                    .font(.system(size: (12) * ThemeManager.shared.fontScale))
                                                    .foregroundColor(.muted)
                                            }
                                        }
                                    }
                                    
                                    let pDate = formatDateOnly(batch.productionDate)
                                    let eDate = formatDateOnly(batch.expiryDate)
                                    let iDate = formatDateOnly(batch.inboundDate)
                                    
                                    if pDate != "-" || eDate != "-" || iDate != "-" {
                                        Divider().foregroundColor(Color.cardBorder).padding(.top, 4)
                                        HStack {
                                            VStack(alignment: .leading, spacing: 2) {
                                                Text("生产日期").font(.system(size: (9) * ThemeManager.shared.fontScale)).foregroundColor(.muted)
                                                Text(pDate).font(.system(size: (10) * ThemeManager.shared.fontScale)).foregroundColor(.ink).lineLimit(1)
                                            }.frame(maxWidth: .infinity, alignment: .leading)
                                            
                                            VStack(alignment: .leading, spacing: 2) {
                                                Text("有效期至").font(.system(size: (9) * ThemeManager.shared.fontScale)).foregroundColor(.muted)
                                                Text(eDate).font(.system(size: (10) * ThemeManager.shared.fontScale)).foregroundColor(.ink).lineLimit(1)
                                            }.frame(maxWidth: .infinity, alignment: .leading)
                                            
                                            VStack(alignment: .leading, spacing: 2) {
                                                Text("入库日期").font(.system(size: (9) * ThemeManager.shared.fontScale)).foregroundColor(.muted)
                                                Text(iDate).font(.system(size: (10) * ThemeManager.shared.fontScale)).foregroundColor(.muted).lineLimit(1)
                                            }.frame(maxWidth: .infinity, alignment: .leading)
                                        }
                                        .padding(.top, 2)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    AppCard(padding: 24) {
                        HStack {
                            Spacer()
                            VStack(spacing: 8) {
                                Image(systemName: "archivebox")
                                    .font(.system(size: (32) * ThemeManager.shared.fontScale))
                                    .foregroundColor(.muted)
                                Text("该商品暂无库存批次")
                                    .font(.system(size: (14) * ThemeManager.shared.fontScale))
                                    .foregroundColor(.muted)
                            }
                            Spacer()
                        }
                    }
                }
            }
            .padding(16)
        }
        .background(Color.pageBackground)
        .navigationTitle("商品详情")
        .navigationBarTitleDisplayMode(.inline)
    }
}
