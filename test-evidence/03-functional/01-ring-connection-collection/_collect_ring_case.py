import os, re, time, json, sqlite3, subprocess, textwrap, shutil
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(r'D:\newstart\test-evidence\03-functional\01-ring-connection-collection')
SS = ROOT / 'screenshots'
DB = ROOT / 'db-snapshots'
API = ROOT / 'api-captures'
for p in [ROOT, SS, DB, API]:
    p.mkdir(parents=True, exist_ok=True)

run_lines = []
def log(msg):
    ts = time.strftime('%Y-%m-%d %H:%M:%S')
    line = f'[{ts}] {msg}'
    print(line, flush=True)
    run_lines.append(line)
    (ROOT / 'run.log').write_text('\n'.join(run_lines) + '\n', encoding='utf-8')

def run(cmd, check=True, capture=True, text=True, timeout=60):
    log('RUN ' + ' '.join(cmd))
    cp = subprocess.run(cmd, capture_output=capture, text=text, timeout=timeout)
    if check and cp.returncode != 0:
        raise RuntimeError(f'command failed: {cmd}\nstdout={cp.stdout}\nstderr={cp.stderr}')
    return cp

def adb(args, **kwargs):
    return run(['adb'] + args, **kwargs)

def adb_bytes(args, timeout=60):
    cp = subprocess.run(['adb'] + args, capture_output=True, text=False, timeout=timeout)
    if cp.returncode != 0:
        raise RuntimeError(f'adb failed: {args}\nstdout={cp.stdout}\nstderr={cp.stderr}')
    return cp.stdout

def dump_ui(name):
    remote = '/sdcard/uidump-ring.xml'
    adb(['shell', 'uiautomator', 'dump', remote], timeout=30)
    local = SS / f'{name}.xml'
    adb(['pull', remote, str(local)], timeout=30)
    return local

def screenshot(name):
    path = SS / f'{name}.png'
    data = adb_bytes(['exec-out', 'screencap', '-p'], timeout=30)
    path.write_bytes(data)
    return path

