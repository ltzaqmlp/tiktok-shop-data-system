"""Sync only MY product-ad facts from the existing seven export folders."""
from scan import BASE, ROOT, read
from decimal import Decimal
from pathlib import Path
import hashlib, json, re, subprocess, sys

def sql_string(value):
    return "'" + str(value).replace("'", "''") + "'"

files, records, keys = [], [], set()
for folder in range(1, 8):
    directory = BASE / f'10-0{folder}' / '马来' / '新建文件夹 (2)'
    paths = sorted(directory.glob('Product data*.xlsx'))
    for path in paths:
        match = re.fullmatch(r'Product data (\d{4}-\d{2}-\d{2}) - (\d{4}-\d{2}-\d{2}) - Campaign (\d+)\.xlsx', path.name)
        assert match and match[1] == match[2], path
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        rows = read(path)[2]
        assert rows[1]['B'] in ('商品 ID', '商品ID', 'Product ID'), rows[1]
        source = {'path': str(path), 'sha256': digest, 'date': match[1], 'campaign': match[3]}
        files.append(source)
        for n, row in rows.items():
            if n == 1 or not row.get('B'):
                continue
            assert re.fullmatch(r'\d+', row['B']), row
            assert 'tomato facial mask' not in row.get('A', '').lower(), path
            spend, revenue, orders = (Decimal(row[c].replace(',', '')) for c in ('D', 'G', 'E'))
            assert spend >= 0 and revenue >= 0 and orders >= 0 and orders == int(orders)
            assert spend == spend.quantize(Decimal('.01')) and revenue == revenue.quantize(Decimal('.01'))
            assert row['I'] == 'USD', row
            key = (match[1], match[3], row['B'])
            assert key not in keys, key
            keys.add(key)
            records.append((*key, str(spend), str(revenue), int(orders), json.dumps(source, ensure_ascii=False)))

