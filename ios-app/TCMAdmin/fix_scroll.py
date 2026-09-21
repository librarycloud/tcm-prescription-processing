import os
import re

views = [
    "Processing/ProcessingView.swift",
    "E6Imports/E6ImportsView.swift",
    "Packages/PackagesView.swift",
    "Prescriptions/PrescriptionsView.swift",
    "Inventory/InventoryView.swift",
    "Herbs/HerbsView.swift",
    "Stocktaking/StocktakingView.swift",
    "Transfers/TransfersView.swift",
    "Differences/DifferencesView.swift"
]

base_path = "/Users/yunfei/Desktop/tcm/ios-app/TCMAdmin/Views"

for view in views:
    path = os.path.join(base_path, view)
    if not os.path.exists(path):
        continue
        
    with open(path, 'r') as f:
        content = f.read()
        
    # Check what filter variables exist
    triggers = []
    if "var filterStatus" in content or "var filterStatus:" in content:
        triggers.append("filterStatus")
    if "var activeTab" in content:
        triggers.append("activeTab")
    if "var selectedStoreId" in content:
        triggers.append("selectedStoreId")
        
    if not triggers:
        continue
        
    # Find .onAppear and insert before it
    # Because .onAppear is usually at the bottom of the View's body
    
    # Let's insert it inside the main ZStack or VStack, or just attach it to the body.
    # Actually, replacing '.onAppear {' with '.onChange(of: [triggers]) { NotificationCenter.default.post(...) }\n        .onAppear {'
    # But .onChange in iOS 17 takes no arguments for the old value, just 0 arguments closure or 2 arguments.
    # .onChange(of: filterStatus) { _, _ in NotificationCenter.default.post(name: NSNotification.Name("ScrollToTop"), object: nil) }
    
    changes = []
    for t in triggers:
        changes.append(f".onChange(of: {t}) {{ _, _ in NotificationCenter.default.post(name: NSNotification.Name(\"ScrollToTop\"), object: nil) }}")
        
    replacement = "\n        ".join(changes) + "\n        .onAppear {"
    
    if ".onAppear {" in content:
        content = content.replace(".onAppear {", replacement, 1)
        with open(path, 'w') as f:
            f.write(content)
        print(f"Fixed {view}")
    