bounds_re = re.compile(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]')
def center(bounds):
    m = bounds_re.match(bounds)
    if not m:
        return None
    x1,y1,x2,y2 = map(int, m.groups())
    return ((x1+x2)//2, (y1+y2)//2)

def parse_nodes(xml_path):
    root = ET.parse(xml_path).getroot()
    return root.iter('node')

def find_node(xml_path, resource_id=None, text=None, content_desc=None):
    for node in parse_nodes(xml_path):
        if resource_id and node.attrib.get('resource-id') != resource_id:
            continue
        if text is not None and node.attrib.get('text') != text:
            continue
        if content_desc is not None and node.attrib.get('content-desc') != content_desc:
            continue
        return node.attrib
    return None

def tap_xy(x, y):
    adb(['shell', 'input', 'tap', str(x), str(y)], timeout=30)
    time.sleep(0.8)

def tap_node(node):
    c = center(node.get('bounds',''))
    if not c:
        raise RuntimeError('no bounds for node')
    tap_xy(*c)

def swipe(x1,y1,x2,y2,duration=300):
    adb(['shell', 'input', 'swipe', str(x1), str(y1), str(x2), str(y2), str(duration)], timeout=30)
    time.sleep(1.0)

def ensure_app_device_page():
    adb(['shell', 'am', 'start', '-W', '-n', 'com.example.newstart/.MainActivity'], timeout=60)
    time.sleep(2)
    xml = dump_ui('nav_check')
    node = find_node(xml, resource_id='com.example.newstart:id/navigation_device')
    if node and node.get('selected') != 'true':
        tap_node(node)
        time.sleep(1)
    return dump_ui('device_top_initial')

def goto_device_controls():
    xml = dump_ui('device_controls_probe')
    if find_node(xml, resource_id='com.example.newstart:id/btn_connect'):
        return xml
    swipe(2200, 1300, 2200, 500, 350)
    return dump_ui('device_controls_after_swipe')

def goto_top():
    swipe(2200, 600, 2200, 1500, 350)
    swipe(2200, 600, 2200, 1500, 350)
    return dump_ui('device_top_after_scroll_up')

def goto_today_page(name):
    xml = dump_ui(f'{name}_nav_probe')
    node = find_node(xml, resource_id='com.example.newstart:id/navigation_home')
    if node:
        tap_node(node)
        time.sleep(1)
    screenshot(name)
    dump_ui(name)

def start_recording():
    adb(['shell', 'rm', '-f', '/sdcard/ring_case.mp4'], timeout=30)
    adb(['shell', 'sh', '-c', 'screenrecord --time-limit 180 /sdcard/ring_case.mp4 >/dev/null 2>&1 &'], timeout=30)
    log('started screenrecord')

def stop_recording():
    try:
        adb(['shell', 'pkill', '-INT', 'screenrecord'], check=False, timeout=30)
    except Exception:
        pass
    time.sleep(2)
    adb(['pull', '/sdcard/ring_case.mp4', str(ROOT / 'screenrecord.mp4')], check=False, timeout=60)


def export_db_snapshot(prefix):
    db_name = 'sleep_health_database'
    for suffix in ['', '-wal', '-shm']:
        local = DB / f'{prefix}_{db_name}{suffix}'
        with open(local, 'wb') as f:
            cp = subprocess.run(['adb', 'exec-out', 'run-as', 'com.example.newstart', 'cat', f'databases/{db_name}{suffix}'], stdout=f, stderr=subprocess.PIPE)
        if cp.returncode != 0:
            local.write_bytes(b'')
    return DB / f'{prefix}_{db_name}'


def query_db(prefix):
    db_path = DB / f'{prefix}_sleep_health_database'
    wal = DB / f'{prefix}_sleep_health_database-wal'
    shm = DB / f'{prefix}_sleep_health_database-shm'
    out = {'prefix': prefix, 'tables': [], 'queries': {}, 'errors': []}
    if not db_path.exists() or db_path.stat().st_size == 0:
        out['errors'].append('database export missing')
        (DB / f'{prefix}_db_overview.json').write_text(json.dumps(out, ensure_ascii=False, indent=2), encoding='utf-8')
        return out
    tmp_db = DB / f'{prefix}_query.db'
    shutil.copyfile(db_path, tmp_db)
    if wal.exists() and wal.stat().st_size > 0:
        shutil.copyfile(wal, DB / f'{prefix}_query.db-wal')
    if shm.exists() and shm.stat().st_size > 0:
        shutil.copyfile(shm, DB / f'{prefix}_query.db-shm')
    try:
        conn = sqlite3.connect(str(tmp_db))
        cur = conn.cursor()
        cur.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")
        out['tables'] = [r[0] for r in cur.fetchall()]
        for table in ['devices', 'health_metrics', 'ppg_samples']:
            try:
                cur.execute(f'SELECT COUNT(*) FROM {table}')
                count = cur.fetchone()[0]
                out['queries'][table] = {'count': count}
                cur.execute(f'PRAGMA table_info({table})')
                cols = [r[1] for r in cur.fetchall()]
                out['queries'][table]['columns'] = cols
                if table == 'devices':
                    order_col = 'lastSyncTime'
                else:
                    order_col = 'timestamp'
                sample_cols = ', '.join(cols[:min(len(cols),8)])
                cur.execute(f'SELECT {sample_cols} FROM {table} ORDER BY {order_col} DESC LIMIT 5')
                rows = cur.fetchall()
                out['queries'][table]['sample_rows'] = rows
            except Exception as e:
                out['queries'][table] = {'error': str(e)}
        conn.close()
    except Exception as e:
        out['errors'].append(str(e))
    (DB / f'{prefix}_db_overview.json').write_text(json.dumps(out, ensure_ascii=False, indent=2), encoding='utf-8')
    return out


def extract_status(xml_path):
    keys = {
        'device_name': 'com.example.newstart:id/tv_device_name',
        'connection_status': 'com.example.newstart:id/tv_connection_status',
        'risk_badge': 'com.example.newstart:id/tv_risk_badge',
        'risk_title': 'com.example.newstart:id/tv_risk_title',
        'risk_summary': 'com.example.newstart:id/tv_risk_summary',
        'risk_supporting': 'com.example.newstart:id/tv_risk_supporting',
    }
    result = {}
    for k, rid in keys.items():
        node = find_node(xml_path, resource_id=rid)
        result[k] = node.get('text') if node else None
    return result

summary = {}
try:
    adb(['logcat', '-c'])
    ensure_app_device_page()
    start_recording()

    bt = adb(['shell', 'dumpsys', 'bluetooth_manager'], timeout=60)
    (ROOT / 'bluetooth_manager.txt').write_text(bt.stdout, encoding='utf-8', errors='ignore')

    # CASE1
    log('CASE1 start')
    xml_top_before = goto_top(); screenshot('case1_before_top')
    xml_controls_before = goto_device_controls(); screenshot('case1_before_controls')
    summary['case1_before'] = extract_status(xml_top_before)
    export_db_snapshot('before_case1'); db1_before = query_db('before_case1')
    connect_node = find_node(xml_controls_before, resource_id='com.example.newstart:id/btn_connect')
    if connect_node:
        tap_node(connect_node)
        time.sleep(0.4)
    xml_connecting = dump_ui('case1_connecting'); screenshot('case1_connecting')
    summary['case1_connecting'] = extract_status(xml_connecting)
    time.sleep(2.3)
    xml_connected = dump_ui('case1_connected'); screenshot('case1_connected')
    summary['case1_connected'] = extract_status(xml_connected)
    export_db_snapshot('after_case1'); db1_after = query_db('after_case1')
    goto_today_page('case1_today_after_connect')

    # CASE2
    log('CASE2 start')
    ensure_app_device_page(); goto_device_controls()
    adb(['shell', 'cmd', 'bluetooth_manager', 'disable'], timeout=60); time.sleep(2)
    xml_bt_off = dump_ui('case2_controls_bt_off'); screenshot('case2_controls_bt_off')
    scan_node = find_node(xml_bt_off, resource_id='com.example.newstart:id/btn_scan')
    if scan_node:
        tap_node(scan_node)
        time.sleep(2)
    xml_after_scan = dump_ui('case2_after_scan_bt_off'); screenshot('case2_after_scan_bt_off')
    rootnode = next(parse_nodes(xml_after_scan), None)
    summary['case2'] = {
        'package_after_scan': rootnode.get('package') if rootnode else None,
        'status': extract_status(xml_after_scan)
    }
    adb(['shell', 'cmd', 'bluetooth_manager', 'enable'], timeout=60); time.sleep(3)

    # CASE3
    log('CASE3 start')
    ensure_app_device_page()
    if extract_status(goto_top()).get('connection_status') != '已连接':
        controls = goto_device_controls()
        node = find_node(controls, resource_id='com.example.newstart:id/btn_connect')
        if node:
            tap_node(node); time.sleep(2.3)
    xml_c3_before = dump_ui('case3_connected_before_disconnect'); screenshot('case3_connected_before_disconnect')
    controls = goto_device_controls(); dnode = find_node(controls, resource_id='com.example.newstart:id/btn_disconnect')
    if dnode:
        tap_node(dnode); time.sleep(0.8)
    xml_c3_disc = dump_ui('case3_disconnected'); screenshot('case3_disconnected')
    controls = goto_device_controls(); cnode = find_node(controls, resource_id='com.example.newstart:id/btn_connect')
    if cnode:
        tap_node(cnode); time.sleep(2.3)
    xml_c3_recovery = dump_ui('case3_recovered'); screenshot('case3_recovered')
    export_db_snapshot('after_case3'); db3 = query_db('after_case3')
    goto_today_page('case3_today_after_recovery')
    summary['case3'] = {
        'before_disconnect': extract_status(xml_c3_before),
        'after_disconnect': extract_status(xml_c3_disc),
        'after_recovery': extract_status(xml_c3_recovery)
    }

    stop_recording()
    cp = adb(['logcat', '-d', '-v', 'time'], timeout=60)
    full_log = cp.stdout
    (ROOT / 'all-logcat.txt').write_text(full_log, encoding='utf-8', errors='ignore')
    pats = ['DeviceViewModel', 'MockBleManager', 'BluetoothGatt', 'BtGatt', 'Ble', 'DataCollectionService', 'BluetoothManagerService', 'GattService']
    filtered = [line for line in full_log.splitlines() if any(p.lower() in line.lower() for p in pats)]
    (ROOT / 'logcat.txt').write_text('\n'.join(filtered) + '\n', encoding='utf-8', errors='ignore')
    ble_summary = []
    for line in bt.stdout.splitlines():
        if 'ACL Connection successful' in line or 'ACL Disconnected' in line or 'ACL Allow connection' in line:
            ble_summary.append(line.strip())
    (DB / 'bluetooth_history_summary.txt').write_text('\n'.join(ble_summary) + '\n', encoding='utf-8')
    summary['db_before_case1'] = db1_before
    summary['db_after_case1'] = db1_after
    summary['db_after_case3'] = db3
    summary['system_ble_history'] = ble_summary
    (ROOT / 'case-summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
    log('SUCCESS')
except Exception as e:
    log(f'ERROR {e!r}')
    (ROOT / 'case-summary.json').write_text(json.dumps({'error': repr(e), 'partial': summary}, ensure_ascii=False, indent=2), encoding='utf-8')
    raise
