"""Replace MY_MAIN dashboard facts for Oct 1-7, atomically, without a backup."""
from pathlib import Path
import hashlib, json, re, subprocess, sys

ROOT = Path(__file__).resolve().parent
SOURCE, TARGET = 'shop-test-postgres-1', 'shop-prod-postgres-1'
TABLES = ['fact_shop_daily','fact_product_daily','fact_product_period','fact_order','fact_order_sku',
          'fact_affiliate_order','fact_ad_campaign_daily','fact_ad_product_daily','fact_refund_detail','agg_dashboard_daily']
START, END = '2026-10-01', '2026-10-07'

def run(container, sql):
    proc = subprocess.run(['docker','exec','-i',container,'psql','-X','-qAt','-v','ON_ERROR_STOP=1','-U','shop_app','-d','shop_operations'],
                          input=sql, text=True, encoding='utf-8', capture_output=True)
    if proc.returncode:
        raise RuntimeError(proc.stderr)
    return proc.stdout.strip()

def literal(text):
    return "'" + text.replace("'", "''") + "'"

def scope(table):
    date = f"date_from>=DATE '{START}' AND date_to<=DATE '{END}'" if table=='fact_product_period' else f"biz_date BETWEEN DATE '{START}' AND DATE '{END}'"
    return f"shop_id=1 AND market_code='MY' AND {date}"

def snapshot(container):
    parts = []
    for table in TABLES:
        parts.append(f"'{table}',json_build_object('rows',(SELECT coalesce(json_agg(row_to_json(t) ORDER BY to_jsonb(t)::text),'[]')::text FROM {table} t WHERE {scope(table)}),'columns',(SELECT json_agg(json_build_array(column_name,udt_name) ORDER BY ordinal_position) FROM information_schema.columns WHERE table_schema='public' AND table_name='{table}'))")
    return json.loads(run(container, "BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY; SELECT json_build_object(" + ','.join(parts) + "); COMMIT;"))

def digest_rows(records):
    # Ignore generated surrogate IDs; compare every other stored field.
    rows = [{k:v for k,v in r.items() if k!='id'} for r in json.loads(records)]
    return hashlib.sha256(json.dumps(sorted(rows,key=lambda r:json.dumps(r,sort_keys=True)),sort_keys=True,ensure_ascii=False).encode()).hexdigest()

def validate_environments():
    for container, volume in [(SOURCE,'shop-test_postgres_data'),(TARGET,'shop-operations_postgres_data')]:
        mounts=json.loads(subprocess.check_output(['docker','inspect',container,'--format','{{json .Mounts}}'],text=True))
        assert any(m.get('Name')==volume for m in mounts), (container,'Wrong data volume')
        assert run(container,"SELECT count(*) FROM dim_shop WHERE id=1 AND shop_key='MY_MAIN' AND market_code='MY';")=='1'
        assert run(container,"SELECT count(*) FROM sys_import_task WHERE status IN ('CREATED','VALIDATING','IMPORTING','AGGREGATING');")=='0', 'An import is active'

