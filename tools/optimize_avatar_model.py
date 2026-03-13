#!/usr/bin/env python3
"""
Generate a lighter GLB avatar while preserving animation clips.

Usage (Windows):
  "C:\\Program Files\\Blender Foundation\\Blender 4.5\\blender.exe" --background --python tools/optimize_avatar_model.py -- \
    --input d:/newstart/app/src/main/assets/3d_avatar/guide_avatar_hq.glb \
    --output d:/newstart/app/src/main/assets/3d_avatar/guide_avatar_lite.glb \
    --decimate-ratio 0.55 --max-texture-size 1024
"""

from __future__ import annotations

import argparse
import logging
import os
import sys
from pathlib import Path

import bpy  # type: ignore


LOGGER = logging.getLogger("optimize_avatar_model")


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Optimize animated avatar GLB for lite runtime profile.")
    parser.add_argument("--input", required=True, help="Input GLB path")
    parser.add_argument("--output", required=True, help="Output GLB path")
    parser.add_argument("--decimate-ratio", type=float, default=0.55, help="Decimate ratio for meshes (0~1)")
    parser.add_argument("--max-texture-size", type=int, default=1024, help="Max texture width/height")
    parser.add_argument("--skip-decimate", action="store_true", help="Skip mesh decimation")
    return parser.parse_args(argv)


def reset_scene() -> None:
    bpy.ops.wm.read_factory_settings(use_empty=True)


def import_glb(path: str) -> None:
    bpy.ops.import_scene.gltf(filepath=path)


def remove_helper_objects() -> int:
    removed = 0
    blacklist = ("camera", "light", "helper", "ground", "icosphere")
    for obj in list(bpy.data.objects):
        name = obj.name.lower()
        if any(token in name for token in blacklist):
            bpy.data.objects.remove(obj, do_unlink=True)
            removed += 1
    return removed


def decimate_meshes(ratio: float) -> tuple[int, int]:
    touched = 0
    failed = 0
    safe_ratio = max(0.1, min(1.0, ratio))

    for obj in bpy.data.objects:
        if obj.type != "MESH":
            continue
        if len(obj.data.polygons) < 800:
            continue
        touched += 1
        modifier = obj.modifiers.new(name="LiteDecimate", type="DECIMATE")
        modifier.ratio = safe_ratio
        modifier.use_collapse_triangulate = True
        try:
            bpy.context.view_layer.objects.active = obj
            bpy.ops.object.modifier_apply(modifier=modifier.name)
        except Exception:
            failed += 1
            obj.modifiers.remove(modifier)
    return touched, failed


def clamp_texture_size(max_size: int) -> int:
    resized = 0
    safe_max = max(256, max_size)
    for image in bpy.data.images:
        if not image.has_data:
            continue
        width, height = image.size[0], image.size[1]
        if width <= safe_max and height <= safe_max:
            continue
        scale = min(safe_max / float(width), safe_max / float(height))
        new_w = max(1, int(width * scale))
        new_h = max(1, int(height * scale))
        try:
            image.scale(new_w, new_h)
            resized += 1
        except Exception:
            continue
    return resized


def export_glb(path: str) -> None:
    out = Path(path)
    out.parent.mkdir(parents=True, exist_ok=True)
    bpy.ops.export_scene.gltf(
        filepath=str(out),
        export_format="GLB",
        export_animations=True,
        export_skins=True,
        export_morph=True,
        export_lights=False,
        export_cameras=False,
        export_extras=False,
        export_apply=True,
        export_draco_mesh_compression_enable=True,
        export_draco_mesh_compression_level=6,
        export_draco_position_quantization=14,
        export_draco_normal_quantization=10,
        export_draco_texcoord_quantization=12,
        export_draco_generic_quantization=12,
    )


def main(raw_argv: list[str]) -> int:
    logging.basicConfig(level=logging.INFO, format="[%(levelname)s] %(message)s")
    args = parse_args(raw_argv)

    in_path = Path(args.input)
    out_path = Path(args.output)
    if not in_path.exists():
        LOGGER.error("Input file not found: %s", in_path)
        return 2

    LOGGER.info("Input: %s", in_path)
    LOGGER.info("Output: %s", out_path)
    LOGGER.info("Decimate ratio: %.2f", args.decimate_ratio)
    LOGGER.info("Max texture size: %d", args.max_texture_size)

    reset_scene()
    import_glb(str(in_path))

    removed = remove_helper_objects()
    LOGGER.info("Removed helper objects: %d", removed)

    resized = clamp_texture_size(args.max_texture_size)
    LOGGER.info("Resized textures: %d", resized)

    if not args.skip_decimate:
        touched, failed = decimate_meshes(args.decimate_ratio)
        LOGGER.info("Decimated meshes: %d (failed: %d)", touched, failed)

    export_glb(str(out_path))

    if out_path.exists():
        in_size = in_path.stat().st_size
        out_size = out_path.stat().st_size
        ratio = (out_size / in_size) if in_size > 0 else 0
        LOGGER.info(
            "Done. size_in=%d bytes, size_out=%d bytes (ratio=%.3f)",
            in_size,
            out_size,
            ratio,
        )
    return 0


if __name__ == "__main__":
    # Blender passes script args after '--'
    if "--" in sys.argv:
        argv = sys.argv[sys.argv.index("--") + 1 :]
    else:
        argv = []
    raise SystemExit(main(argv))
