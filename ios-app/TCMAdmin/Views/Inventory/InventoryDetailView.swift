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
                    .scaledFont(14, weight: .bold)
                    .foregroundStyle(Color.ink)
                    .padding(.top, 8)
                
                // 基本信息卡片
                AppCard(padding: 16) {
                    VStack(alignment: .leading, spacing: 12) {
                        HStack(alignment: .top) {
                            Text(item.name)
                                .scaledFont(16, weight: .bold)
                                .foregroundStyle(Color.ink)
                            Spacer()
                            if let price = item.retailPrice, price > 0 {
                                Text("¥\(String(format: "%.2f", price))")
                                    .scaledFont(16, weight: .bold)
                                    .foregroundStyle(Color.danger)
                            }
                        }
                        
                        Divider().foregroundStyle(Color.cardBorder)
                        
                        InfoRowItem(label: "商品编码", value: item.productCode?.isEmpty == false ? item.productCode! : "-")
                        InfoRowItem(label: "商品条码", value: item.barcode?.isEmpty == false ? item.barcode! : "无条码")
                        
                        HStack {
                            Text("规格：\(item.specification?.isEmpty == false ? item.specification! : "-")")
                                .scaledFont(13)
                                .foregroundStyle(Color.muted)
                            Spacer()
                            Text("单位：\(item.unit?.isEmpty == false ? item.unit! : "-")")
                                .scaledFont(13)
                                .foregroundStyle(Color.muted)
                        }
                        
                        HStack {
                            Text("生产厂商")
                                .scaledFont(13)
                                .foregroundStyle(Color.muted)
                            Spacer()
                            Text(item.manufacturer?.isEmpty == false ? item.manufacturer! : "-")
                                .scaledFont(13)
                                .foregroundStyle(Color.muted)
                        }
                        
                        // 高亮总库存 Banner
                        HStack {
                            Text("总库存：")
                                .scaledFont(13)
                                .foregroundStyle(Color.ink)
                            Text("\(String(format: "%g", item.displayStock))")
                                .scaledFont(16, weight: .bold)
                                .foregroundStyle(item.displayStock > 0 ? Color.appPrimary : Color.danger)
                            Text(" \(item.displayUnit)")
                                .scaledFont(13)
                                .foregroundStyle(Color.ink)
                            
                            Spacer()
                            
                            Text("共 ")
                                .scaledFont(13)
                                .foregroundStyle(Color.ink)
                            Text("\(item.inventories?.count ?? 0)")
                                .scaledFont(16, weight: .bold)
                                .foregroundStyle(Color.appPrimary)
                            Text(" 个库存批次")
                                .scaledFont(13)
                                .foregroundStyle(Color.ink)
                        }
                        .padding(.vertical, 10)
                        .padding(.horizontal, 12)
                        .background(Color.appPrimary.opacity(0.1))
                        .clipShape(.rect(cornerRadius: 8))
                    }
                }
                
                // 批次信息
                Text("库存批次明细")
                    .scaledFont(14, weight: .bold)
                    .foregroundStyle(Color.ink)
                    .padding(.top, 8)
                
                if let batches = item.inventories, !batches.isEmpty {
                    VStack(spacing: 12) {
                        ForEach(batches) { batch in
                            AppCard(padding: 16) {
                                VStack(alignment: .leading, spacing: 8) {
                                    HStack(alignment: .center) {
                                        VStack(alignment: .leading, spacing: 6) {
                                            Text("批号：\(batch.batchNo?.isEmpty == false ? batch.batchNo! : "-")")
                                                .scaledFont(14, weight: .semibold)
                                                .foregroundStyle(Color.ink)
                                            
                                            HStack(spacing: 0) {
                                                Text("货位：")
                                                    .scaledFont(14)
                                                    .foregroundStyle(Color.muted)
                                                
                                                let loc = batch.locationName ?? ""
                                                Text(loc.isEmpty ? "未分配" : loc)
                                                    .scaledFont(16, weight: .black)
                                                    .foregroundStyle(loc.isEmpty ? Color.muted : Color.appPrimaryDark)
                                                    .padding(.horizontal, loc.isEmpty ? 0 : 6)
                                                    .padding(.vertical, loc.isEmpty ? 0 : 2)
                                                    .background(loc.isEmpty ? Color.clear : Color.appPrimarySoft)
                                                    .clipShape(.rect(cornerRadius: 4))
                                            }
                                        }
                                        
                                        Spacer()
                                        
                                        VStack(alignment: .trailing, spacing: 2) {
                                            HStack(alignment: .firstTextBaseline, spacing: 3) {
                                                let qty = batch.quantity ?? 0.0
                                                Text(String(format: "%g", qty))
                                                    .scaledFont(24, weight: .bold)
                                                    .foregroundStyle(qty <= 0 ? Color.danger : Color.appPrimaryDark)
                                                Text(item.displayUnit)
                                                    .scaledFont(12, weight: .medium)
                                                    .foregroundStyle(Color.muted)
                                            }
                                        }
                                    }
                                    
                                    let pDate = formatDateOnly(batch.productionDate)
                                    let eDate = formatDateOnly(batch.expiryDate)
                                    let iDate = formatDateOnly(batch.inboundDate)
                                    
                                    if pDate != "-" || eDate != "-" || iDate != "-" {
                                        Divider().foregroundStyle(Color.cardBorder).padding(.top, 4)
                                        HStack {
                                            VStack(alignment: .leading, spacing: 2) {
                                                Text("生产日期").scaledFont(9).foregroundStyle(Color.muted)
                                                Text(pDate).scaledFont(10).foregroundStyle(Color.ink).lineLimit(1)
                                            }.frame(maxWidth: .infinity, alignment: .leading)
                                            
                                            VStack(alignment: .leading, spacing: 2) {
                                                Text("有效期至").scaledFont(9).foregroundStyle(Color.muted)
                                                Text(eDate).scaledFont(10).foregroundStyle(Color.ink).lineLimit(1)
                                            }.frame(maxWidth: .infinity, alignment: .leading)
                                            
                                            VStack(alignment: .leading, spacing: 2) {
                                                Text("入库日期").scaledFont(9).foregroundStyle(Color.muted)
                                                Text(iDate).scaledFont(10).foregroundStyle(Color.muted).lineLimit(1)
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
                                    .scaledFont(32)
                                    .foregroundStyle(Color.muted)
                                Text("该商品暂无库存批次")
                                    .scaledFont(14)
                                    .foregroundStyle(Color.muted)
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
