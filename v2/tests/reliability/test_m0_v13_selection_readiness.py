from __future__ import annotations

import base64
import hashlib
import importlib.util
import json
import shutil
import subprocess
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

import pytest
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa, utils
from cryptography.x509.oid import NameOID

from innerflow_v2.reliability.beacon_v2 import (
    BeaconEvidenceBundle,
    NistPulseV2,
    VerifiedBeaconArtifactV13,
    _serialize_signed_fields,
    fetch_first_eligible_beacon_evidence,
    verify_beacon_evidence,
    write_create_only,
)
from innerflow_v2.reliability.protocol_v13 import (
    CandidateV13,
    SelectionManifestV13,
    load_candidate_pool_public_freeze_manifest,
    public_selection_manifest,
    select_candidate_pool,
    selection_manifest_sha256,
    validate_candidate_pool,
)
from innerflow_v2.reliability.selection_readiness import (
    PUBLIC_FREEZE_MANIFEST,
    load_official_selection_inputs,
    load_verified_beacon_for_selection,
)

REPO_ROOT = Path(__file__).resolve().parents[3]
V2_ROOT = REPO_ROOT / "v2"
SCRIPT = V2_ROOT / "scripts" / "check_m0_v13_conformance.py"
OFFICIAL_EVIDENCE = V2_ROOT / "eval/m0/beacon/M0_V13_BEACON_EVIDENCE.json"
OFFICIAL_VERIFIED = V2_ROOT / "eval/m0/beacon/M0_V13_VERIFIED_BEACON.json"
OFFICIAL_PUBLIC_SELECTION = (
    V2_ROOT / "eval/m0/manifests/M0_V13_SELECTION_PUBLIC.json"
)
ZERO_512 = "00" * 64


def _certificate() -> tuple[rsa.RSAPrivateKey, bytes, str]:
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "offline.test")])
    now = datetime(2026, 1, 1, tzinfo=timezone.utc)
    certificate = (
        x509.CertificateBuilder()
        .subject_name(name)
        .issuer_name(name)
        .public_key(key.public_key())
        .serial_number(1)
        .not_valid_before(now - timedelta(days=1))
        .not_valid_after(now + timedelta(days=3650))
        .sign(key, hashes.SHA256())
    )
    pem = certificate.public_bytes(serialization.Encoding.PEM)
    certificate_id = hashlib.sha512(
        certificate.public_bytes(serialization.Encoding.DER)
    ).hexdigest()
    return key, pem, certificate_id


def _timestamp(value: datetime) -> str:
    return value.astimezone(timezone.utc).isoformat(timespec="milliseconds").replace(
        "+00:00", "Z"
    )


def _signed_response(
    key: rsa.RSAPrivateKey,
    certificate_id: str,
    *,
    pulse_index: int,
    timestamp: datetime,
    previous_output: str,
) -> tuple[bytes, str]:
    payload = {
        "uri": (
            "https://beacon.nist.gov/beacon/2.0/chain/2/pulse/"
            f"{pulse_index}"
        ),
        "version": "2.0",
        "cipherSuite": 0,
        "period": 60_000,
        "certificateId": certificate_id,
        "chainIndex": 2,
        "pulseIndex": pulse_index,
        "timeStamp": _timestamp(timestamp),
        "localRandomValue": hashlib.sha512(
            f"local:{pulse_index}".encode()
        ).hexdigest(),
        "external": {
            "sourceId": ZERO_512,
            "statusCode": 0,
            "value": ZERO_512,
        },
        "listValues": [
            {"type": "previous", "value": previous_output},
            {"type": "hour", "value": ZERO_512},
            {"type": "day", "value": ZERO_512},
            {"type": "month", "value": ZERO_512},
            {"type": "year", "value": ZERO_512},
        ],
        "precommitmentValue": hashlib.sha512(
            f"next:{pulse_index}".encode()
        ).hexdigest(),
        "statusCode": 0,
        "signatureValue": "00",
        "outputValue": ZERO_512,
    }
    unsigned = NistPulseV2.model_validate(payload)
    signed_fields = _serialize_signed_fields(unsigned)
    digest = hashlib.sha512(signed_fields).digest()
    signature = key.sign(
        digest,
        padding.PKCS1v15(),
        utils.Prehashed(hashes.SHA512()),
    )
    payload["signatureValue"] = signature.hex()
    payload["outputValue"] = hashlib.sha512(signed_fields + signature).hexdigest()
    raw = json.dumps(
        {"pulse": payload},
        sort_keys=False,
        separators=(",", ":"),
    ).encode()
    return raw, payload["outputValue"]


