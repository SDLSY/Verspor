#!/usr/bin/env python3
"""Mirror right-side mesh objects in a GLB to generate left-side counterparts using Blender."""

import argparse
import re
import sys

import bpy
import bmesh


RIGHT_PATTERNS = [
    re.compile(r"\.r(\b|\.)", re.IGNORECASE),
    re.compile(r"\bright\b", re.IGNORECASE),
    re.compile(r"_r(\b|_)", re.IGNORECASE),
]


def is_right_name(name: str) -> bool:
    lower = name.lower()
    return any(p.search(lower) for p in RIGHT_PATTERNS)


def to_left_name(name: str) -> str:
    out = re.sub(r"\.r(\b|\.)", lambda m: ".l" + (m.group(1) if m.group(1) == "." else ""), name, flags=re.IGNORECASE)
    out = re.sub(r"\bright\b", "left", out, flags=re.IGNORECASE)
    out = re.sub(r"_r(\b|_)", lambda m: "_l" + (m.group(1) if m.group(1) == "_" else ""), out, flags=re.IGNORECASE)
    return out


def clear_scene() -> None:
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete(use_global=False)


def recalc_normals(mesh_obj: bpy.types.Object) -> None:
    mesh = mesh_obj.data
    bm = bmesh.new()
    bm.from_mesh(mesh)
    if bm.faces:
        bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(mesh)
    bm.free()
    mesh.update()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    argv = sys.argv
    if "--" in argv:
        argv = argv[argv.index("--") + 1 :]
    else:
        argv = []
    args = parser.parse_args(argv)

    clear_scene()

    bpy.ops.import_scene.gltf(filepath=args.input)

    scene = bpy.context.scene
    mesh_objs = [obj for obj in scene.objects if obj.type == "MESH"]
    right_meshes = [obj for obj in mesh_objs if is_right_name(obj.name)]

    mirrored = []
    for obj in right_meshes:
        dup = obj.copy()
        dup.data = obj.data.copy()
        dup.name = to_left_name(obj.name)
        if obj.parent is not None:
            dup.parent = obj.parent
            dup.matrix_parent_inverse = obj.matrix_parent_inverse.copy()
        scene.collection.objects.link(dup)

        dup.scale.x *= -1.0
        mirrored.append(dup)

    if mirrored:
        bpy.ops.object.select_all(action="DESELECT")
        for obj in mirrored:
            obj.select_set(True)
        bpy.context.view_layer.objects.active = mirrored[0]
        bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)

        for obj in mirrored:
            recalc_normals(obj)

    # Keep static model for stage rendering.
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

    print(f"mirrored_right_meshes={len(right_meshes)}")
    print(f"created_left_meshes={len(mirrored)}")
    print(f"output={args.output}")


if __name__ == "__main__":
    main()
