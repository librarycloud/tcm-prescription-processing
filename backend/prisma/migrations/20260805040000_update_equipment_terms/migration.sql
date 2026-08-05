UPDATE `processing_equipment`
SET `name` = REPLACE(
  REPLACE(`name`, '煎药锅', '煎药机'),
  '打包机', '包装机'
)
WHERE `name` LIKE '%煎药锅%'
   OR `name` LIKE '%打包机%';

UPDATE `print_templates`
SET `layout_json` = REPLACE(
  `layout_json`,
  '"text":"扫码记录设备"',
  '"text":"固定设备码"'
)
WHERE `template_type` = 'EQUIPMENT'
  AND `layout_json` LIKE '%"text":"扫码记录设备"%';
