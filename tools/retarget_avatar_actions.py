#!/usr/bin/env python3
"""
Retarget 3 guide animations from a source avatar to a VRoid target avatar.

This script runs inside Blender and bakes constraints to target armature actions.
"""

from __future__ import annotations

import argparse
import json
import logging
import sys
from pathlib import Path

import bpy  # type: ignore


LOGGER = logging.getLogger("retarget_avatar_actions")

DEFAULT_ACTIONS = [
    "Pointing Forward_1",
    "Waving Gesture_2",
    "Jumping Down_3",
]

MIXAMO_TO_VROID_BONES: dict[str, str] = {
    "mixamorigHips": "J_Bip_C_Hips",
    "mixamorigSpine": "J_Bip_C_Spine",
    "mixamorigSpine1": "J_Bip_C_Chest",
    "mixamorigSpine2": "J_Bip_C_UpperChest",
    "mixamorigNeck": "J_Bip_C_Neck",
    "mixamorigHead": "J_Bip_C_Head",
    "mixamorigLeftShoulder": "J_Bip_L_Shoulder",
    "mixamorigLeftArm": "J_Bip_L_UpperArm",
    "mixamorigLeftForeArm": "J_Bip_L_LowerArm",
    "mixamorigLeftHand": "J_Bip_L_Hand",
    "mixamorigLeftHandThumb1": "J_Bip_L_Thumb1",
    "mixamorigLeftHandThumb2": "J_Bip_L_Thumb2",
    "mixamorigLeftHandThumb3": "J_Bip_L_Thumb3",
    "mixamorigLeftHandIndex1": "J_Bip_L_Index1",
    "mixamorigLeftHandIndex2": "J_Bip_L_Index2",
    "mixamorigLeftHandIndex3": "J_Bip_L_Index3",
    "mixamorigLeftHandMiddle1": "J_Bip_L_Middle1",
    "mixamorigLeftHandMiddle2": "J_Bip_L_Middle2",
    "mixamorigLeftHandMiddle3": "J_Bip_L_Middle3",
    "mixamorigLeftHandRing1": "J_Bip_L_Ring1",
    "mixamorigLeftHandRing2": "J_Bip_L_Ring2",
    "mixamorigLeftHandRing3": "J_Bip_L_Ring3",
    "mixamorigLeftHandPinky1": "J_Bip_L_Little1",
    "mixamorigLeftHandPinky2": "J_Bip_L_Little2",
    "mixamorigLeftHandPinky3": "J_Bip_L_Little3",
    "mixamorigRightShoulder": "J_Bip_R_Shoulder",
    "mixamorigRightArm": "J_Bip_R_UpperArm",
    "mixamorigRightForeArm": "J_Bip_R_LowerArm",
    "mixamorigRightHand": "J_Bip_R_Hand",
    "mixamorigRightHandThumb1": "J_Bip_R_Thumb1",
    "mixamorigRightHandThumb2": "J_Bip_R_Thumb2",
    "mixamorigRightHandThumb3": "J_Bip_R_Thumb3",
    "mixamorigRightHandIndex1": "J_Bip_R_Index1",
    "mixamorigRightHandIndex2": "J_Bip_R_Index2",
    "mixamorigRightHandIndex3": "J_Bip_R_Index3",
    "mixamorigRightHandMiddle1": "J_Bip_R_Middle1",
    "mixamorigRightHandMiddle2": "J_Bip_R_Middle2",
    "mixamorigRightHandMiddle3": "J_Bip_R_Middle3",
    "mixamorigRightHandRing1": "J_Bip_R_Ring1",
    "mixamorigRightHandRing2": "J_Bip_R_Ring2",
    "mixamorigRightHandRing3": "J_Bip_R_Ring3",
    "mixamorigRightHandPinky1": "J_Bip_R_Little1",
    "mixamorigRightHandPinky2": "J_Bip_R_Little2",
    "mixamorigRightHandPinky3": "J_Bip_R_Little3",
    "mixamorigLeftUpLeg": "J_Bip_L_UpperLeg",
    "mixamorigLeftLeg": "J_Bip_L_LowerLeg",
    "mixamorigLeftFoot": "J_Bip_L_Foot",
    "mixamorigLeftToeBase": "J_Bip_L_ToeBase",
    "mixamorigRightUpLeg": "J_Bip_R_UpperLeg",
    "mixamorigRightLeg": "J_Bip_R_LowerLeg",
    "mixamorigRightFoot": "J_Bip_R_Foot",
    "mixamorigRightToeBase": "J_Bip_R_ToeBase",
}


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Retarget source guide actions to a VRoid target model.")
    parser.add_argument("--source", required=True, help="Source animated GLB (Mixamo rig)")
    parser.add_argument("--target", required=True, help="Target VRoid GLB (no animation required)")
    parser.add_argument("--output", required=True, help="Output GLB with baked animations")
    parser.add_argument(
        "--actions",
        default=",".join(DEFAULT_ACTIONS),
        help="Comma-separated source action names to retarget",
    )
    parser.add_argument("--metadata-out", default="", help="Optional metadata json path")
    return parser.parse_args(argv)


