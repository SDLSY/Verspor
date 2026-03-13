#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import mimetypes
import os
import sys
import uuid
from dataclasses import dataclass
from pathlib import Path
from typing import Any

try:
    import requests
except ImportError as exc:  # pragma: no cover
    raise SystemExit("需要先安装 requests: pip install requests") from exc


BASE_URL = "https://sparkcons-rag.cn-huabei-1.xf-yun.com/aiuiKnowledge"
DEFAULT_REPO_NAME = "VesperO数字人医生知识库"
DEFAULT_REPO_DESCRIPTION = "VesperO 数字人医生知识库，覆盖睡眠恢复、症状初筛、指标解释、医检说明与产品导航。"
DEFAULT_THRESHOLD = "0.35"
DEFAULT_DOCS_DIR = Path("tools") / "xfyun_km" / "knowledge"
DEFAULT_STATE_FILE = Path("tmp") / "xfyun_km_state.json"


def load_properties(path: Path) -> dict[str, str]:
    if not path.exists():
        return {}
    result: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        result[key.strip()] = value.strip()
    return result


def pick(props: dict[str, str], *keys: str, default: str = "") -> str:
    for key in keys:
        value = os.environ.get(key) or props.get(key, "")
        if value:
            return value
    return default


@dataclass
class RagConfig:
    uid: str
    api_password: str
    app_id: str
    scene_name: str

    @property
    def headers(self) -> dict[str, str]:
        return {"Authorization": f"Bearer {self.api_password}"}

    @classmethod
    def from_properties(cls, props: dict[str, str]) -> "RagConfig":
        config = cls(
            uid=pick(props, "XFYUN_RAG_UID", "XFYUN_UID"),
            api_password=pick(props, "XFYUN_RAG_API_PASSWORD", "XFYUN_API_PASSWORD"),
            app_id=pick(props, "XFYUN_AIUI_APP_ID", "XFYUN_APP_ID"),
            scene_name=pick(props, "XFYUN_AIUI_SCENE", default="sos_app"),
        )
        missing = []
        if not config.uid:
            missing.append("XFYUN_RAG_UID 或 XFYUN_UID")
        if not config.api_password:
            missing.append("XFYUN_RAG_API_PASSWORD 或 XFYUN_API_PASSWORD")
        if not config.app_id:
            missing.append("XFYUN_AIUI_APP_ID 或 XFYUN_APP_ID")
        if not config.scene_name:
            missing.append("XFYUN_AIUI_SCENE")
        if missing:
            raise SystemExit("缺少知识库配置: " + "、".join(missing))
        return config


def request_json(
    session: requests.Session,
    method: str,
    path: str,
    *,
    headers: dict[str, str],
    json_body: dict[str, Any] | None = None,
    data: dict[str, Any] | None = None,
    files: dict[str, Any] | None = None,
    timeout: int = 60,
) -> dict[str, Any]:
    url = f"{BASE_URL}/{path.lstrip('/')}"
    response = session.request(
        method=method,
        url=url,
        headers=headers,
        json=json_body,
        data=data,
        files=files,
        timeout=timeout,
    )
    response.raise_for_status()
    payload = response.json()
    code = str(payload.get("code", ""))
    if code not in {"0", ""}:
        raise RuntimeError(f"{path} 失败: code={code} desc={payload.get('desc')}")
    return payload


def create_repo(
    session: requests.Session,
    config: RagConfig,
    repo_name: str,
    repo_description: str,
) -> dict[str, Any]:
    sid = str(uuid.uuid4())
    payload = {
        "uid": int(config.uid),
        "name": repo_name,
        "description": repo_description,
        "sid": sid,
        "channel": "vespero",
    }
    response = request_json(
        session,
        "POST",
        "rag/api/repo/create",
        headers={**config.headers, "Content-Type": "application/json"},
        json_body=payload,
    )
    data = response.get("data") or {}
    return {
        "repo_name": data.get("name", repo_name),
        "repo_db_id": data.get("id"),
        "group_id": data.get("groupId"),
        "sid": response.get("sid", sid),
    }


