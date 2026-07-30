from __future__ import annotations

import base64
import hashlib
import json
import struct
import urllib.request
from collections.abc import Callable
from datetime import datetime
from pathlib import Path
from typing import Literal

from cryptography import x509
from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa, utils
from pydantic import Field, model_validator

from innerflow_v2.reliability.protocol_v13 import (
    NIST_BEACON_V2_CHAIN,
    NIST_BEACON_V2_ENDPOINT,
    BeaconPulse,
    CandidatePoolPublicFreezeManifestV13,
    StrictModel,
    canonical_sha256,
)

NIST_BEACON_V2_PERIOD_MS = 60_000
HEX_CHARS = frozenset("0123456789abcdefABCDEF")
LIST_VALUE_ORDER = ("previous", "hour", "day", "month", "year")


class NistExternalValueV2(StrictModel):
    sourceId: str
    statusCode: int = Field(ge=0, le=2**32 - 1)
    value: str


class NistListValueV2(StrictModel):
    uri: str | None = None
    type: Literal["previous", "hour", "day", "month", "year"]
    value: str


class NistPulseV2(StrictModel):
    uri: str
    version: Literal["2.0"]
    cipherSuite: Literal[0]
    period: Literal[NIST_BEACON_V2_PERIOD_MS]
    certificateId: str
    chainIndex: Literal[2]
    pulseIndex: int = Field(ge=1)
    timeStamp: str
    localRandomValue: str
    external: NistExternalValueV2
    listValues: list[NistListValueV2] = Field(min_length=5, max_length=5)
    precommitmentValue: str
    statusCode: int = Field(ge=0, le=2**32 - 1)
    signatureValue: str
    outputValue: str

    @model_validator(mode="after")
    def _canonical_fields(self) -> "NistPulseV2":
        for name in (
            "certificateId",
            "localRandomValue",
            "precommitmentValue",
            "outputValue",
        ):
            _decode_hex(getattr(self, name), expected_bytes=64, field=name)
        _decode_hex(self.external.sourceId, expected_bytes=64, field="external.sourceId")
        _decode_hex(self.external.value, expected_bytes=64, field="external.value")
        _decode_hex(self.signatureValue, field="signatureValue")
        if tuple(item.type for item in self.listValues) != LIST_VALUE_ORDER:
            raise ValueError("listValues must be previous/hour/day/month/year in order")
        for item in self.listValues:
            _decode_hex(item.value, expected_bytes=64, field=f"listValues.{item.type}")
        timestamp = _parse_timestamp(self.timeStamp)
        if timestamp.microsecond % 1000:
            raise ValueError("NIST pulse timestamp must have millisecond precision")
        expected_uri = (
            f"{NIST_BEACON_V2_ENDPOINT}/chain/{NIST_BEACON_V2_CHAIN}"
            f"/pulse/{self.pulseIndex}"
        )
        if self.uri != expected_uri:
            raise ValueError("pulse URI does not match pinned chain and pulse index")
        return self

    @property
    def timestamp(self) -> datetime:
        return _parse_timestamp(self.timeStamp)


class NistPulseResponseV2(StrictModel):
    pulse: NistPulseV2


class BeaconEvidenceBundle(StrictModel):
    protocol_version: Literal["nist-beacon-2.0"] = "nist-beacon-2.0"
    pulse_request_url: str
    pulse_response_base64: str
    previous_request_url: str
    previous_response_base64: str
    certificates_pem_base64: dict[str, str]

    @model_validator(mode="after")
    def _evidence_is_bound(self) -> "BeaconEvidenceBundle":
        if not self.certificates_pem_base64:
            raise ValueError("beacon evidence must contain certificate material")
        for value in (
            self.pulse_response_base64,
            self.previous_response_base64,
            *self.certificates_pem_base64.values(),
        ):
            try:
                base64.b64decode(value, validate=True)
            except ValueError as exc:
                raise ValueError("beacon evidence contains invalid base64") from exc
        return self

    @property
    def sha256(self) -> str:
        return canonical_sha256(self.model_dump(mode="json"))