def reset_scene() -> None:
    bpy.ops.wm.read_factory_settings(use_empty=True)


def import_glb(path: Path) -> list[bpy.types.Object]:
    before = set(bpy.data.objects)
    bpy.ops.import_scene.gltf(filepath=str(path))
    after = set(bpy.data.objects)
    return list(after - before)


def find_primary_armature(objects: list[bpy.types.Object]) -> bpy.types.Object:
    armatures = [obj for obj in objects if obj.type == "ARMATURE"]
    if not armatures:
        raise RuntimeError("No armature found in imported objects")
    return max(armatures, key=lambda obj: len(obj.data.bones))


def find_action_by_name(action_name: str) -> bpy.types.Action | None:
    for action in bpy.data.actions:
        if action.name == action_name:
            return action
    for action in bpy.data.actions:
        if action_name.lower() in action.name.lower():
            return action
    return None


def build_constraints(
    source_armature: bpy.types.Object,
    target_armature: bpy.types.Object,
) -> tuple[int, int]:
    mapped = 0
    total = len(MIXAMO_TO_VROID_BONES)
    for src_bone, dst_bone in MIXAMO_TO_VROID_BONES.items():
        if src_bone not in source_armature.pose.bones:
            continue
        if dst_bone not in target_armature.pose.bones:
            continue
        target_pose_bone = target_armature.pose.bones[dst_bone]
        copy_rotation = target_pose_bone.constraints.new(type="COPY_ROTATION")
        copy_rotation.name = "RetargetCopyRotation"
        copy_rotation.target = source_armature
        copy_rotation.subtarget = src_bone
        copy_rotation.owner_space = "LOCAL"
        copy_rotation.target_space = "LOCAL_OWNER_ORIENT"
        copy_rotation.mix_mode = "REPLACE"
        if dst_bone == "J_Bip_C_Hips":
            copy_location = target_pose_bone.constraints.new(type="COPY_LOCATION")
            copy_location.name = "RetargetCopyLocation"
            copy_location.target = source_armature
            copy_location.subtarget = src_bone
            copy_location.owner_space = "LOCAL"
            copy_location.target_space = "LOCAL"
        mapped += 1
    return mapped, total


def clear_retarget_constraints(target_armature: bpy.types.Object) -> None:
    for pose_bone in target_armature.pose.bones:
        for constraint in list(pose_bone.constraints):
            if constraint.name.startswith("RetargetCopy"):
                pose_bone.constraints.remove(constraint)


def bake_action(
    source_armature: bpy.types.Object,
    target_armature: bpy.types.Object,
    source_action: bpy.types.Action,
    action_name: str,
) -> bool:
    source_armature.animation_data_create()
    source_armature.animation_data.action = source_action

    target_armature.animation_data_create()
    baked_action = bpy.data.actions.new(name=action_name)
    target_armature.animation_data.action = baked_action

    frame_start = int(source_action.frame_range[0])
    frame_end = int(source_action.frame_range[1])
    bpy.context.scene.frame_start = frame_start
    bpy.context.scene.frame_end = frame_end
    bpy.context.scene.frame_set(frame_start)

    bpy.ops.object.mode_set(mode="OBJECT")
    bpy.ops.object.select_all(action="DESELECT")
    bpy.context.view_layer.objects.active = target_armature
    target_armature.select_set(True)
    bpy.ops.object.mode_set(mode="POSE")
    for pose_bone in target_armature.pose.bones:
        pose_bone.bone.select = True

    bpy.ops.nla.bake(
        frame_start=frame_start,
        frame_end=frame_end,
        step=1,
        only_selected=True,
        visual_keying=True,
        clear_constraints=False,
        use_current_action=True,
        bake_types={"POSE"},
    )
    bpy.ops.object.mode_set(mode="OBJECT")

    created_action = target_armature.animation_data.action
    if created_action is None:
        return False
    created_action.name = action_name
    created_action.use_fake_user = True
    return True


