import re

file_path = "/Users/yunfei/Desktop/tcm/ios-app/TCMAdmin/Views/Transfers/TransfersView.swift"
with open(file_path, "r") as f:
    content = f.read()

# We need to extract the sheet content into a new view `TransferReturnSheetContent`
# But it's easier to just pass the item to a new struct and bind the states.

# Actually, the simplest fix for SwiftUI .sheet outer state bug is to wrap the content inside a wrapper view that observes the states, or just use a tiny helper view.

# Let's see the sheet code
sheet_regex = r"\.sheet\(item: \$returnDialogItem\) \{ item in\n\s*NavigationStack \{\n.*?\n\s*\}\n\s*\}"
match = re.search(sheet_regex, content, re.DOTALL)
if match:
    print("Found sheet code")
else:
    print("Not found")

