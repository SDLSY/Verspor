#!/usr/bin/env python3
"""
Generate semantic + lively guide actions directly on a VRoid armature.

Core actions (names preserved for runtime mapping):
  - Waving Gesture_2      -> IDLE
  - Pointing Forward_1    -> SPEAK
  - Jumping Down_3        -> EMPHASIS

Additional lively idle actions:
  - Friendly Wave_4
  - Idle Sway_5
  - Cheer Pose_6
  - Guide Left_7

Pain showcase actions:
  - Head Pain_8
  - Chest Pain_9
  - Abdominal Pain_10
  - Limb Pain_11
"""

from __future__ import annotations

import argparse
import logging
import sys
from pathlib import Path

import bpy  # type: ignore


LOGGER = logging.getLogger("generate_avatar_procedural_actions")


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate procedural avatar guide actions.")
    parser.add_argument("--input", required=True, help="Input target GLB")
    parser.add_argument("--output", required=True, help="Output GLB with generated actions")
    return parser.parse_args(argv)


def reset_scene() -> None:
    bpy.ops.wm.read_factory_settings(use_empty=True)


def import_glb(path: Path) -> list[bpy.types.Object]:
    before = set(bpy.data.objects)
    bpy.ops.import_scene.gltf(filepath=str(path))
    after = set(bpy.data.objects)
    return list(after - before)


def find_armature(objects: list[bpy.types.Object]) -> bpy.types.Object:
    armatures = [obj for obj in objects if obj.type == "ARMATURE"]
    if not armatures:
        raise RuntimeError("No armature found in avatar model")
    return max(armatures, key=lambda obj: len(obj.data.bones))


def pose_bone(armature: bpy.types.Object, name: str) -> bpy.types.PoseBone | None:
    return armature.pose.bones.get(name)


def clear_pose(armature: bpy.types.Object) -> None:
    for pb in armature.pose.bones:
        pb.rotation_mode = "XYZ"
        pb.location = (0.0, 0.0, 0.0)
        pb.rotation_euler = (0.0, 0.0, 0.0)
        pb.scale = (1.0, 1.0, 1.0)


def apply_natural_idle_pose(armature: bpy.types.Object) -> None:
    # Keep a neutral standing pose with relaxed arms + subtle torso breathing.
    chest = pose_bone(armature, "J_Bip_C_Chest")
    hips = pose_bone(armature, "J_Bip_C_Hips")
    l_upper = pose_bone(armature, "J_Bip_L_UpperArm")
    r_upper = pose_bone(armature, "J_Bip_R_UpperArm")
    l_lower = pose_bone(armature, "J_Bip_L_LowerArm")
    r_lower = pose_bone(armature, "J_Bip_R_LowerArm")
    l_hand = pose_bone(armature, "J_Bip_L_Hand")
    r_hand = pose_bone(armature, "J_Bip_R_Hand")
    if chest:
        chest.rotation_euler = (0.015, 0.0, 0.0)
    if hips:
        hips.rotation_euler = (-0.015, 0.0, 0.0)
    # VRoid import pose is close to T-pose; explicitly lower both arms to resting stance.
    if l_upper:
        l_upper.rotation_euler = (0.0, 0.0, 1.15)
    if r_upper:
        r_upper.rotation_euler = (0.0, 0.0, -1.15)
    if l_lower:
        l_lower.rotation_euler = (0.0, 0.0, 0.22)
    if r_lower:
        r_lower.rotation_euler = (0.0, 0.0, -0.22)
    if l_hand:
        l_hand.rotation_euler = (0.0, 0.0, 0.05)
    if r_hand:
        r_hand.rotation_euler = (0.0, 0.0, -0.05)


def insert_keyframe_for_all_bones(armature: bpy.types.Object, frame: int) -> None:
    bpy.context.scene.frame_set(frame)
    for pb in armature.pose.bones:
        pb.keyframe_insert(data_path="location", frame=frame)
        pb.keyframe_insert(data_path="rotation_euler", frame=frame)
        pb.keyframe_insert(data_path="scale", frame=frame)


