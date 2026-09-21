import os

views = [
    ("Processing/ProcessingView.swift", ["filterStatus", "activeTab"]),
    ("E6Imports/E6ImportsView.swift", ["filterStatus"]),
    ("Packages/PackagesView.swift", ["filterStatus", "activeTab"]),
    ("Prescriptions/PrescriptionsView.swift", ["filterStatus"]),
    ("Inventory/InventoryView.swift", ["selectedStoreId"]),
    ("Herbs/HerbsView.swift", ["selectedStoreId"]),
    ("Stocktaking/StocktakingView.swift", ["filterStatus"]),
    ("Differences/DifferencesView.swift", ["filterStatus"])
]

base = "/Users/yunfei/Desktop/tcm/ios-app/TCMAdmin/Views"

for view, triggers in views:
    path = os.path.join(base, view)
    if not os.path.exists(path): continue
    
    with open(path, 'r') as f:
        content = f.read()
        
    # We want to insert it at the end of the main body view, just before the closing brace of the main View struct
    # A simple way is to find ".background(Color.pageBackground.ignoresSafeArea())" which I often use.
    
    changes = []
    for t in triggers:
        changes.append(f".onChange(of: {t}) {{ _, _ in NotificationCenter.default.post(name: NSNotification.Name(\"ScrollToTop\"), object: nil) }}")
    
    replacement = "\n        ".join(changes) + "\n        .background(Color.pageBackground"
    
    if ".background(Color.pageBackground" in content and ".onChange(of: " not in content:
        content = content.replace(".background(Color.pageBackground", replacement, 1)
        with open(path, 'w') as f:
            f.write(content)
        print(f"Fixed {view}")