def main():
    validate_environments()
    incoming, production = snapshot(SOURCE), snapshot(TARGET)
    for table in TABLES:
        assert incoming[table]['columns']==production[table]['columns'], (table,'Schema differs')
    counts={t:len(json.loads(incoming[t]['rows'])) for t in TABLES}
    shop=json.loads(incoming['fact_shop_daily']['rows'])
    assert {r['biz_date'] for r in shop}=={f'2026-10-0{d}' for d in range(1,8)}, 'Test data does not cover all seven days'
    assert counts['fact_product_period']==0, 'Unexpected period data; review its scope'
    for table in ('fact_order','fact_order_sku','fact_affiliate_order'):
        assert 'tomato facial mask' not in incoming[table]['rows'].lower(), (table,'Fake order remains')
    metrics=json.loads(run(SOURCE,f"SELECT json_build_object('gmv',(SELECT sum(gmv) FROM v_effective_order_daily WHERE {scope('fact_shop_daily')}),'orders',(SELECT sum(order_count) FROM v_effective_order_daily WHERE {scope('fact_shop_daily')}),'quantity',(SELECT sum(sold_qty) FROM v_effective_order_daily WHERE {scope('fact_shop_daily')}),'ad_spend',(SELECT sum(spend) FROM fact_ad_campaign_daily WHERE {scope('fact_ad_campaign_daily')}),'ad_revenue',(SELECT sum(attributed_revenue) FROM fact_ad_campaign_daily WHERE {scope('fact_ad_campaign_daily')}));"))
    summary=dict(source=SOURCE,target=TARGET,market='MY',shop_key='MY_MAIN',date_from=START,date_to=END,
                 source_rows=counts,old_production_rows={t:len(json.loads(production[t]['rows'])) for t in TABLES},metrics=metrics,backup_created=False)
    print(json.dumps(summary,ensure_ascii=False))
    if '--apply' not in sys.argv:
        return
    sql=[r'\set ON_ERROR_STOP on', 'BEGIN;', "SET LOCAL lock_timeout='5s'; SET LOCAL statement_timeout='60s';",
         'LOCK TABLE '+','.join(TABLES+['sys_import_task'])+' IN SHARE ROW EXCLUSIVE MODE;',
         "SELECT pg_advisory_xact_lock(1);",
         "DO $$ BEGIN ASSERT NOT EXISTS(SELECT 1 FROM sys_import_task WHERE status IN ('CREATED','VALIDATING','IMPORTING','AGGREGATING')), 'Import active'; ASSERT EXISTS(SELECT 1 FROM dim_shop WHERE id=1 AND shop_key='MY_MAIN' AND market_code='MY'); END $$;",
         'CREATE TEMP TABLE sync_counts(table_name text,deleted bigint,inserted bigint);',
         'CREATE TEMP TABLE outside_hashes(table_name text,hash text);']
    for table in TABLES:
        data=incoming[table]['rows']; assert '$payload$' not in data
        sql.append(f"CREATE TEMP TABLE incoming_{table} ON COMMIT DROP AS SELECT * FROM json_populate_recordset(NULL::{table},$payload${data}$payload$::json);")
        sql.append(f"INSERT INTO outside_hashes SELECT '{table}',md5(coalesce(string_agg(to_jsonb(t)::text,'' ORDER BY to_jsonb(t)::text),'')) FROM {table} t WHERE NOT({scope(table)});")
    # Period facts are limited to periods wholly within Oct 1-7. Wider historical
    # reports remain outside the replacement and are covered by the hash check.
    for table in TABLES:
        columns=[c for c,_ in incoming[table]['columns'] if c!='id']
        assert all(re.fullmatch(r'[a-z_]+',c) for c in columns)
        col_sql=','.join(columns)
        sql.extend([f"WITH removed AS(DELETE FROM {table} WHERE {scope(table)} RETURNING 1) INSERT INTO sync_counts SELECT '{table}',count(*),0 FROM removed;",
                    f"WITH added AS(INSERT INTO {table}({col_sql}) SELECT {col_sql} FROM incoming_{table} RETURNING 1) UPDATE sync_counts SET inserted=(SELECT count(*) FROM added) WHERE table_name='{table}';",
                    f"DO $$ BEGIN ASSERT (SELECT count(*) FROM {table} WHERE {scope(table)})={counts[table]}, 'Count mismatch: {table}';",
                    f"ASSERT NOT EXISTS((SELECT to_jsonb(t)-'id' FROM {table} t WHERE {scope(table)} EXCEPT ALL SELECT to_jsonb(s)-'id' FROM incoming_{table} s)), 'Data differs: {table}';",
                    f"ASSERT NOT EXISTS((SELECT to_jsonb(s)-'id' FROM incoming_{table} s EXCEPT ALL SELECT to_jsonb(t)-'id' FROM {table} t WHERE {scope(table)})), 'Missing data: {table}';",
                    f"ASSERT (SELECT hash FROM outside_hashes WHERE table_name='{table}')=(SELECT md5(coalesce(string_agg(to_jsonb(t)::text,'' ORDER BY to_jsonb(t)::text),'')) FROM {table} t WHERE NOT({scope(table)})), 'Unrelated rows changed: {table}'; END $$;"])
    sql.extend(["DO $$ BEGIN ASSERT NOT EXISTS(SELECT 1 FROM fact_order_sku s WHERE "+scope('fact_order_sku')+" AND NOT EXISTS(SELECT 1 FROM fact_order o WHERE o.shop_id=s.shop_id AND o.order_id=s.order_id)), 'Orphan order SKU'; END $$;",
                f"INSERT INTO sys_audit_log(user_id,module,action,target_type,target_id,request_id,success,request_summary) VALUES((SELECT id FROM sys_user WHERE username='admin'),'import','TEST_TO_PROD_DASHBOARD_SYNC','shop_date_range','MY_MAIN:{START}~{END}','test-prod-sync-20261008',true,jsonb_build_object('authorization','User requested replacing production MY dashboard data with test Oct 1-7 data, without backup','source','{SOURCE}','backup_created',false,'counts',(SELECT jsonb_agg(row_to_json(c)) FROM sync_counts c)));",
                'COMMIT;', "SELECT json_agg(row_to_json(c)) FROM sync_counts c;"])
    # Transport stays in memory; no source export or production backup is saved.
    output=run(TARGET,'\n'.join(sql))
    print('PRODUCTION TRANSACTION COMMITTED')
    print(output)
    after=snapshot(TARGET)
    assert all(digest_rows(after[t]['rows'])==digest_rows(incoming[t]['rows']) for t in TABLES), 'Post-commit verification differs'
    current_source=snapshot(SOURCE)
    assert all(digest_rows(current_source[t]['rows'])==digest_rows(incoming[t]['rows']) for t in TABLES), 'Test changed during transfer'
    summary['verified']=True
    (ROOT/'result.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
    print('PASS: all 10 table scopes match test; every out-of-scope production row unchanged; test untouched; no backup created')

if __name__=='__main__':
    main()