def create_idle_action(armature: bpy.types.Object) -> bpy.types.Action:
    action = bpy.data.actions.new(name="Waving Gesture_2")
    armature.animation_data_create()
    armature.animation_data.action = action

    for frame, chest_x, head_x in [(0, 0.02, 0.00), (18, 0.06, 0.02), (36, 0.02, 0.00)]:
        clear_pose(armature)
        apply_natural_idle_pose(armature)
        chest = pose_bone(armature, "J_Bip_C_Chest")
        head = pose_bone(armature, "J_Bip_C_Head")
        if chest:
            chest.rotation_euler.x += chest_x
        if head:
            head.rotation_euler.x += head_x
        insert_keyframe_for_all_bones(armature, frame)

    action.use_fake_user = True
    return action


def create_friendly_wave_action(armature: bpy.types.Object) -> bpy.types.Action:
    action = bpy.data.actions.new(name="Friendly Wave_4")
    armature.animation_data_create()
    armature.animation_data.action = action
    frames = [0, 10, 20, 30, 40]
    hand_y = [0.10, -0.28, 0.22, -0.22, 0.10]
    for idx, frame in enumerate(frames):
        clear_pose(armature)
        apply_natural_idle_pose(armature)
        r_upper = pose_bone(armature, "J_Bip_R_UpperArm")
        r_lower = pose_bone(armature, "J_Bip_R_LowerArm")
        r_hand = pose_bone(armature, "J_Bip_R_Hand")
        head = pose_bone(armature, "J_Bip_C_Head")
        if r_upper:
            r_upper.rotation_euler = (-1.08, 0.02, -0.36)
        if r_lower:
            r_lower.rotation_euler = (-0.25, 0.06, -0.20)
        if r_hand:
            r_hand.rotation_euler = (0.0, hand_y[idx], -0.12)
        if head:
            head.rotation_euler = (0.0, 0.0, 0.06)
        insert_keyframe_for_all_bones(armature, frame)
    action.use_fake_user = True
    return action


def create_idle_sway_action(armature: bpy.types.Object) -> bpy.types.Action:
    action = bpy.data.actions.new(name="Idle Sway_5")
    armature.animation_data_create()
    armature.animation_data.action = action
    keyframes = [
        (0, -0.05, 0.02),
        (16, 0.06, -0.02),
        (32, -0.05, 0.02),
    ]
    for frame, hips_y, chest_z in keyframes:
        clear_pose(armature)
        apply_natural_idle_pose(armature)
        hips = pose_bone(armature, "J_Bip_C_Hips")
        chest = pose_bone(armature, "J_Bip_C_Chest")
        head = pose_bone(armature, "J_Bip_C_Head")
        if hips:
            hips.location.x = hips_y
        if chest:
            chest.rotation_euler.z = chest_z
        if head:
            head.rotation_euler.z = chest_z * 0.45
        insert_keyframe_for_all_bones(armature, frame)
    action.use_fake_user = True
    return action


def create_cheer_pose_action(armature: bpy.types.Object) -> bpy.types.Action:
    action = bpy.data.actions.new(name="Cheer Pose_6")
    armature.animation_data_create()
    armature.animation_data.action = action
    keyframes = [
        (0, 0.00, 0.00),
        (10, 0.14, 0.18),
        (22, -0.04, -0.05),
        (32, 0.00, 0.00),
    ]
    for frame, bounce, chest_pitch in keyframes:
        clear_pose(armature)
        apply_natural_idle_pose(armature)
        hips = pose_bone(armature, "J_Bip_C_Hips")
        chest = pose_bone(armature, "J_Bip_C_Chest")
        l_upper = pose_bone(armature, "J_Bip_L_UpperArm")
        r_upper = pose_bone(armature, "J_Bip_R_UpperArm")
        if hips:
            hips.location.z = bounce
        if chest:
            chest.rotation_euler.x += chest_pitch
        if l_upper:
            l_upper.rotation_euler = (-0.84, 0.08, 0.96)
        if r_upper:
            r_upper.rotation_euler = (-0.84, -0.08, -0.96)
        insert_keyframe_for_all_bones(armature, frame)
    action.use_fake_user = True
    return action