def _certificate_fetch(bundle: BeaconEvidenceBundle):
    certificates = {
        certificate_id: base64.b64decode(value)
        for certificate_id, value in bundle.certificates_pem_base64.items()
    }

    def fetch(url: str) -> bytes:
        return certificates[url.rsplit("/", 1)[-1]]

    return fetch


@pytest.fixture
def public_freeze():
    return load_candidate_pool_public_freeze_manifest(
        REPO_ROOT / PUBLIC_FREEZE_MANIFEST
    )


@pytest.fixture
def valid_evidence(public_freeze):
    key, pem, certificate_id = _certificate()
    boundary = public_freeze.future_seed_not_before
    previous_time = boundary.replace(second=0, microsecond=0)
    pulse_time = previous_time + timedelta(minutes=1)
    previous_raw, previous_output = _signed_response(
        key,
        certificate_id,
        pulse_index=100,
        timestamp=previous_time,
        previous_output=ZERO_512,
    )
    pulse_raw, _ = _signed_response(
        key,
        certificate_id,
        pulse_index=101,
        timestamp=pulse_time,
        previous_output=previous_output,
    )
    pulse_url = (
        "https://beacon.nist.gov/beacon/2.0/pulse/time/"
        f"{int(boundary.timestamp() * 1000)}"
    )
    previous_url = (
        "https://beacon.nist.gov/beacon/2.0/pulse/time/previous/"
        f"{int(pulse_time.timestamp() * 1000)}"
    )
    bundle = BeaconEvidenceBundle(
        pulse_request_url=pulse_url,
        pulse_response_base64=base64.b64encode(pulse_raw).decode(),
        previous_request_url=previous_url,
        previous_response_base64=base64.b64encode(previous_raw).decode(),
        certificates_pem_base64={
            certificate_id: base64.b64encode(pem).decode()
        },
    )
    return bundle, pem


def test_official_exclusions_rebuild_from_frozen_v12_sources() -> None:
    official = load_official_selection_inputs(REPO_ROOT)
    assert len(official.forbidden_hashes) == 24
    assert len(official.forbidden_fingerprints) == 140
    assert official.exclusion_manifest.source_protocol_version == "v1.2"
    assert official.exclusion_manifest.overlap_fingerprint_algorithm == (
        "sha256-nfkc-casefold-whitespace-v1"
    )


def test_normalized_old_text_reuse_is_rejected() -> None:
    official = load_official_selection_inputs(REPO_ROOT)
    scenarios = json.loads(
        (V2_ROOT / "eval/m0/fixtures/memory_reliability_m0.json").read_text()
    )
    old_text = scenarios[0]["setup_sessions"][0]["events"][0]["content"]
    candidates = list(official.registry.candidates)
    payload = candidates[0].model_dump(mode="json")
    for world in payload["worlds"]:
        world["setup_memory_events"][2]["content"] = f"  {old_text.upper()}  "
    candidates[0] = CandidateV13.model_validate(payload)
    with pytest.raises(ValueError, match="normalized text overlaps"):
        validate_candidate_pool(
            candidates,
            forbidden_candidate_hashes=official.forbidden_hashes,
            forbidden_overlap_fingerprints=official.forbidden_fingerprints,
        )


