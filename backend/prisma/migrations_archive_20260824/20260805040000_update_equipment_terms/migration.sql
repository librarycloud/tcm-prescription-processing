UPDATE `print_templates`
SET `layout_json` = REPLACE(
  `layout_json`,
  '"text":"扫码记录设备"',
  '"text":"固定设备码"'
)
WHERE `template_type` = 'EQUIPMENT'
  AND `layout_json` LIKE '%"text":"扫码记录设备"%';
