#!/usr/bin/env python3
"""Build a layered cartoon-doctor GLB for 3D intervention stage.

Run with Blender:
  blender --background --python tools/build_layered_cartoon_model.py -- \
    --anatomy app/src/main/assets/3d/human_body.glb \
    --shell app/src/main/assets/3d/human_body_cartoon.glb \
    --output app/src/main/assets/3d/human_body_layered_cartoon.glb
"""

from __future__ import annotations

import argparse
import sys
from collections import Counter

import bpy
from mathutils import Vector


LAYER_HEAD_NECK = "layer_head_neck"
LAYER_THORAX = "layer_thorax"
LAYER_ABDOMEN = "layer_abdomen"
LAYER_PELVIS = "layer_pelvis"
LAYER_LIMBS = "layer_limbs"
LAYER_SHELL = "layer_shell"

LAYER_CODES = (
    LAYER_HEAD_NECK,
    LAYER_THORAX,
    LAYER_ABDOMEN,
    LAYER_PELVIS,
    LAYER_LIMBS,
)

LAYER_MATERIAL = {
    LAYER_HEAD_NECK: ((0.60, 0.89, 0.98, 0.72), 0.38),
    LAYER_THORAX: ((0.46, 0.80, 0.96, 0.70), 0.36),
    LAYER_ABDOMEN: ((0.47, 0.92, 0.83, 0.68), 0.34),
    LAYER_PELVIS: ((0.58, 0.80, 0.97, 0.66), 0.32),
    LAYER_LIMBS: ((0.72, 0.86, 0.98, 0.62), 0.30),
    LAYER_SHELL: ((0.97, 0.98, 1.00, 0.42), 0.58),
}

HEAD_KEYWORDS = (
    "frontal",
    "parietal",
    "occipital",
    "mandible",
    "maxilla",
    "sphenoid",
    "ethmoid",
    "hyoid",
    "head",
    "face",
    "neck",
    "atlas",
    "axis",
    "cervical",
)

THORAX_KEYWORDS = (
    "sternum",
    "rib",
    "clavicle",
    "scapula",
    "thoracic",
    "costal",
    "xiphoid",
    "manubrium",
    "chest",
)

ABDOMEN_KEYWORDS = (
    "lumbar",
    "abdomen",
    "abdominal",
)

PELVIS_KEYWORDS = (
    "pelvis",
    "hip",
    "sacrum",
    "coccyx",
)

LIMB_KEYWORDS = (
    "humerus",
    "radius",
    "ulna",
    "carpal",
    "metacarpal",
    "phalan",
    "femur",
    "tibia",
    "fibula",
    "patella",
    "tarsal",
    "metatarsal",
    "limb",
    "arm",
    "leg",
    "foot",
    "hand",
)

FORBIDDEN_KEYWORDS = ("icosphere", "ground", "camera", "light", "helper", "bucket", "chair", "table")


def parse_args() -> argparse.Namespace:
    argv = sys.argv
    if "--" in argv:
        argv = argv[argv.index("--") + 1 :]
    else:
        argv = []
    parser = argparse.ArgumentParser()
    parser.add_argument("--anatomy", required=True, help="source anatomy GLB")
    parser.add_argument("--shell", required=True, help="source cartoon shell GLB")
    parser.add_argument("--output", required=True, help="output layered GLB path")
    parser.add_argument("--shell-scale", type=float, default=1.03)
    parser.add_argument("--anatomy-decimate", type=float, default=0.26)
    return parser.parse_args(argv)


def clear_scene() -> None:
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete(use_global=False)


def import_glb(path: str):
    before = set(bpy.data.objects)
    bpy.ops.import_scene.gltf(filepath=path)
    after = set(bpy.data.objects)
    return list(after - before)


def mesh_objects(objs):
    return [o for o in objs if o.type == "MESH"]


def object_bounds_world(obj) -> tuple[Vector, Vector]:
    corners = [obj.matrix_world @ Vector(c) for c in obj.bound_box]
    mins = Vector((min(c.x for c in corners), min(c.y for c in corners), min(c.z for c in corners)))
    maxs = Vector((max(c.x for c in corners), max(c.y for c in corners), max(c.z for c in corners)))
    return mins, maxs


