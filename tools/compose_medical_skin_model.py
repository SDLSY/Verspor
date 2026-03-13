#!/usr/bin/env python3
"""Compose a medical-style GLB with bilateral skeleton + translucent skin shell."""

import argparse
from mathutils import Vector
import bpy


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

def pick_primary_skin_meshes(skin_meshes):
    """
    Keep only the real body shell mesh from legacy skin assets.
    Some sources include helper geometry (e.g. Icosphere) that breaks bounding size.
    """
    if not skin_meshes:
        return []

    named = [m for m in skin_meshes if "cesium" in m.name.lower() or "body" in m.name.lower()]
    if named:
        return named

    # Fallback: keep the densest mesh as body shell.
    return [max(skin_meshes, key=lambda m: len(m.data.vertices))]


def world_bounds(objs):
    mins = Vector((1e9, 1e9, 1e9))
    maxs = Vector((-1e9, -1e9, -1e9))
    has = False
    for obj in objs:
        for corner in obj.bound_box:
            p = obj.matrix_world @ Vector(corner)
            mins.x = min(mins.x, p.x)
            mins.y = min(mins.y, p.y)
            mins.z = min(mins.z, p.z)
            maxs.x = max(maxs.x, p.x)
            maxs.y = max(maxs.y, p.y)
            maxs.z = max(maxs.z, p.z)
            has = True
    if not has:
        return Vector((0, 0, 0)), Vector((0, 0, 0))
    return mins, maxs


def size_and_center(objs):
    mins, maxs = world_bounds(objs)
    size = maxs - mins
    center = (mins + maxs) * 0.5
    return size, center


def apply_translucent_skin_material(skin_meshes):
    mat = bpy.data.materials.get("Skin_Translucent")
    if mat is None:
        mat = bpy.data.materials.new(name="Skin_Translucent")
    mat.use_nodes = True
    if hasattr(mat, "blend_method"):
        mat.blend_method = "BLEND"
    if hasattr(mat, "shadow_method"):
        mat.shadow_method = "HASHED"

    nt = mat.node_tree
    bsdf = nt.nodes.get("Principled BSDF")
    if bsdf is not None:
        bsdf.inputs["Base Color"].default_value = (0.93, 0.79, 0.73, 0.26)
        bsdf.inputs["Alpha"].default_value = 0.26
        bsdf.inputs["Roughness"].default_value = 0.58
        bsdf.inputs["Metallic"].default_value = 0.0
        if "Subsurface Weight" in bsdf.inputs:
            bsdf.inputs["Subsurface Weight"].default_value = 0.08

    for obj in skin_meshes:
        obj.data.materials.clear()
        obj.data.materials.append(mat)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--skeleton", required=True)
    parser.add_argument("--skin", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--skin-scale", type=float, default=1.02)
    args = parser.parse_args(__import__('sys').argv[__import__('sys').argv.index('--') + 1:] if '--' in __import__('sys').argv else [])

    clear_scene()

    skeleton_objs = import_glb(args.skeleton)
    skeleton_meshes = mesh_objects(skeleton_objs)

    skin_objs = import_glb(args.skin)
    skin_meshes = mesh_objects(skin_objs)
    selected_skin_meshes = pick_primary_skin_meshes(skin_meshes)
    selected_set = set(selected_skin_meshes)
    for obj in skin_meshes:
        if obj not in selected_set:
            bpy.data.objects.remove(obj, do_unlink=True)
    skin_meshes = selected_skin_meshes

    skeleton_size, skeleton_center = size_and_center(skeleton_meshes)
    skin_size, skin_center = size_and_center(skin_meshes)

    skeleton_max = max(skeleton_size.x, skeleton_size.y, skeleton_size.z)
    skin_max = max(skin_size.x, skin_size.y, skin_size.z) if max(skin_size.x, skin_size.y, skin_size.z) > 0 else 1.0
    scale_factor = (skeleton_max / skin_max) * args.skin_scale

    skin_roots = [o for o in skin_meshes if o.parent not in skin_meshes]
    if not skin_roots:
        skin_roots = skin_meshes

    for root in skin_roots:
        root.scale = (root.scale.x * scale_factor, root.scale.y * scale_factor, root.scale.z * scale_factor)

    bpy.context.view_layer.update()

    _, skin_center_after_scale = size_and_center(skin_meshes)
    delta = skeleton_center - skin_center_after_scale
    for root in skin_roots:
        root.location += delta

    bpy.context.view_layer.update()

    apply_translucent_skin_material(skin_meshes)

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

    print(f"skeleton_meshes={len(skeleton_meshes)}")
    print(f"skin_meshes={len(skin_meshes)}")
    print(f"skin_scale_factor={scale_factor:.4f}")
    print(f"output={args.output}")


if __name__ == "__main__":
    main()
