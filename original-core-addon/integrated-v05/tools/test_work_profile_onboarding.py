#!/usr/bin/env python3
"""Strict source-only safety contracts for optional, user-authorized DPC handoff.
Android device Work Profile creation and vendor compatibility are NOT tested.
"""
from pathlib import Path
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
java=(root/"library/src/main/java/com/omnigate/integrated/AndroidProfileSafetyCenter.java").read_text()
manifest=(root/"library/src/main/AndroidManifest.xml").read_text()
tags=["offerWorkProfileCreation(Activity host)", "canProvisionHere(Activity host)",
      "isProvisioningAllowed(", "FEATURE_MANAGED_USERS", "SHELTER_OFFICIAL",
      "https://f-droid.org/packages/net.typeblog.shelter/",
      "getLaunchIntentForPackage(SHELTER_PACKAGE)",
      '"net.typeblog.shelter.ui.MainActivity"', 
      "创建工作资料 · 手机端系统授权",
      "打开/获取 Shelter", "Vivo XSpace（666）", "应用分身（999）",
      "不通过 root/ADB", "保留现有资料"]
for t in tags:
    assert t in java, f"Missing safety: {t}"
# Reject a "shortcut" that would silently claim permission to provision an
# Android profile or destructively change any existing Android user.
for forbidden in ("startActivity(new Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE",
                  "createAndManageUser(", "removeUser(", "clearApplicationUserData(",
                  "setProfileOwner(", "setDeviceOwner(", "dpm set-profile-owner",
                  "adb shell pm create-user", "requestPermissions(new String[]"):
    assert forbidden not in java, f"Privileged shortcut: {forbidden}"
xml=ET.fromstring(manifest)
an="{http://schemas.android.com/apk/res/android}"
queries=xml.find("queries")
assert queries is not None
assert any(tag.attrib.get(an+"name")=="net.typeblog.shelter"
           for tag in queries.findall("package"))
permissions=[x.attrib.get(an+"name") for x in xml.findall("uses-permission")]
for forbidden in ("android.permission.MANAGE_USERS", 
                  "android.permission.INTERACT_ACROSS_USERS",
                  "android.permission.MANAGE_PROFILE_AND_DEVICE_OWNERS"):
    assert forbidden not in permissions, forbidden
assert "<receiver" not in manifest or "DeviceAdminReceiver" not in manifest
print("OGPROFILE07_SHELTER_USER_CONSENTED_DPC_HANDOFF=PASS")
print("OGPROFILE07_NO_PRIVILEGE_OR_USER_DATA_DELETION=PASS")
print("OGPROFILE07_XML_NARROW_PACKAGE_VISIBILITY=PASS")
print("OGPROFILE07_VIVO_XSPACE_CLONE_RETAINED=PASS_SOURCE_ONLY")
print("OGPROFILE07_ANDROID16_VIVO_WORK_PROFILE_CREATION=NOT_TESTED")