def collection_bounds(objs) -> tuple[Vector, Vector]:
    mins = Vector((1e9, 1e9, 1e9))
    maxs = Vector((-1e9, -1e9, -1e9))
    for obj in objs:
        if obj.type != "MESH":
            continue
        lo, hi = object_bounds_world(obj)
        mins.x, mins.y, mins.z = min(mins.x, lo.x), min(mins.y, lo.y), min(mins.z, lo.z)
        maxs.x, maxs.y, maxs.z = max(maxs.x, hi.x), max(maxs.y, hi.y), max(maxs.z, hi.z)
    return mins, maxs


def ensure_material(name: str, base_rgba: tuple[float, float, float, float], roughness: float):
    mat = bpy.data.materials.get(name)
    if mat is None:
        mat = bpy.data.materials.new(name=name)
    mat.use_nodes = True
    if hasattr(mat, "blend_method"):
        mat.blend_method = "BLEND"
    if hasattr(mat, "shadow_method"):
        mat.shadow_method = "HASHED"
    nt = mat.node_tree
    bsdf = nt.nodes.get("Principled BSDF")
    if bsdf is not None:
        bsdf.inputs["Base Color"].default_value = base_rgba
        bsdf.inputs["Alpha"].default_value = base_rgba[3]
        bsdf.inputs["Roughness"].default_value = roughness
        bsdf.inputs["Metallic"].default_value = 0.02
        if "Emission Color" in bsdf.inputs:
            bsdf.inputs["Emission Color"].default_value = (
                min(base_rgba[0] * 0.45, 1.0),
                min(base_rgba[1] * 0.45, 1.0),
                min(base_rgba[2] * 0.45, 1.0),
                1.0,
            )
        if "Emission Strength" in bsdf.inputs:
            bsdf.inputs["Emission Strength"].default_value = 0.12
    return mat


def classify_by_name(lower_name: str) -> str | None:
    if any(k in lower_name for k in HEAD_KEYWORDS):
        return LAYER_HEAD_NECK
    if any(k in lower_name for k in THORAX_KEYWORDS):
        return LAYER_THORAX
    if any(k in lower_name for k in ABDOMEN_KEYWORDS):
        return LAYER_ABDOMEN
    if any(k in lower_name for k in PELVIS_KEYWORDS):
        return LAYER_PELVIS
    if any(k in lower_name for k in LIMB_KEYWORDS):
        return LAYER_LIMBS
    return None


def classify_by_bounds(obj, global_min: Vector, global_max: Vector) -> str:
    lo, hi = object_bounds_world(obj)
    center = (lo + hi) * 0.5
    size_z = max(global_max.z - global_min.z, 1e-6)
    size_x = max(global_max.x - global_min.x, 1e-6)
    z_ratio = (center.z - global_min.z) / size_z
    x_ratio = abs(center.x) / size_x

    if x_ratio > 0.22 or z_ratio < 0.30:
        return LAYER_LIMBS
    if z_ratio >= 0.78:
        return LAYER_HEAD_NECK
    if z_ratio >= 0.56:
        return LAYER_THORAX
    if z_ratio >= 0.40:
        return LAYER_ABDOMEN
    return LAYER_PELVIS


def apply_decimate(obj, ratio: float) -> None:
    if ratio >= 0.99:
        return
    mod = obj.modifiers.new(name="DecimateLite", type="DECIMATE")
    mod.ratio = max(0.05, min(ratio, 1.0))
    mod.use_collapse_triangulate = True
    bpy.context.view_layer.objects.active = obj
    try:
        bpy.ops.object.modifier_apply(modifier=mod.name)
    except RuntimeError:
        pass