class VerifiedBeaconArtifactV13(StrictModel):
    protocol_version: Literal["v1.3"] = "v1.3"
    public_freeze_manifest_sha256: str
    future_seed_not_before: datetime
    evidence_bundle_sha256: str
    previous_pulse_timestamp: datetime
    beacon: BeaconPulse

    @model_validator(mode="after")
    def _verified_identity(self) -> "VerifiedBeaconArtifactV13":
        for value in (
            self.public_freeze_manifest_sha256,
            self.evidence_bundle_sha256,
        ):
            if len(value) != 64 or any(char not in "0123456789abcdef" for char in value):
                raise ValueError("verified Beacon hashes must be lowercase SHA-256")
        if (
            self.future_seed_not_before.tzinfo is None
            or self.previous_pulse_timestamp.tzinfo is None
        ):
            raise ValueError("verified Beacon timestamps must be timezone-aware")
        return self


def _decode_hex(value: str, *, expected_bytes: int | None = None, field: str) -> bytes:
    if not value or len(value) % 2 or any(char not in HEX_CHARS for char in value):
        raise ValueError(f"{field} must be non-empty even-length hexadecimal")
    decoded = bytes.fromhex(value)
    if expected_bytes is not None and len(decoded) != expected_bytes:
        raise ValueError(f"{field} must decode to {expected_bytes} bytes")
    return decoded


def _parse_timestamp(value: str) -> datetime:
    if not value.endswith("Z"):
        raise ValueError("NIST pulse timestamp must use UTC Z suffix")
    parsed = datetime.fromisoformat(value[:-1] + "+00:00")
    if parsed.tzinfo is None:
        raise ValueError("NIST pulse timestamp must be timezone-aware")
    return parsed


def _length_prefixed(value: bytes) -> bytes:
    return struct.pack(">I", len(value)) + value


def _serialize_signed_fields(pulse: NistPulseV2) -> bytes:
    # The official XSD order is uri, version, cipher, period, certificate,
    # chain, pulse, timestamp, then the remaining hash/integer fields.
    uri = _length_prefixed(pulse.uri.encode("utf-8"))
    version = _length_prefixed(pulse.version.encode("utf-8"))
    timestamp = _length_prefixed(pulse.timeStamp.encode("utf-8"))
    return b"".join(
        (
            uri,
            version,
            struct.pack(">I", pulse.cipherSuite),
            struct.pack(">I", pulse.period),
            _length_prefixed(_decode_hex(
                pulse.certificateId, expected_bytes=64, field="certificateId"
            )),
            struct.pack(">Q", pulse.chainIndex),
            struct.pack(">Q", pulse.pulseIndex),
            timestamp,
            _length_prefixed(_decode_hex(
                pulse.localRandomValue,
                expected_bytes=64,
                field="localRandomValue",
            )),
            _length_prefixed(_decode_hex(
                pulse.external.sourceId,
                expected_bytes=64,
                field="external.sourceId",
            )),
            struct.pack(">I", pulse.external.statusCode),
            _length_prefixed(_decode_hex(
                pulse.external.value,
                expected_bytes=64,
                field="external.value",
            )),
            *(
                _length_prefixed(_decode_hex(
                    item.value,
                    expected_bytes=64,
                    field=f"listValues.{item.type}",
                ))
                for item in pulse.listValues
            ),
            _length_prefixed(_decode_hex(
                pulse.precommitmentValue,
                expected_bytes=64,
                field="precommitmentValue",
            )),
            struct.pack(">I", pulse.statusCode),
        )
    )


def _parse_response(raw_response: bytes) -> NistPulseV2:
    try:
        payload = json.loads(raw_response)
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ValueError("NIST pulse response is not valid UTF-8 JSON") from exc
    return NistPulseResponseV2.model_validate(payload).pulse


