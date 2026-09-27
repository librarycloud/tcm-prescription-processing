require 'xcodeproj'
project = Xcodeproj::Project.open('ios-app/TCMAdmin.xcodeproj')
target = project.targets.first
group = project.main_group.find_subpath(File.join('TCMAdmin', 'PaddleOCREngine'), true)
file_ref = group.new_file('SharedOCRManager.swift')
target.source_build_phase.add_file_reference(file_ref)
project.save