assert files and len(keys) == len(records)
values = ',\n'.join('(' + ','.join(sql_string(v) for v in record) + ')' for record in records)
statement = r"""\set ON_ERROR_STOP on
BEGIN;
LOCK TABLE fact_ad_product_daily IN SHARE ROW EXCLUSIVE MODE;
CREATE TEMP TABLE before_product_ads AS TABLE fact_ad_product_daily;
CREATE TEMP TABLE excel_ads(biz_date date,campaign_id text,product_id text,spend numeric(18,2),revenue numeric(18,2),orders bigint,source jsonb, PRIMARY KEY(biz_date,campaign_id,product_id));
INSERT INTO excel_ads VALUES
""" + values + """;
DO $$ BEGIN
 ASSERT EXISTS(SELECT 1 FROM dim_shop WHERE id=1 AND market_code='MY');
 ASSERT NOT EXISTS(SELECT 1 FROM fact_ad_product_daily a WHERE a.shop_id=1 AND EXISTS(SELECT 1 FROM excel_ads e WHERE e.biz_date=a.biz_date AND e.campaign_id=a.campaign_id) AND NOT EXISTS(SELECT 1 FROM excel_ads e WHERE (e.biz_date,e.campaign_id,e.product_id)=(a.biz_date,a.campaign_id,a.product_id))), 'Unexpected extra product in source scope';
END $$;
SELECT count(*) FILTER(WHERE a.id IS NULL) missing_rows,
 count(*) FILTER(WHERE a.id IS NOT NULL AND (a.spend,a.attributed_revenue,a.attributed_order_count) IS DISTINCT FROM (e.spend,e.revenue,e.orders)) changed_values
FROM excel_ads e LEFT JOIN fact_ad_product_daily a ON a.shop_id=1 AND (a.biz_date,a.campaign_id,a.product_id)=(e.biz_date,e.campaign_id,e.product_id);
INSERT INTO fact_ad_product_daily(shop_id,market_code,biz_date,currency_code,campaign_id,product_id,spend,attributed_revenue,attributed_order_count,raw_extra)
SELECT 1,'MY',biz_date,'USD',campaign_id,product_id,spend,revenue,orders,jsonb_build_object('_excel_source_sync',source) FROM excel_ads
ON CONFLICT(shop_id,biz_date,campaign_id,product_id) DO UPDATE SET
 spend=excluded.spend,attributed_revenue=excluded.attributed_revenue,attributed_order_count=excluded.attributed_order_count,
 raw_extra=coalesce(fact_ad_product_daily.raw_extra,'{}'::jsonb)||excluded.raw_extra,updated_at=now();
DO $$ BEGIN
 ASSERT NOT EXISTS(SELECT 1 FROM excel_ads e LEFT JOIN fact_ad_product_daily a ON a.shop_id=1 AND (a.biz_date,a.campaign_id,a.product_id)=(e.biz_date,e.campaign_id,e.product_id) WHERE a.id IS NULL OR (a.spend,a.attributed_revenue,a.attributed_order_count,a.currency_code) IS DISTINCT FROM (e.spend,e.revenue,e.orders,'USD'::varchar));
 ASSERT NOT EXISTS(SELECT 1 FROM before_product_ads b JOIN fact_ad_product_daily a USING(id) WHERE NOT(b.shop_id=1 AND EXISTS(SELECT 1 FROM excel_ads e WHERE (e.biz_date,e.campaign_id,e.product_id)=(b.biz_date,b.campaign_id,b.product_id))) AND to_jsonb(b) IS DISTINCT FROM to_jsonb(a)), 'Unrelated row changed';
END $$;
INSERT INTO sys_audit_log(user_id,module,action,target_type,target_id,request_id,success,request_summary,before_data,after_data)
SELECT 2,'import','PRODUCT_AD_EXCEL_SYNC','test_product_ads','MY:1:export-folders-10-01~10-07','excel-source-sync-20261008',true,
 jsonb_build_object('authorization','User requested direct test database update from current daily Product data Excel files','sources',(SELECT jsonb_agg(DISTINCT source) FROM excel_ads),'rows',(SELECT count(*) FROM excel_ads)),
 (SELECT jsonb_agg(to_jsonb(b)) FROM before_product_ads b JOIN excel_ads e ON b.shop_id=1 AND (b.biz_date,b.campaign_id,b.product_id)=(e.biz_date,e.campaign_id,e.product_id)),
 (SELECT jsonb_agg(to_jsonb(a)) FROM fact_ad_product_daily a JOIN excel_ads e ON a.shop_id=1 AND (a.biz_date,a.campaign_id,a.product_id)=(e.biz_date,e.campaign_id,e.product_id));
COMMIT;
SELECT product_id,sum(spend) spend,sum(attributed_revenue) revenue,sum(attributed_order_count) orders FROM fact_ad_product_daily WHERE shop_id=1 AND biz_date BETWEEN '2026-10-01' AND '2026-10-06' GROUP BY product_id ORDER BY product_id;
"""
(ROOT / 'product-ad-excel-sync.sql').write_text(statement, encoding='utf-8')
(ROOT / 'product-ad-excel-sync-sources.json').write_text(json.dumps({'files': files, 'records': records}, ensure_ascii=False, indent=2), encoding='utf-8')
print('Source files:', len(files), 'product rows:', len(records), 'dates:', sorted({r[0] for r in records}))
if '--apply' in sys.argv:
    mounts = json.loads(subprocess.check_output(['docker','inspect','shop-test-postgres-1','--format','{{json .Mounts}}'], text=True))
    assert any(m.get('Name') == 'shop-test_postgres_data' for m in mounts)
    assert all(hashlib.sha256(Path(f['path']).read_bytes()).hexdigest() == f['sha256'] for f in files)
    result = subprocess.run(['docker','exec','-i','shop-test-postgres-1','psql','-U','shop_app','-d','shop_operations'], input=statement, text=True, encoding='utf-8', capture_output=True)
    print(result.stdout)
    if result.returncode:
        raise RuntimeError(result.stderr)
    assert 'COMMIT' in result.stdout
    assert all(hashlib.sha256(Path(f['path']).read_bytes()).hexdigest() == f['sha256'] for f in files)