def _load_certificate(pem_bytes: bytes, certificate_id: str) -> x509.Certificate:
    try:
        certificate = x509.load_pem_x509_certificate(pem_bytes)
    except ValueError as exc:
        raise ValueError("NIST certificate is not valid PEM X.509") from exc
    der = certificate.public_bytes(serialization.Encoding.DER)
    if hashlib.sha512(der).hexdigest() != certificate_id.lower():
        raise ValueError("certificate DER hash does not match pulse certificateId")
    public_key = certificate.public_key()
    if not isinstance(public_key, rsa.RSAPublicKey):
        raise ValueError("NIST cipher suite 0 requires an RSA certificate")
    return certificate


def verify_signed_pulse(raw_response: bytes, certificate_pem: bytes) -> NistPulseV2:
    pulse = _parse_response(raw_response)
    certificate = _load_certificate(certificate_pem, pulse.certificateId)
    if not (
        certificate.not_valid_before_utc
        <= pulse.timestamp
        <= certificate.not_valid_after_utc
    ):
        raise ValueError("NIST certificate is not valid at the pulse timestamp")
    signed_fields = _serialize_signed_fields(pulse)
    digest = hashlib.sha512(signed_fields).digest()
    signature = _decode_hex(pulse.signatureValue, field="signatureValue")
    public_key = certificate.public_key()
    assert isinstance(public_key, rsa.RSAPublicKey)
    try:
        public_key.verify(
            signature,
            digest,
            padding.PKCS1v15(),
            utils.Prehashed(hashes.SHA512()),
        )
    except InvalidSignature as exc:
        raise ValueError("NIST pulse signature verification failed") from exc
    expected_output = hashlib.sha512(signed_fields + signature).hexdigest()
    if expected_output != pulse.outputValue.lower():
        raise ValueError("NIST pulse outputValue does not match signed pulse bytes")
    return pulse


def _bundle_bytes(value: str) -> bytes:
    return base64.b64decode(value, validate=True)


def verify_beacon_evidence(
    bundle: BeaconEvidenceBundle,
    public_freeze: CandidatePoolPublicFreezeManifestV13,
    *,
    certificate_fetch: Callable[[str], bytes],
) -> VerifiedBeaconArtifactV13:
    raw_pulse = _bundle_bytes(bundle.pulse_response_base64)
    raw_previous = _bundle_bytes(bundle.previous_response_base64)
    parsed_pulse = _parse_response(raw_pulse)
    parsed_previous = _parse_response(raw_previous)

    try:
        pulse_certificate = _bundle_bytes(
            bundle.certificates_pem_base64[parsed_pulse.certificateId.lower()]
        )
        previous_certificate = _bundle_bytes(
            bundle.certificates_pem_base64[parsed_previous.certificateId.lower()]
        )
    except KeyError as exc:
        raise ValueError("beacon evidence omits a referenced certificate") from exc
    for certificate_id, embedded in (
        (parsed_pulse.certificateId.lower(), pulse_certificate),
        (parsed_previous.certificateId.lower(), previous_certificate),
    ):
        certificate_url = (
            f"{NIST_BEACON_V2_ENDPOINT}/certificate/{certificate_id}"
        )
        if certificate_fetch(certificate_url) != embedded:
            raise ValueError(
                "embedded certificate does not match authoritative NIST retrieval"
            )

    pulse = verify_signed_pulse(raw_pulse, pulse_certificate)
    previous = verify_signed_pulse(raw_previous, previous_certificate)
    boundary = public_freeze.future_seed_not_before
    boundary_ms = int(boundary.timestamp() * 1000)
    expected_request = f"{NIST_BEACON_V2_ENDPOINT}/pulse/time/{boundary_ms}"
    if bundle.pulse_request_url != expected_request:
        raise ValueError("pulse request is not bound to the public freeze boundary")
    expected_previous_request = (
        f"{NIST_BEACON_V2_ENDPOINT}/pulse/time/previous/"
        f"{int(pulse.timestamp.timestamp() * 1000)}"
    )
    if bundle.previous_request_url != expected_previous_request:
        raise ValueError("previous-pulse request is not bound to selected pulse")
    if pulse.timestamp < boundary:
        raise ValueError("beacon pulse predates the future-seed boundary")
    if previous.timestamp >= boundary:
        raise ValueError("beacon pulse is not the first eligible pulse")
    if previous.timestamp >= pulse.timestamp:
        raise ValueError("previous pulse timestamp must precede selected pulse")
    previous_value = next(
        item.value.lower() for item in pulse.listValues if item.type == "previous"
    )
    if previous_value != previous.outputValue.lower():
        raise ValueError("selected pulse is not linked to verified previous pulse")

    beacon = BeaconPulse(
        pulse_timestamp=pulse.timestamp,
        output_value_hex=pulse.outputValue.lower(),
        signed_pulse_sha256=hashlib.sha256(raw_pulse).hexdigest(),
        signature_verified=True,
    )
    return VerifiedBeaconArtifactV13(
        public_freeze_manifest_sha256=canonical_sha256(
            public_freeze.model_dump(mode="json")
        ),
        future_seed_not_before=boundary,
        evidence_bundle_sha256=bundle.sha256,
        previous_pulse_timestamp=previous.timestamp,
        beacon=beacon,
    )


