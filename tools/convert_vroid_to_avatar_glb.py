#!/usr/bin/env python3
"""
Convert a VRoid/VRM avatar file to a Filament-friendly GLB.

This script is intended to run inside Blender:
  "C:\\Program Files\\Blender Foundation\\Blender 4.5\\blender.exe" --background --python tools/convert_vroid_to_avatar_glb.py -- \
    --input d:/newstart/tmp/avatar_vroid_primary.vrm \
    --output d:/newstart/tmp/avatar_vroid_converted.glb
"""

from __future__ import annotations

import argparse
import logging
import sys
import tempfile
from pathlib import Path

import bpy  # type: ignore


LOGGER = logging.getLogger("convert_vroid_to_avatar_glb")

DEFAULT_BLACKLIST = [
    "background",
    "floor",
    "stage",
    "plane",
    "helper",
    "light",
    "camera",
]


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Convert VRM/GLB avatar to clean GLB.")
    parser.add_argument("--input", required=True, help="Input avatar (.vrm/.glb/.gltf)")
    parser.add_argument("--output", required=True, help="Output avatar GLB")
    parser.add_argument(
        "--remove-keywords",
        default=",".join(DEFAULT_BLACKLIST),
        help="Comma-separated object-name blacklist keywords",
    )
    return parser.parse_args(argv)


def reset_scene() -> None:
    bpy.ops.wm.read_factory_settings(use_empty=True)


def import_avatar(path: Path) -> None:
    # Blender's glTF importer works when VRM bytes are copied to a .glb path.
    if path.suffix.lower() == ".vrm":
        with tempfile.TemporaryDirectory(prefix="vroid_import_") as tmp_dir:
            tmp_glb = Path(tmp_dir) / f"{path.stem}.glb"
            tmp_glb.write_bytes(path.read_bytes())
            bpy.ops.import_scene.gltf(filepath=str(tmp_glb))
    else:
        bpy.ops.import_scene.gltf(filepath=str(path))


def remove_blacklisted_objects(keywords: list[str]) -> int:
    removed = 0
    for obj in list(bpy.data.objects):
        lower_name = obj.name.lower()
        if any(token in lower_name for token in keywords):
            bpy.data.objects.remove(obj, do_unlink=True)
            removed += 1
    return removed


def ensure_principled_materials() -> tuple[int, int]:
    converted = 0
    failures = 0
    for mat in bpy.data.materials:
        try:
            mat.use_nodes = True
            node_tree = mat.node_tree
            if node_tree is None:
                failures += 1
                continue

            nodes = node_tree.nodes
            links = node_tree.links
            output = next((n for n in nodes if n.type == "OUTPUT_MATERIAL"), None)
            if output is None:
                output = nodes.new(type="ShaderNodeOutputMaterial")
                output.location = (320, 0)

            principled = next((n for n in nodes if n.type == "BSDF_PRINCIPLED"), None)
            if principled is None:
                principled = nodes.new(type="ShaderNodeBsdfPrincipled")
                principled.location = (0, 0)

            # Preserve an existing image texture link if available.
            base_input = principled.inputs.get("Base Color")
            if base_input is not None and not base_input.is_linked:
                image_node = next((n for n in nodes if n.type == "TEX_IMAGE"), None)
                if image_node is not None:
                    links.new(image_node.outputs["Color"], base_input)

            if not output.inputs["Surface"].is_linked:
                links.new(principled.outputs["BSDF"], output.inputs["Surface"])

            # Stable PBR defaults for bright UI background.
            principled.inputs["Roughness"].default_value = 0.45
            principled.inputs["Metallic"].default_value = 0.0
            mat.blend_method = "BLEND"
            mat.shadow_method = "HASHED"
            converted += 1
        except Exception:
            failures += 1
    return converted, failures


def export_glb(path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    bpy.ops.export_scene.gltf(
        filepath=str(path),
        export_format="GLB",
        export_animations=True,
        export_skins=True,
        export_morph=True,
        export_lights=False,
        export_cameras=False,
        export_extras=False,
        export_apply=True,
        export_draco_mesh_compression_enable=False,
    )


def main(argv: list[str]) -> int:
    logging.basicConfig(level=logging.INFO, format="[%(levelname)s] %(message)s")
    args = parse_args(argv)

    in_path = Path(args.input).resolve()
    out_path = Path(args.output).resolve()
    if not in_path.exists():
        LOGGER.error("Input not found: %s", in_path)
        return 2

    keywords = [
        token.strip().lower()
        for token in args.remove_keywords.split(",")
        if token.strip()
    ]
    if not keywords:
        keywords = DEFAULT_BLACKLIST

    LOGGER.info("Input: %s", in_path)
    LOGGER.info("Output: %s", out_path)
    LOGGER.info("Blacklist: %s", ", ".join(keywords))

    reset_scene()
    import_avatar(in_path)
    removed = remove_blacklisted_objects(keywords)
    converted, failures = ensure_principled_materials()
    export_glb(out_path)

    LOGGER.info("Removed blacklisted objects: %d", removed)
    LOGGER.info("Materials normalized: %d (failed=%d)", converted, failures)
    if out_path.exists():
        LOGGER.info("Export complete, bytes=%d", out_path.stat().st_size)
    return 0


if __name__ == "__main__":
    if "--" in sys.argv:
        cli_args = sys.argv[sys.argv.index("--") + 1 :]
    else:
        cli_args = []
    raise SystemExit(main(cli_args))