def create_guide_left_action(armature: bpy.types.Object) -> bpy.types.Action:
    action = bpy.data.actions.new(name="Guide Left_7")
    armature.animation_data_create()
    armature.animation_data.action = action
    keyframes = [
        (0, 0.0),
        (14, 0.14),
        (28, 0.0),
    ]
    for frame, head_y in keyframes:
        clear_pose(armature)
        apply_natural_idle_pose(armature)
        l_upper = pose_bone(armature, "J_Bip_L_UpperArm")
        l_lower = pose_bone(armature, "J_Bip_L_LowerArm")
        l_hand = pose_bone(armature, "J_Bip_L_Hand")
        head = pose_bone(armature, "J_Bip_C_Head")
        if l_upper:
            l_upper.rotation_euler = (-0.52, 0.04, 1.24)
        if l_lower:
            l_lower.rotation_euler = (-0.22, 0.04, 0.20)
        if l_hand:
            l_hand.rotation_euler = (0.0, -0.20, 0.08)
        if head:
            head.rotation_euler.y = head_y
        insert_keyframe_for_all_bones(armature, frame)
    action.use_fake_user = True
    return action


def create_head_pain_action(armature: bpy.types.Object) -> bpy.types.Action:
    action = bpy.data.actions.new(name="Head Pain_8")
    armature.animation_data_create()
    armature.animation_data.action = action
    keyframes = [
        (0, 0.04, 0.00, 0.00),
        (8, 0.18, 0.06, -0.10),
        (16, 0.22, -0.05, 0.08),
        (24, 0.18, 0.05, -0.08),
        (32, 0.04, 0.00, 0.00),
    ]
    for frame, chest_pitch, head_roll, head_yaw in keyframes:
        clear_pose(armature)
        apply_natural_idle_pose(armature)
        chest = pose_bone(armature, "J_Bip_C_Chest")
        head = pose_bone(armature, "J_Bip_C_Head")
        l_upper = pose_bone(armature, "J_Bip_L_UpperArm")
        r_upper = pose_bone(armature, "J_Bip_R_UpperArm")
        l_lower = pose_bone(armature, "J_Bip_L_LowerArm")
        r_lower = pose_bone(armature, "J_Bip_R_LowerArm")
        l_hand = pose_bone(armature, "J_Bip_L_Hand")
        r_hand = pose_bone(armature, "J_Bip_R_Hand")
        if chest:
            chest.rotation_euler.x += chest_pitch
        if head:
            head.rotation_euler = (0.20, head_yaw, head_roll)
        if l_upper:
            l_upper.rotation_euler = (-0.90, 0.08, 0.84)
        if r_upper:
            r_upper.rotation_euler = (-0.90, -0.08, -0.84)
        if l_lower:
            l_lower.rotation_euler = (-0.52, 0.12, 0.18)
        if r_lower:
            r_lower.rotation_euler = (-0.52, -0.12, -0.18)
        if l_hand:
            l_hand.rotation_euler = (0.14, -0.18, 0.20)
        if r_hand:
            r_hand.rotation_euler = (0.14, 0.18, -0.20)
        insert_keyframe_for_all_bones(armature, frame)
    action.use_fake_user = True
    return action


def create_chest_pain_action(armature: bpy.types.Object) -> bpy.types.Action:
    action = bpy.data.actions.new(name="Chest Pain_9")
    armature.animation_data_create()
    armature.animation_data.action = action
    keyframes = [
        (0, 0.02, 0.00, 0.00),
        (10, 0.12, 0.10, -0.08),
        (20, 0.18, -0.06, 0.05),
        (32, 0.02, 0.00, 0.00),
    ]
    for frame, chest_pitch, chest_roll, head_pitch in keyframes:
        clear_pose(armature)
        apply_natural_idle_pose(armature)
        chest = pose_bone(armature, "J_Bip_C_Chest")
        head = pose_bone(armature, "J_Bip_C_Head")
        hips = pose_bone(armature, "J_Bip_C_Hips")
        l_upper = pose_bone(armature, "J_Bip_L_UpperArm")
        r_upper = pose_bone(armature, "J_Bip_R_UpperArm")
        l_lower = pose_bone(armature, "J_Bip_L_LowerArm")
        r_lower = pose_bone(armature, "J_Bip_R_LowerArm")
        r_hand = pose_bone(armature, "J_Bip_R_Hand")
        if hips:
            hips.location.z = -0.02
        if chest:
            chest.rotation_euler.x += chest_pitch
            chest.rotation_euler.z += chest_roll
        if head:
            head.rotation_euler.x += head_pitch
        if l_upper:
            l_upper.rotation_euler = (-0.08, 0.02, 1.02)
        if l_lower:
            l_lower.rotation_euler = (-0.08, 0.04, 0.18)
        if r_upper:
            r_upper.rotation_euler = (-0.32, 0.18, -0.12)
        if r_lower:
            r_lower.rotation_euler = (-0.92, 0.10, -0.40)
        if r_hand:
            r_hand.rotation_euler = (0.20, -0.08, -0.10)
        insert_keyframe_for_all_bones(armature, frame)
    action.use_fake_user = True
    return action


