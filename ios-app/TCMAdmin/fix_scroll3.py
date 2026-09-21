import os

views = [
    ("Packages/PackagesView.swift", ["selectedStatus", "selectedSortBy", "selectedStoreId"]),
    ("Prescriptions/PrescriptionsView.swift", ["filterStatus"]),
    ("Inventory/InventoryView.swift", ["selectedStoreId"]),
    ("Stocktaking/StocktakingView.swift", ["filterStatus"])
]

base = "/Users/yunfei/Desktop/tcm/ios-app/TCMAdmin/Views"

for view, triggers in views:
    path = os.path.join(base, view)
    if not os.path.exists(path): continue
    
    with open(path, 'r') as f:
        content = f.read()
        
    changes = []
    for t in triggers:
        changes.append(f".onChange(of: {t}) {{ _, _ in NotificationCenter.default.post(name: NSNotification.Name(\"ScrollToTop\"), object: nil) }}")
    
    replacement = "\n        ".join(changes) + "\n        .scrollDismissesKeyboard(.interactively)"
    
    if ".scrollDismissesKeyboard(.interactively)" in content and ".onChange(of: " not in content:
        content = content.replace(".scrollDismissesKeyboard(.interactively)", replacement, 1)
        with open(path, 'w') as f:
            f.write(content)
        print(f"Fixed {view}")
    else:
        # try task
        replacement2 = "\n        ".join(changes) + "\n        .task {"
        if ".task {" in content and ".onChange(of: " not in content:
            content = content.replace(".task {", replacement2, 1)
            with open(path, 'w') as f:
                f.write(content)
            print(f"Fixed {view} (task)")