def test_beacon_fetch_and_verification_are_fully_offline(
    public_freeze,
    valid_evidence,
) -> None:
    expected_bundle, pem = valid_evidence
    pulse_raw = base64.b64decode(expected_bundle.pulse_response_base64)
    previous_raw = base64.b64decode(expected_bundle.previous_response_base64)
    certificate_id = next(iter(expected_bundle.certificates_pem_base64))
    responses = {
        expected_bundle.pulse_request_url: pulse_raw,
        expected_bundle.previous_request_url: previous_raw,
        (
            "https://beacon.nist.gov/beacon/2.0/certificate/"
            f"{certificate_id}"
        ): pem,
    }
    requested: list[str] = []

    def offline_fetch(url: str) -> bytes:
        requested.append(url)
        return responses[url]

    bundle, verified = fetch_first_eligible_beacon_evidence(
        public_freeze,
        fetch=offline_fetch,
    )
    assert bundle == expected_bundle
    assert requested == list(responses)
    assert verified.beacon.signature_verified is True
    assert verified.beacon.signed_pulse_sha256 == hashlib.sha256(pulse_raw).hexdigest()


def test_beacon_rejects_pre_boundary_pulse(public_freeze, valid_evidence) -> None:
    bundle, _ = valid_evidence
    payload = bundle.model_dump(mode="json")
    payload["pulse_response_base64"] = payload["previous_response_base64"]
    payload["previous_request_url"] = (
        "https://beacon.nist.gov/beacon/2.0/pulse/time/previous/"
        f"{int((public_freeze.future_seed_not_before.replace(second=0)).timestamp() * 1000)}"
    )
    with pytest.raises(ValueError, match="predates"):
        altered = BeaconEvidenceBundle.model_validate(payload)
        verify_beacon_evidence(
            altered,
            public_freeze,
            certificate_fetch=_certificate_fetch(altered),
        )


def test_beacon_rejects_later_nonfirst_eligible_pulse(public_freeze) -> None:
    key, pem, certificate_id = _certificate()
    first_time = public_freeze.future_seed_not_before + timedelta(seconds=19)
    later_time = first_time + timedelta(minutes=1)
    first_raw, first_output = _signed_response(
        key,
        certificate_id,
        pulse_index=200,
        timestamp=first_time,
        previous_output=ZERO_512,
    )
    later_raw, _ = _signed_response(
        key,
        certificate_id,
        pulse_index=201,
        timestamp=later_time,
        previous_output=first_output,
    )
    bundle = BeaconEvidenceBundle(
        pulse_request_url=(
            "https://beacon.nist.gov/beacon/2.0/pulse/time/"
            f"{int(public_freeze.future_seed_not_before.timestamp() * 1000)}"
        ),
        pulse_response_base64=base64.b64encode(later_raw).decode(),
        previous_request_url=(
            "https://beacon.nist.gov/beacon/2.0/pulse/time/previous/"
            f"{int(later_time.timestamp() * 1000)}"
        ),
        previous_response_base64=base64.b64encode(first_raw).decode(),
        certificates_pem_base64={
            certificate_id: base64.b64encode(pem).decode()
        },
    )
    with pytest.raises(ValueError, match="not the first eligible"):
        verify_beacon_evidence(
            bundle,
            public_freeze,
            certificate_fetch=_certificate_fetch(bundle),
        )