def fetch_first_eligible_beacon_evidence(
    public_freeze: CandidatePoolPublicFreezeManifestV13,
    *,
    fetch: Callable[[str], bytes],
) -> tuple[BeaconEvidenceBundle, VerifiedBeaconArtifactV13]:
    boundary_ms = int(public_freeze.future_seed_not_before.timestamp() * 1000)
    pulse_url = f"{NIST_BEACON_V2_ENDPOINT}/pulse/time/{boundary_ms}"
    raw_pulse = fetch(pulse_url)
    pulse = _parse_response(raw_pulse)
    previous_url = (
        f"{NIST_BEACON_V2_ENDPOINT}/pulse/time/previous/"
        f"{int(pulse.timestamp.timestamp() * 1000)}"
    )
    raw_previous = fetch(previous_url)
    previous = _parse_response(raw_previous)
    certificate_ids = {
        pulse.certificateId.lower(),
        previous.certificateId.lower(),
    }
    certificates = {
        certificate_id: base64.b64encode(
            fetch(f"{NIST_BEACON_V2_ENDPOINT}/certificate/{certificate_id}")
        ).decode("ascii")
        for certificate_id in sorted(certificate_ids)
    }
    bundle = BeaconEvidenceBundle(
        pulse_request_url=pulse_url,
        pulse_response_base64=base64.b64encode(raw_pulse).decode("ascii"),
        previous_request_url=previous_url,
        previous_response_base64=base64.b64encode(raw_previous).decode("ascii"),
        certificates_pem_base64=certificates,
    )
    certificate_bytes = {
        certificate_id: base64.b64decode(value, validate=True)
        for certificate_id, value in bundle.certificates_pem_base64.items()
    }
    return bundle, verify_beacon_evidence(
        bundle,
        public_freeze,
        certificate_fetch=lambda url: certificate_bytes[url.rsplit("/", 1)[-1]],
    )


def fetch_url(url: str) -> bytes:
    request = urllib.request.Request(
        url,
        headers={"Accept": "application/json", "User-Agent": "innerflow-m0-v1.3"},
        method="GET",
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.read()


def write_create_only(path: str | Path, payload: StrictModel) -> None:
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    with target.open("x", encoding="utf-8") as handle:
        handle.write(
            json.dumps(
                payload.model_dump(mode="json"),
                ensure_ascii=False,
                indent=2,
            )
            + "\n"
        )