def upload_file(
    session: requests.Session,
    config: RagConfig,
    group_id: str,
    file_path: Path,
) -> dict[str, Any]:
    sid = str(uuid.uuid4())
    mime_type = mimetypes.guess_type(file_path.name)[0] or "text/plain"
    with file_path.open("rb") as handle:
        response = request_json(
            session,
            "POST",
            "rag/api/doc/saveRepoDoc",
            headers=config.headers,
            data={
                "uid": config.uid,
                "groupId": group_id,
                "sid": sid,
            },
            files={"file": (file_path.name, handle, mime_type)},
            timeout=180,
        )
    return response


def bind_repo(
    session: requests.Session,
    config: RagConfig,
    repo_name: str,
    group_id: str,
    threshold: str,
) -> dict[str, Any]:
    sid = str(uuid.uuid4())
    payload = {
        "uid": int(config.uid),
        "appId": config.app_id,
        "sceneName": config.scene_name,
        "repos": [
            {
                "repoId": group_id,
                "repoName": repo_name,
                "groupId": group_id,
                "threshold": threshold,
            }
        ],
        "sid": sid,
    }
    return request_json(
        session,
        "POST",
        "rag/api/app/saveRepoConfig",
        headers={**config.headers, "Content-Type": "application/json"},
        json_body=payload,
    )


def write_state(state_path: Path, state: dict[str, Any]) -> None:
    state_path.parent.mkdir(parents=True, exist_ok=True)
    state_path.write_text(
        json.dumps(state, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


def collect_docs(docs_dir: Path) -> list[Path]:
    if not docs_dir.exists():
        raise SystemExit(f"知识库素材目录不存在: {docs_dir}")
    docs = sorted(path for path in docs_dir.rglob("*.txt") if path.is_file())
    if not docs:
        raise SystemExit(f"知识库素材目录内没有 .txt 文件: {docs_dir}")
    return docs


def bootstrap(args: argparse.Namespace) -> int:
    props = load_properties(Path(args.local_properties))
    config = RagConfig.from_properties(props)
    docs_dir = Path(args.docs_dir)
    docs = collect_docs(docs_dir)

    if args.dry_run:
        plan = {
            "repoName": args.repo_name,
            "repoDescription": args.repo_description,
            "appId": config.app_id,
            "sceneName": config.scene_name,
            "docs": [str(doc) for doc in docs],
            "threshold": args.threshold,
        }
        print(json.dumps(plan, ensure_ascii=False, indent=2))
        return 0

    session = requests.Session()
    repo_info = create_repo(session, config, args.repo_name, args.repo_description)

    uploads: list[dict[str, Any]] = []
    for doc in docs:
        response = upload_file(session, config, repo_info["group_id"], doc)
        uploads.append(
            {
                "file": doc.name,
                "response": response.get("data"),
            }
        )

    bind_response = bind_repo(
        session,
        config,
        repo_info["repo_name"],
        repo_info["group_id"],
        args.threshold,
    )

    summary = {
        "repo": repo_info,
        "uploads": uploads,
        "bind": bind_response,
        "appId": config.app_id,
        "sceneName": config.scene_name,
    }
    write_state(Path(args.state_file), summary)
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="创建讯飞超拟人交互知识库、上传素材并绑定到 appId + sceneName。"
    )
    parser.add_argument(
        "command",
        choices=["bootstrap"],
        help="当前只支持一键建库、上传、绑定。",
    )
    parser.add_argument(
        "--local-properties",
        default="local.properties",
        help="默认读取仓库根目录 local.properties。",
    )
    parser.add_argument(
        "--docs-dir",
        default=str(DEFAULT_DOCS_DIR),
        help="知识库素材目录，默认 tools/xfyun_km/knowledge。",
    )
    parser.add_argument(
        "--repo-name",
        default=DEFAULT_REPO_NAME,
        help="远端知识库名称。",
    )
    parser.add_argument(
        "--repo-description",
        default=DEFAULT_REPO_DESCRIPTION,
        help="远端知识库描述。",
    )
    parser.add_argument(
        "--threshold",
        default=DEFAULT_THRESHOLD,
        help="绑定到应用时的检索阈值字符串，默认 0.35。",
    )
    parser.add_argument(
        "--state-file",
        default=str(DEFAULT_STATE_FILE),
        help="执行结果输出到本地 JSON 文件，默认 tmp/xfyun_km_state.json。",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="只校验配置和素材，不调用远端接口。",
    )
    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    if args.command == "bootstrap":
        return bootstrap(args)
    parser.error(f"未知命令: {args.command}")
    return 2


if __name__ == "__main__":
    sys.exit(main())
