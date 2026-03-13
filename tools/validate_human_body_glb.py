#!/usr/bin/env python3
"""Validate the 3D human model GLB structure for interaction readiness."""

from __future__ import annotations

import argparse
import json
import struct
from pathlib import Path


def load_glb_json(path: Path) -> dict:
    data = path.read_bytes()
    if data[:4] != b"glTF":
        raise ValueError("not a valid GLB file")

    total_len = struct.unpack_from("<I", data, 8)[0]
    offset = 12
    while offset < total_len:
        chunk_len, chunk_type = struct.unpack_from("<II", data, offset)
        offset += 8
        chunk = data[offset : offset + chunk_len]
        offset += chunk_len
        if chunk_type == 0x4E4F534A:  # JSON
            return json.loads(chunk.decode("utf-8"))

    raise ValueError("GLB JSON chunk not found")


def detect_side_markers(node_names: list[str]) -> tuple[bool, bool]:
    has_left = any(".l" in n.lower() or " left" in n.lower() or "_l" in n.lower() for n in node_names)
    has_right = any(".r" in n.lower() or " right" in n.lower() or "_r" in n.lower() for n in node_names)
    return has_left, has_right


def detect_limb_bilateral(node_names: list[str]) -> tuple[bool, bool, bool, bool]:
    lower = [n.lower() for n in node_names]
    left_arm = any("humerus.l" in n or "radius.l" in n or "ulna.l" in n for n in lower)
    right_arm = any("humerus.r" in n or "radius.r" in n or "ulna.r" in n for n in lower)
    left_leg = any("femur.l" in n or "tibia.l" in n or "fibula.l" in n for n in lower)
    right_leg = any("femur.r" in n or "tibia.r" in n or "fibula.r" in n for n in lower)
    return left_arm, right_arm, left_leg, right_leg


def detect_polluted_names(names: list[str], forbidden_keywords: list[str]) -> list[str]:
    polluted = []
    lower = [n.lower() for n in names]
    for keyword in forbidden_keywords:
        if any(keyword in name for name in lower):
            polluted.append(keyword)
    return sorted(set(polluted))


def detect_required_tokens(
    names: list[str],
    required_tokens: list[str],
) -> tuple[list[str], list[str]]:
    if not required_tokens:
        return [], sorted(set(names))

    lower_names = [n.lower() for n in names if n]
    hit_names = set()
    missing_tokens = []
    for token in required_tokens:
        token_lower = token.lower()
        matched = [n for n in lower_names if token_lower in n]
        if matched:
            hit_names.update(matched)
        else:
            missing_tokens.append(token)
    return missing_tokens, sorted(hit_names)


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate human_body.glb")
    parser.add_argument("path", type=Path, nargs="?", default=Path("app/src/main/assets/3d/human_body.glb"))
    parser.add_argument("--min-meshes", type=int, default=100)
    parser.add_argument("--require-bilateral", action="store_true")
    parser.add_argument(
        "--forbidden-keywords",
        type=str,
        default="icosphere,ground,camera",
        help="comma-separated node name keywords that should not exist",
    )
    parser.add_argument(
        "--required-layer-tokens",
        type=str,
        default="",
        help="comma-separated layer tokens that must appear in node or mesh names",
    )
    args = parser.parse_args()

    gltf = load_glb_json(args.path)
    meshes = gltf.get("meshes", [])
    nodes = gltf.get("nodes", [])
    animations = gltf.get("animations", [])
    node_names = [n.get("name", "") for n in nodes if n.get("name")]
    mesh_names = [m.get("name", "") for m in meshes if m.get("name")]
    all_names = node_names + mesh_names
    has_left, has_right = detect_side_markers(node_names)
    left_arm, right_arm, left_leg, right_leg = detect_limb_bilateral(node_names)
    forbidden_keywords = [k.strip().lower() for k in args.forbidden_keywords.split(",") if k.strip()]
    required_layer_tokens = [k.strip().lower() for k in args.required_layer_tokens.split(",") if k.strip()]
    polluted_keywords = detect_polluted_names(all_names, forbidden_keywords)
    missing_tokens, hit_layer_names = detect_required_tokens(all_names, required_layer_tokens)

    print(f"file={args.path}")
    print(f"meshes={len(meshes)} nodes={len(nodes)} animations={len(animations)}")
    print(f"side_markers: left={has_left} right={has_right}")
    print(
        "limb_markers: "
        f"left_arm={left_arm} right_arm={right_arm} left_leg={left_leg} right_leg={right_leg}"
    )
    print(f"forbidden_keywords={forbidden_keywords}")
    if required_layer_tokens:
        print(f"required_layer_tokens={required_layer_tokens}")

    failed = False
    if len(meshes) < args.min_meshes:
        print(f"[FAIL] meshes < {args.min_meshes}")
        failed = True
    else:
        print(f"[OK] meshes >= {args.min_meshes}")

    if len(animations) > 0:
        print("[WARN] model contains animations; ensure autoPlayAnimation=false")

    if args.require_bilateral and not (has_left and has_right and left_arm and right_arm and left_leg and right_leg):
        print("[FAIL] bilateral markers are incomplete for side-sensitive limb zones")
        failed = True
    elif has_left and has_right:
        print("[OK] bilateral markers detected")
    else:
        print("[WARN] bilateral markers incomplete; rely on side-fallback mapping")

    if not (left_arm and right_arm and left_leg and right_leg):
        print("[WARN] limb bilateral markers incomplete; Blender mirror workflow is recommended")
    else:
        print("[OK] limb bilateral markers detected")

    if polluted_keywords:
        print(f"[FAIL] polluted helper nodes found: {', '.join(polluted_keywords)}")
        failed = True
    else:
        print("[OK] no forbidden helper nodes detected")

    if required_layer_tokens:
        if missing_tokens:
            print(f"[FAIL] missing required layer tokens: {', '.join(missing_tokens)}")
            failed = True
        else:
            print("[OK] all required layer tokens detected")
        if hit_layer_names:
            preview = ", ".join(hit_layer_names[:6])
            suffix = "..." if len(hit_layer_names) > 6 else ""
            print(f"layer_token_hits={preview}{suffix}")

    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
