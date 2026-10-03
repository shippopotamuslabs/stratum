"""VERSIONING.md §2 decoder-leniency rows for the Python v1 request decoder."""

import pytest

from solver.wire import (
    canonical_role,
    decode_request,
    parse_model_version,
    region_filter_for_session_type,
    WireError,
)

from .support import tiny_request


def test_missing_model_version_means_v1():
    req = tiny_request()
    del req["modelVersion"]
    assert parse_model_version(req.get("modelVersion"))[0] == 1
    assert decode_request(req)["modelVersion"] == "v1"


@pytest.mark.parametrize("version", ["v1", "v1.0", "v1.1", "v1.9"])
def test_v1_minor_accepted_as_v1(version):
    req = tiny_request()
    req["modelVersion"] = version
    assert decode_request(req)["modelVersion"] == version


@pytest.mark.parametrize("version", ["v2", "v3", "v1x", "1", "V1", "", ["v1"], 1])
def test_unknown_major_rejected(version):
    req = tiny_request()
    req["modelVersion"] = version
    with pytest.raises(WireError):
        decode_request(req)


def test_null_model_version_tolerated_as_v1():
    req = tiny_request()
    req["modelVersion"] = None
    assert decode_request(req)["modelVersion"] == "v1"


def test_unknown_fields_ignored_top_level_and_nested():
    req = tiny_request()
    req["futureField"] = {"anything": [1, 2, 3]}
    req["context"]["anotherNewField"] = "x"
    lift_id = next(iter(req["context"]["liftsById"]))
    req["context"]["liftsById"][lift_id]["brandNewLiftField"] = 42
    decoded = decode_request(req)
    assert decoded["context"]["trainingDaysPerWeek"] == 1


def test_missing_fatigue_profile_defaults_low_and_unknown_defaults_low():
    req = tiny_request()
    lift_id = next(iter(req["context"]["liftsById"]))
    del req["context"]["liftsById"][lift_id]["fatigueProfile"]
    assert decode_request(req)["context"]["liftsById"][lift_id]["fatigueProfile"] == "Low"
    req2 = tiny_request()
    req2["context"]["liftsById"][lift_id]["fatigueProfile"] = "AxialCompound"
    assert decode_request(req2)["context"]["liftsById"][lift_id]["fatigueProfile"] == "Low"


def test_missing_increment_tiers_defaults_empty():
    req = tiny_request()
    lift_id = next(iter(req["context"]["liftsById"]))
    del req["context"]["liftsById"][lift_id]["incrementTiers"]
    assert decode_request(req)["context"]["liftsById"][lift_id]["incrementTiers"] == []


def test_missing_cycle_space_means_server_defaults():
    req = tiny_request()
    ctx = decode_request(req)["context"]
    assert ctx["restSlotIndices"] is None
    assert ctx["cycleLength"] is None


def test_free_form_strings_tolerated():
    req = tiny_request()
    req["context"]["trainingAge"] = "Super Saiyan 12"
    req["context"]["lastTrained"] = "a long long time ago"
    decoded = decode_request(req)
    assert decoded["context"]["trainingAge"] == "Super Saiyan 12"
    assert decoded["context"]["lastTrained"] == "a long long time ago"


def test_unknown_specialization_level_defaults_maintenance():
    req = tiny_request()
    req["context"]["specialization"] = {"Chest": "HyperFocus"}
    assert decode_request(req)["context"]["specialization"]["Chest"] == "Maintenance"


def test_role_isolation_alias_maps_to_accessory():
    assert canonical_role("Isolation") == "Accessory"
    assert canonical_role("Accessory") == "Accessory"
    assert canonical_role("Anchor") == "Anchor"


def test_region_filter_inferred_from_session_type_labels():
    assert region_filter_for_session_type("Lower") == "LowerOnly"
    assert region_filter_for_session_type("QuadsCalves") == "LowerOnly"
    assert region_filter_for_session_type("FullBody") == "Unrestricted"
    assert region_filter_for_session_type("Upper") == "UpperOnly"
    assert region_filter_for_session_type("Arms") == "UpperOnly"


def test_missing_required_structure_rejected():
    req = tiny_request()
    del req["weeklyTargets"]
    with pytest.raises(WireError):
        decode_request(req)
    req2 = tiny_request()
    del req2["context"]
    with pytest.raises(WireError):
        decode_request(req2)