def create_abdominal_pain_action(armature: bpy.types.Object) -> bpy.types.Action:
    action = bpy.data.actions.new(name="Abdominal Pain_10")
    armature.animation_data_create()
    armature.animation_data.action = action
    keyframes = [
        (0, 0.06, -0.02),
        (10, 0.20, -0.10),
        (20, 0.28, 0.06),
        (32, 0.06, -0.02),
    ]
    for frame, chest_pitch, hips_pitch in keyframes:
        clear_pose(armature)
        apply_natural_idle_pose(armature)
        chest = pose_bone(armature, "J_Bip_C_Chest")
        hips = pose_bone(armature, "J_Bip_C_Hips")
        head = pose_bone(armature, "J_Bip_C_Head")
        l_upper = pose_bone(armature, "J_Bip_L_UpperArm")
        r_upper = pose_bone(armature, "J_Bip_R_UpperArm")
        l_lower = pose_bone(armature, "J_Bip_L_LowerArm")
        r_lower = pose_bone(armature, "J_Bip_R_LowerArm")
        l_hand = pose_bone(armature, "J_Bip_L_Hand")
        r_hand = pose_bone(armature, "J_Bip_R_Hand")
        if chest:
            chest.rotation_euler.x += chest_pitch
        if hips:
            hips.rotation_euler.x += hips_pitch
            hips.location.z = -0.04
        if head:
            head.rotation_euler.x += 0.10
        if l_upper:
            l_upper.rotation_euler = (-0.42, 0.06, 0.86)
        if r_upper:
            r_upper.rotation_euler = (-0.42, -0.06, -0.86)
        if l_lower:
            l_lower.rotation_euler = (-1.04, 0.08, 0.30)
        if r_lower:
            r_lower.rotation_euler = (-1.04, -0.08, -0.30)
        if l_hand:
            l_hand.rotation_euler = (0.18, -0.12, 0.12)
        if r_hand:
            r_hand.rotation_euler = (0.18, 0.12, -0.12)
        insert_keyframe_for_all_bones(armature, frame)
    action.use_fake_user = True
    return action


def create_limb_pain_action(armature: bpy.types.Object) -> bpy.types.Action:
    action = bpy.data.actions.new(name="Limb Pain_11")
    armature.animation_data_create()
    armature.animation_data.action = action
    keyframes = [
        (0, -0.02, 0.00, 0.00),
        (10, 0.12, 0.10, -0.08),
        (20, -0.08, -0.08, 0.06),
        (32, -0.02, 0.00, 0.00),
    ]
    for frame, hips_shift, chest_roll, head_roll in keyframes:
        clear_pose(armature)
        apply_natural_idle_pose(armature)
        hips = pose_bone(armature, "J_Bip_C_Hips")
        chest = pose_bone(armature, "J_Bip_C_Chest")
        head = pose_bone(armature, "J_Bip_C_Head")
        l_upper = pose_bone(armature, "J_Bip_L_UpperArm")
        r_upper = pose_bone(armature, "J_Bip_R_UpperArm")
        l_lower = pose_bone(armature, "J_Bip_L_LowerArm")
        r_lower = pose_bone(armature, "J_Bip_R_LowerArm")
        l_leg = pose_bone(armature, "J_Bip_L_UpperLeg")
        l_knee = pose_bone(armature, "J_Bip_L_LowerLeg")
        r_leg = pose_bone(armature, "J_Bip_R_UpperLeg")
        if hips:
            hips.location.x = hips_shift
        if chest:
            chest.rotation_euler.z += chest_roll
        if head:
            head.rotation_euler.z += head_roll
        if l_upper:
            l_upper.rotation_euler = (-0.18, 0.02, 0.98)
        if l_lower:
            l_lower.rotation_euler = (-0.06, 0.04, 0.20)
        if r_upper:
            r_upper.rotation_euler = (-0.30, 0.12, -0.42)
        if r_lower:
            r_lower.rotation_euler = (-0.82, 0.12, -0.24)
        if l_leg:
            l_leg.rotation_euler = (0.10, 0.02, 0.08)
        if l_knee:
            l_knee.rotation_euler = (0.18, 0.00, 0.00)
        if r_leg:
            r_leg.rotation_euler = (-0.04, 0.00, -0.04)
        insert_keyframe_for_all_bones(armature, frame)
    action.use_fake_user = True
    return action