def delete_objects(objects: list[bpy.types.Object]) -> None:
    for obj in objects:
        if obj.name in bpy.data.objects:
            bpy.data.objects.remove(obj, do_unlink=True)


def prune_actions(allowed_action_names: set[str]) -> None:
    for action in list(bpy.data.actions):
        if action.name not in allowed_action_names:
            bpy.data.actions.remove(action)


def export_target(target_objects: list[bpy.types.Object], output: Path) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    bpy.ops.object.select_all(action="DESELECT")
    for obj in target_objects:
        if obj.name in bpy.data.objects:
            obj.select_set(True)
            if obj.type == "ARMATURE":
                bpy.context.view_layer.objects.active = obj
    bpy.ops.export_scene.gltf(
        filepath=str(output),
        use_selection=True,
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


def write_metadata(path: Path, payload: dict[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")


def main(argv: list[str]) -> int:
    logging.basicConfig(level=logging.INFO, format="[%(levelname)s] %(message)s")
    args = parse_args(argv)

    source_path = Path(args.source).resolve()
    target_path = Path(args.target).resolve()
    output_path = Path(args.output).resolve()
    metadata_out = Path(args.metadata_out).resolve() if args.metadata_out else None
    requested_actions = [name.strip() for name in args.actions.split(",") if name.strip()]
    if not requested_actions:
        requested_actions = DEFAULT_ACTIONS

    if not source_path.exists():
        LOGGER.error("Source not found: %s", source_path)
        return 2
    if not target_path.exists():
        LOGGER.error("Target not found: %s", target_path)
        return 2

    reset_scene()
    LOGGER.info("Import source: %s", source_path)
    source_objects = import_glb(source_path)
    source_armature = find_primary_armature(source_objects)
    source_armature.name = "SourceArmature"
    source_actions = {action.name: action for action in bpy.data.actions}

    LOGGER.info("Import target: %s", target_path)
    target_objects = import_glb(target_path)
    target_armature = find_primary_armature(target_objects)
    target_armature.name = "TargetArmature"

    mapped_pairs, total_pairs = build_constraints(source_armature, target_armature)
    bone_coverage = (mapped_pairs / total_pairs * 100.0) if total_pairs > 0 else 0.0
    LOGGER.info("Bone mapping coverage: %d/%d = %.2f%%", mapped_pairs, total_pairs, bone_coverage)

    baked_names: list[str] = []
    for action_name in requested_actions:
        action = source_actions.get(action_name) or find_action_by_name(action_name)
        if action is None:
            LOGGER.warning("Source action missing: %s", action_name)
            continue
        LOGGER.info("Baking action: %s", action_name)
        success = bake_action(source_armature, target_armature, action, action_name)
        if success:
            baked_names.append(action_name)

    clear_retarget_constraints(target_armature)
    delete_objects(source_objects)
    prune_actions(set(baked_names))
    export_target(target_objects, output_path)

    success_count = len(baked_names)
    retarget_success = int(success_count == len(requested_actions) and success_count > 0)
    LOGGER.info(
        "Retarget result: success=%s, baked=%d/%d, output=%s",
        bool(retarget_success),
        success_count,
        len(requested_actions),
        output_path,
    )

    payload = {
        "retargetSuccess": bool(retarget_success),
        "requestedActions": requested_actions,
        "bakedActions": baked_names,
        "boneCoverage": round(bone_coverage, 3),
        "mappedPairs": mapped_pairs,
        "totalPairs": total_pairs,
        "source": str(source_path),
        "target": str(target_path),
        "output": str(output_path),
    }
    if metadata_out is not None:
        write_metadata(metadata_out, payload)
        LOGGER.info("Metadata written: %s", metadata_out)

    return 0 if success_count > 0 else 3


if __name__ == "__main__":
    if "--" in sys.argv:
        cli_args = sys.argv[sys.argv.index("--") + 1 :]
    else:
        cli_args = []
    raise SystemExit(main(cli_args))