@pytest.mark.parametrize(
    ("field", "expected"),
    [
        ("signatureValue", "signature verification failed"),
        ("outputValue", "outputValue does not match"),
    ],
)
def test_beacon_rejects_signature_or_output_tampering(
    public_freeze,
    valid_evidence,
    field,
    expected,
) -> None:
    bundle, _ = valid_evidence
    raw = json.loads(base64.b64decode(bundle.pulse_response_base64))
    raw["pulse"][field] = (
        "01" * (len(raw["pulse"][field]) // 2)
        if field == "signatureValue"
        else "01" * 64
    )
    payload = bundle.model_dump(mode="json")
    payload["pulse_response_base64"] = base64.b64encode(
        json.dumps(raw, separators=(",", ":")).encode()
    ).decode()
    with pytest.raises(ValueError, match=expected):
        altered = BeaconEvidenceBundle.model_validate(payload)
        verify_beacon_evidence(
            altered,
            public_freeze,
            certificate_fetch=_certificate_fetch(altered),
        )


def test_beacon_rejects_arbitrary_boundary_request(
    public_freeze,
    valid_evidence,
) -> None:
    bundle, _ = valid_evidence
    payload = bundle.model_dump(mode="json")
    payload["pulse_request_url"] += "1"
    with pytest.raises(ValueError, match="public freeze boundary"):
        altered = BeaconEvidenceBundle.model_validate(payload)
        verify_beacon_evidence(
            altered,
            public_freeze,
            certificate_fetch=_certificate_fetch(altered),
        )


def test_beacon_rejects_caller_supplied_self_signed_certificate(
    public_freeze,
    valid_evidence,
) -> None:
    bundle, _ = valid_evidence
    _, authoritative_pem, _ = _certificate()
    with pytest.raises(ValueError, match="authoritative NIST retrieval"):
        verify_beacon_evidence(
            bundle,
            public_freeze,
            certificate_fetch=lambda _: authoritative_pem,
        )


def test_verified_artifact_rejects_altered_raw_evidence(
    public_freeze,
    valid_evidence,
    tmp_path,
) -> None:
    bundle, _ = valid_evidence
    verified = verify_beacon_evidence(
        bundle,
        public_freeze,
        certificate_fetch=_certificate_fetch(bundle),
    )
    evidence_path = tmp_path / "evidence.json"
    artifact_path = tmp_path / "verified.json"
    evidence_path.write_text(bundle.model_dump_json())
    artifact_path.write_text(verified.model_dump_json())
    altered = bundle.model_dump(mode="json")
    altered["pulse_request_url"] += "1"
    evidence_path.write_text(json.dumps(altered))
    with pytest.raises(ValueError, match="public freeze boundary"):
        load_verified_beacon_for_selection(
            evidence_path,
            artifact_path,
            public_freeze,
            certificate_fetch=_certificate_fetch(bundle),
        )


def test_official_selection_inputs_reject_frozen_registry_drift(tmp_path) -> None:
    shutil.copytree(V2_ROOT / "eval", tmp_path / "v2/eval")
    registry = (
        tmp_path
        / "v2/eval/m0/registries/M0_V13_CANDIDATE_REGISTRY_DRAFT.json"
    )
    registry.write_text(registry.read_text() + "\n")
    with pytest.raises(ValueError, match="bound file hash drift"):
        load_official_selection_inputs(tmp_path)


def test_selection_cli_has_no_freeform_timestamp_or_seed_authority() -> None:
    result = subprocess.run(
        [
            sys.executable,
            str(SCRIPT),
            "select",
            "--pool-frozen-at",
            "2099-01-01T00:00:00Z",
            "--beacon-evidence",
            "unused",
            "--beacon-artifact",
            "unused",
            "--output",
            "unused",
            "--public-output",
            "unused",
        ],
        capture_output=True,
        text=True,
    )
    assert result.returncode == 2
    assert "unrecognized arguments: --pool-frozen-at" in result.stderr


def test_verified_evidence_can_be_reloaded_for_official_selection(
    public_freeze,
    valid_evidence,
    tmp_path,
) -> None:
    bundle, _ = valid_evidence
    verified = verify_beacon_evidence(
        bundle,
        public_freeze,
        certificate_fetch=_certificate_fetch(bundle),
    )
    evidence_path = tmp_path / "beacon-evidence.json"
    artifact_path = tmp_path / "beacon-verified.json"
    evidence_path.write_text(bundle.model_dump_json())
    artifact_path.write_text(verified.model_dump_json())
    reloaded = load_verified_beacon_for_selection(
        evidence_path,
        artifact_path,
        public_freeze,
        certificate_fetch=_certificate_fetch(bundle),
    )
    assert reloaded == verified


def test_official_selection_cli_uses_verified_evidence_without_network(
    public_freeze,
    valid_evidence,
    tmp_path,
    monkeypatch,
    capsys,
) -> None:
    bundle, _ = valid_evidence
    verified = verify_beacon_evidence(
        bundle,
        public_freeze,
        certificate_fetch=_certificate_fetch(bundle),
    )
    evidence_path = tmp_path / "beacon-evidence.json"
    artifact_path = tmp_path / "beacon-verified.json"
    selection_path = tmp_path / "selection.json"
    public_path = tmp_path / "public.json"
    evidence_path.write_text(bundle.model_dump_json())
    artifact_path.write_text(verified.model_dump_json())

    spec = importlib.util.spec_from_file_location("m0_v13_cli", SCRIPT)
    assert spec is not None and spec.loader is not None
    cli = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(cli)
    monkeypatch.setattr(cli, "fetch_url", _certificate_fetch(bundle))
    monkeypatch.setattr(
        sys,
        "argv",
        [
            str(SCRIPT),
            "select",
            "--beacon-evidence",
            str(evidence_path),
            "--beacon-artifact",
            str(artifact_path),
            "--output",
            str(selection_path),
            "--public-output",
            str(public_path),
        ],
    )
    cli.main()

    assert json.loads(capsys.readouterr().out)["selected"] == 24
    assert len(json.loads(selection_path.read_text())["selected"]) == 24
    assert json.loads(public_path.read_text())["holdout"]["count"] == 8


def test_beacon_artifacts_are_create_only(
    public_freeze,
    valid_evidence,
    tmp_path,
) -> None:
    bundle, _ = valid_evidence
    verified = verify_beacon_evidence(
        bundle,
        public_freeze,
        certificate_fetch=_certificate_fetch(bundle),
    )
    target = tmp_path / "verified.json"
    write_create_only(target, verified)
    with pytest.raises(FileExistsError):
        write_create_only(target, verified)


def test_committed_official_beacon_evidence_matches_verified_artifact(
    public_freeze,
) -> None:
    bundle = BeaconEvidenceBundle.model_validate_json(
        OFFICIAL_EVIDENCE.read_text(encoding="utf-8")
    )
    claimed = VerifiedBeaconArtifactV13.model_validate_json(
        OFFICIAL_VERIFIED.read_text(encoding="utf-8")
    )
    verified = verify_beacon_evidence(
        bundle,
        public_freeze,
        certificate_fetch=_certificate_fetch(bundle),
    )
    assert claimed == verified
    assert verified.previous_pulse_timestamp < verified.future_seed_not_before
    assert verified.beacon.pulse_timestamp >= verified.future_seed_not_before


def test_committed_official_selection_is_reproducible_offline() -> None:
    official = load_official_selection_inputs(REPO_ROOT)
    bundle = BeaconEvidenceBundle.model_validate_json(
        OFFICIAL_EVIDENCE.read_text(encoding="utf-8")
    )
    verified = load_verified_beacon_for_selection(
        OFFICIAL_EVIDENCE,
        OFFICIAL_VERIFIED,
        official.public_freeze,
        certificate_fetch=_certificate_fetch(bundle),
    )
    reproduced = select_candidate_pool(
        official.registry,
        authoring_inventory=official.inventory,
        exclusion_manifest=official.exclusion_manifest,
        forbidden_candidate_hashes=official.forbidden_hashes,
        forbidden_overlap_fingerprints=official.forbidden_fingerprints,
        pool_frozen_at=official.public_freeze.public_freeze_effective_at,
        beacon=verified.beacon,
    )
    committed_public = json.loads(
        OFFICIAL_PUBLIC_SELECTION.read_text(encoding="utf-8")
    )

    assert isinstance(reproduced, SelectionManifestV13)
    assert selection_manifest_sha256(reproduced) == (
        "01655a903f3f63c15abb24b29d8d151a8c079427ce8e84b4203be8bd63f7e253"
    )
    assert committed_public == public_selection_manifest(reproduced)