def create_speak_action(armature: bpy.types.Object) -> bpy.types.Action:
    action = bpy.data.actions.new(name="Pointing Forward_1")
    armature.animation_data_create()
    armature.animation_data.action = action

    # Speaking gesture: right arm lifts from relaxed pose and points forward.
    frames = [0, 12, 24, 36]
    nod = [0.00, 0.10, -0.05, 0.00]
    forearm = [-0.22, -0.35, -0.28, -0.22]
    upper_z = [-0.24, -0.34, -0.30, -0.24]
    for idx, frame in enumerate(frames):
        clear_pose(armature)
        apply_natural_idle_pose(armature)

        r_upper = pose_bone(armature, "J_Bip_R_UpperArm")
        r_lower = pose_bone(armature, "J_Bip_R_LowerArm")
        l_upper = pose_bone(armature, "J_Bip_L_UpperArm")
        chest = pose_bone(armature, "J_Bip_C_Chest")
        head = pose_bone(armature, "J_Bip_C_Head")

        if r_upper:
            r_upper.rotation_euler = (-0.36, 0.06, upper_z[idx])
        if r_lower:
            r_lower.rotation_euler = (forearm[idx], 0.02, -0.12)
        if l_upper:
            l_upper.rotation_euler = (0.02, 0.0, 1.12)
        if chest:
            chest.rotation_euler.x += 0.03
        if head:
            head.rotation_euler.x += nod[idx]
        insert_keyframe_for_all_bones(armature, frame)

    action.use_fake_user = True
    return action


def create_emphasis_action(armature: bpy.types.Object) -> bpy.types.Action:
    action = bpy.data.actions.new(name="Jumping Down_3")
    armature.animation_data_create()
    armature.animation_data.action = action

    # Emphasis gesture: quick body bounce + both arms open from relaxed pose.
    keyframes = [
        (0, 0.00, 0.00),
        (8, 0.10, 0.14),
        (16, -0.03, -0.05),
        (24, 0.00, 0.00),
    ]
    for frame, hips_up, chest_pitch in keyframes:
        clear_pose(armature)
        apply_natural_idle_pose(armature)
        hips = pose_bone(armature, "J_Bip_C_Hips")
        chest = pose_bone(armature, "J_Bip_C_Chest")
        l_upper = pose_bone(armature, "J_Bip_L_UpperArm")
        r_upper = pose_bone(armature, "J_Bip_R_UpperArm")
        if hips:
            hips.location.z = hips_up
        if chest:
            chest.rotation_euler.x += chest_pitch
        if l_upper:
            l_upper.rotation_euler.x += -0.16
            l_upper.rotation_euler.z += -0.22
        if r_upper:
            r_upper.rotation_euler.x += -0.16
            r_upper.rotation_euler.z += 0.22
        insert_keyframe_for_all_bones(armature, frame)

    action.use_fake_user = True
    return action


def remove_existing_actions() -> None:
    for action in list(bpy.data.actions):
        bpy.data.actions.remove(action)


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
    input_path = Path(args.input).resolve()
    output_path = Path(args.output).resolve()
    if not input_path.exists():
        LOGGER.error("Input not found: %s", input_path)
        return 2

    reset_scene()
    imported_objects = import_glb(input_path)
    armature = find_armature(imported_objects)
    remove_existing_actions()
    create_idle_action(armature)
    create_speak_action(armature)
    create_emphasis_action(armature)
    create_friendly_wave_action(armature)
    create_idle_sway_action(armature)
    create_cheer_pose_action(armature)
    create_guide_left_action(armature)
    create_head_pain_action(armature)
    create_chest_pain_action(armature)
    create_abdominal_pain_action(armature)
    create_limb_pain_action(armature)
    export_glb(output_path)

    LOGGER.info("Generated procedural actions for avatar: %s", output_path)
    return 0


if __name__ == "__main__":
    if "--" in sys.argv:
        cli_args = sys.argv[sys.argv.index("--") + 1 :]
    else:
        cli_args = []
    raise SystemExit(main(cli_args))