def align_shell(shell_objs, target_objs, scale_bias: float) -> None:
    target_min, target_max = collection_bounds(target_objs)
    shell_min, shell_max = collection_bounds(shell_objs)
    target_size = target_max - target_min
    shell_size = shell_max - shell_min

    tmax = max(target_size.x, target_size.y, target_size.z)
    smax = max(shell_size.x, shell_size.y, shell_size.z)
    if smax <= 1e-6:
        return
    scale_factor = (tmax / smax) * scale_bias

    shell_roots = [o for o in shell_objs if o.parent not in shell_objs]
    if not shell_roots:
        shell_roots = shell_objs
    for obj in shell_roots:
        obj.scale = (obj.scale.x * scale_factor, obj.scale.y * scale_factor, obj.scale.z * scale_factor)

    bpy.context.view_layer.update()
    shell_min2, shell_max2 = collection_bounds(shell_objs)
    target_center = (target_min + target_max) * 0.5
    shell_center = (shell_min2 + shell_max2) * 0.5
    delta = target_center - shell_center
    for obj in shell_roots:
        obj.location += delta


def keep_useful_shell(shell_meshes):
    filtered = []
    for obj in shell_meshes:
        lower = obj.name.lower()
        if any(k in lower for k in FORBIDDEN_KEYWORDS):
            bpy.data.objects.remove(obj, do_unlink=True)
            continue
        filtered.append(obj)
    if not filtered:
        return []
    # Keep the densest shell object only; avoids background/helper mesh.
    best = max(filtered, key=lambda o: len(o.data.vertices))
    for obj in filtered:
        if obj != best:
            bpy.data.objects.remove(obj, do_unlink=True)
    return [best]


def main() -> int:
    args = parse_args()
    clear_scene()

    anatomy_imported = import_glb(args.anatomy)
    anatomy_meshes = mesh_objects(anatomy_imported)
    if not anatomy_meshes:
        raise RuntimeError("No mesh objects imported from anatomy asset")

    # Remove legacy outer skin mesh from anatomy asset to avoid style conflict.
    filtered_anatomy = []
    for obj in anatomy_meshes:
        lower = obj.name.lower()
        if "cesium_man" in lower:
            bpy.data.objects.remove(obj, do_unlink=True)
            continue
        if any(k in lower for k in FORBIDDEN_KEYWORDS):
            bpy.data.objects.remove(obj, do_unlink=True)
            continue
        filtered_anatomy.append(obj)
    anatomy_meshes = filtered_anatomy

    global_min, global_max = collection_bounds(anatomy_meshes)
    layer_counter = Counter()

    for obj in anatomy_meshes:
        lower = obj.name.lower()
        layer = classify_by_name(lower)
        if layer is None:
            layer = classify_by_bounds(obj, global_min, global_max)
        layer_counter[layer] += 1
        base_name = obj.name.strip()
        obj.name = f"{layer}_{base_name}"
        if obj.data is not None:
            obj.data.name = obj.name
        apply_decimate(obj, args.anatomy_decimate)

    shell_imported = import_glb(args.shell)
    shell_meshes = keep_useful_shell(mesh_objects(shell_imported))
    align_shell(shell_meshes, anatomy_meshes, args.shell_scale)

    if shell_meshes:
        shell = shell_meshes[0]
        shell.name = f"{LAYER_SHELL}_main"
        if shell.data is not None:
            shell.data.name = shell.name
        shell_mat = ensure_material("LayerMat_shell", *LAYER_MATERIAL[LAYER_SHELL])
        shell.data.materials.clear()
        shell.data.materials.append(shell_mat)

    for layer_code in LAYER_CODES:
        mat = ensure_material(f"LayerMat_{layer_code}", *LAYER_MATERIAL[layer_code])
        for obj in anatomy_meshes:
            if not obj.name.lower().startswith(layer_code):
                continue
            obj.data.materials.clear()
            obj.data.materials.append(mat)

    bpy.ops.export_scene.gltf(
        filepath=args.output,
        export_format="GLB",
        use_selection=False,
        export_animations=False,
        export_apply=True,
        export_morph=False,
        export_draco_mesh_compression_enable=True,
        export_draco_mesh_compression_level=6,
        export_draco_position_quantization=14,
        export_draco_normal_quantization=10,
        export_draco_texcoord_quantization=12,
        export_draco_color_quantization=10,
        export_draco_generic_quantization=12,
    )

    print(f"output={args.output}")
    print(f"anatomy_meshes={len(anatomy_meshes)}")
    print(f"shell_meshes={len(shell_meshes)}")
    print(f"layer_distribution={dict(layer_counter)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
