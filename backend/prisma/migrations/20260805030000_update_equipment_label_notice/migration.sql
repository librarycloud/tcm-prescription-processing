UPDATE `print_templates`
SET `layout_json` = REPLACE(
  `layout_json`,
  '"text":"固定设备码"',
  '"text":"扫码记录设备"'
)
WHERE `template_type` = 'EQUIPMENT'
  AND `layout_json` LIKE '%"text":"固定设备码"%';
